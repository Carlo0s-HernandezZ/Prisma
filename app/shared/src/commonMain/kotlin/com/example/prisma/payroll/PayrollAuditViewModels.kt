package com.example.prisma.payroll

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prisma.analysis.AnalysisException
import com.example.prisma.api.AlertSeverity
import com.example.prisma.api.CardDto
import com.example.prisma.api.PayrollAuditDto
import com.example.prisma.api.ReminderDto
import com.example.prisma.cards.CardRepository
import com.example.prisma.notifications.LocalNotifications
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- Auditoría de nómina

data class PayrollAuditUiState(
    val audits: List<PayrollAuditDto> = emptyList(),
    val selectedId: String? = null,
    val isLoading: Boolean = true,
    val uploading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val sessionExpired: Boolean = false,
) {
    val selected: PayrollAuditDto? get() = audits.firstOrNull { it.id == selectedId } ?: audits.firstOrNull()
}

class PayrollAuditViewModel(private val repository: PayrollAuditRepository) : ViewModel() {
    private val _state = MutableStateFlow(PayrollAuditUiState())
    val state: StateFlow<PayrollAuditUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.list().fold(
                onSuccess = { r -> _state.update { it.copy(audits = r.auditorias, isLoading = false) } },
                onFailure = ::fail,
            )
        }
    }

    fun select(id: String) = _state.update { it.copy(selectedId = id) }

    fun fileError(message: String) = _state.update { it.copy(error = message, message = null) }

    fun upload(name: String, bytes: ByteArray) {
        if (_state.value.uploading) return
        viewModelScope.launch {
            _state.update { it.copy(uploading = true, error = null, message = null) }
            repository.upload(name, bytes).fold(
                onSuccess = { audit ->
                    _state.update { s ->
                        s.copy(
                            uploading = false,
                            audits = (listOf(audit) + s.audits.filterNot { it.id == audit.id }),
                            selectedId = audit.id,
                            message = "Listo: revisamos tu nómina.",
                        )
                    }
                    notifyIrregularities(audit)
                },
                onFailure = { e ->
                    _state.update { it.copy(uploading = false) }
                    fail(e)
                },
            )
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repository.delete(id).fold(
                onSuccess = { _state.update { s -> s.copy(audits = s.audits.filterNot { it.id == id }, selectedId = null, message = "Auditoría eliminada.") } },
                onFailure = ::fail,
            )
        }
    }

    /** Notificación del teléfono cuando la nómina tiene puntos importantes por revisar. */
    private fun notifyIrregularities(audit: PayrollAuditDto) {
        val important = audit.alertas.filter { it.severidad != AlertSeverity.BAJA }
        if (important.isEmpty()) return
        LocalNotifications.notifyNow(
            id = 60_000 + (audit.id.hashCode() and 0xFFF),
            title = if (important.size == 1) "Revisa tu nómina" else "Revisa tu nómina: ${important.size} puntos",
            body = "${important.first().titulo}. ${important.first().detalle}",
        )
    }

    private fun fail(e: Throwable) = _state.update {
        it.copy(
            isLoading = false,
            error = e.message ?: "Ocurrió un error. Inténtalo de nuevo.",
            message = null,
            sessionExpired = it.sessionExpired || (e as? AnalysisException)?.sessionExpired == true,
        )
    }
}

// ---------------------------------------------------------------- Recordatorios

data class RemindersUiState(
    val reminders: List<ReminderDto> = emptyList(),
    val cards: List<CardDto> = emptyList(),
    val isLoading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val sessionExpired: Boolean = false,
)

/**
 * Recordatorios del usuario. Se crea al iniciar sesión: así el teléfono reprograma sus avisos
 * cada vez que se abre la app, aunque el usuario no entre a la pantalla de recordatorios.
 */
class RemindersViewModel(
    private val repository: PayrollAuditRepository,
    private val cards: CardRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RemindersUiState())
    val state: StateFlow<RemindersUiState> = _state.asStateFlow()

    init {
        LocalNotifications.requestPermission()
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val cardList = cards.list().getOrElse { _state.value.cards }
            repository.reminders().fold(
                onSuccess = { list ->
                    _state.update { it.copy(reminders = list, cards = cardList, isLoading = false, error = null) }
                    sync()
                },
                onFailure = { e -> fail(e, cardList) },
            )
        }
    }

    fun save(r: ReminderDto, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            repository.saveReminder(r).fold(
                onSuccess = { saved ->
                    _state.update { s ->
                        s.copy(saving = false, reminders = s.reminders.filterNot { it.id == saved.id } + saved)
                    }
                    sync()
                    onDone()
                },
                onFailure = { e ->
                    _state.update { it.copy(saving = false) }
                    fail(e, _state.value.cards)
                },
            )
        }
    }

    fun toggle(r: ReminderDto) = save(r.copy(activo = !r.activo))

    fun delete(id: String) {
        viewModelScope.launch {
            repository.deleteReminder(id).fold(
                onSuccess = {
                    _state.update { s -> s.copy(reminders = s.reminders.filterNot { it.id == id }) }
                    sync()
                },
                onFailure = { e -> fail(e, _state.value.cards) },
            )
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun sync() {
        val s = _state.value
        val names = s.cards.associate { it.id to (it.banco + (it.ultimosCuatro?.let { l -> " •••• $l" } ?: "")) }
        LocalNotifications.syncReminders(s.reminders, names)
    }

    private fun fail(e: Throwable, cardList: List<CardDto>) = _state.update {
        it.copy(
            cards = cardList,
            isLoading = false,
            error = e.message ?: "Ocurrió un error. Inténtalo de nuevo.",
            sessionExpired = it.sessionExpired || (e as? AnalysisException)?.sessionExpired == true,
        )
    }
}
