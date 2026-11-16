package com.example.prisma.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prisma.api.CardDto
import com.example.prisma.api.CardRules
import com.example.prisma.notifications.CardNotificationNotifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CardUiState(
    val bankInput: String = "",
    val aliasInput: String = "",
    val lastFourInput: String = "",
    val diaCorteInput: Int? = null,
    val notificarCorte: Boolean = false,
    val horaNotificacionCorteInput: String = "09:00",
    val diaPagoInput: Int? = null,
    val notificarPago: Boolean = false,
    val horaNotificacionPagoInput: String = "20:00",
    val cards: List<CardDto> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val editingCard: CardDto? = null,
    val message: String? = null,
    val isError: Boolean = false,
    val pendingDelete: CardDto? = null,
    val sessionExpired: Boolean = false,
)

class CardViewModel(private val repository: CardRepository) : ViewModel() {

    private val _state = MutableStateFlow(CardUiState())
    val state: StateFlow<CardUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            repository.list().fold(
                onSuccess = { cards ->
                    cards.forEach { card ->
                        CardNotificationNotifier.scheduleCardReminders(card)
                    }
                    _state.update { it.copy(isLoading = false, cards = cards) }
                },
                onFailure = { e -> fail(e, "No se pudieron cargar tus tarjetas.", isDelete = false) },
            )
        }
    }

    fun onBankChange(raw: String) {
        _state.update { it.copy(bankInput = raw.take(CardRules.MAX_BANK_LENGTH), message = null) }
    }

    fun onAliasChange(raw: String) {
        _state.update { it.copy(aliasInput = raw.take(CardRules.MAX_ALIAS_LENGTH), message = null) }
    }

    fun onLastFourChange(raw: String) {
        val digits = raw.filter(Char::isDigit).take(CardRules.LAST_FOUR_LENGTH)
        _state.update { it.copy(lastFourInput = digits, message = null) }
    }

    fun onDiaCorteChange(day: Int?) {
        _state.update { it.copy(diaCorteInput = day, message = null) }
    }

    fun onNotificarCorteChange(enabled: Boolean) {
        _state.update { it.copy(notificarCorte = enabled, message = null) }
    }

    fun onHoraNotificacionCorteChange(time: String) {
        _state.update { it.copy(horaNotificacionCorteInput = time, message = null) }
    }

    fun onDiaPagoChange(day: Int?) {
        _state.update { it.copy(diaPagoInput = day, message = null) }
    }

    fun onNotificarPagoChange(enabled: Boolean) {
        _state.update { it.copy(notificarPago = enabled, message = null) }
    }

    fun onHoraNotificacionPagoChange(time: String) {
        _state.update { it.copy(horaNotificacionPagoInput = time, message = null) }
    }

    fun startEditing(card: CardDto) {
        _state.update {
            it.copy(
                editingCard = card,
                bankInput = card.banco,
                aliasInput = card.alias,
                lastFourInput = card.ultimosCuatro.orEmpty(),
                diaCorteInput = card.diaCorte,
                notificarCorte = card.horaNotificacionCorte != null,
                horaNotificacionCorteInput = card.horaNotificacionCorte ?: "09:00",
                diaPagoInput = card.diaPago,
                notificarPago = card.horaNotificacionPago != null,
                horaNotificacionPagoInput = card.horaNotificacionPago ?: "20:00",
                message = null,
            )
        }
    }

    fun cancelEditing() {
        _state.update {
            it.copy(
                editingCard = null,
                bankInput = "",
                aliasInput = "",
                lastFourInput = "",
                diaCorteInput = null,
                notificarCorte = false,
                horaNotificacionCorteInput = "09:00",
                diaPagoInput = null,
                notificarPago = false,
                horaNotificacionPagoInput = "20:00",
                message = null,
            )
        }
    }

    fun save() {
        val s = _state.value
        if (s.isSaving) return

        val bank = s.bankInput.trim()
        if (bank.isEmpty()) {
            return _state.update { it.copy(message = "Escribe el nombre del banco.", isError = true) }
        }

        val alias = s.aliasInput.trim()
        if (alias.isEmpty()) {
            return _state.update { it.copy(message = "Escribe un alias para tu tarjeta.", isError = true) }
        }

        val lastFour = s.lastFourInput.trim().takeIf { it.isNotEmpty() }
        if (lastFour != null && (lastFour.length != 4 || !lastFour.all(Char::isDigit))) {
            return _state.update { it.copy(message = "Los últimos dígitos deben ser exactamente 4 números.", isError = true) }
        }

        val diaCorte = s.diaCorteInput
        val horaCorte = if (s.notificarCorte && diaCorte != null) s.horaNotificacionCorteInput.trim().takeIf { it.isNotEmpty() } else null
        val diaPago = s.diaPagoInput
        val horaPago = if (s.notificarPago && diaPago != null) s.horaNotificacionPagoInput.trim().takeIf { it.isNotEmpty() } else null

        _state.update { it.copy(isSaving = true, message = null) }
        viewModelScope.launch {
            val editing = s.editingCard
            val result = if (editing != null) {
                repository.update(editing.id, bank, alias, lastFour, diaCorte, horaCorte, diaPago, horaPago)
            } else {
                repository.add(bank, alias, lastFour, diaCorte, horaCorte, diaPago, horaPago)
            }

            result.fold(
                onSuccess = { savedCard ->
                    CardNotificationNotifier.scheduleCardReminders(savedCard)
                    val msg = if (editing != null) "Tarjeta \"$alias\" actualizada." else "Tarjeta \"$alias\" agregada."
                    _state.update {
                        it.copy(
                            isSaving = false,
                            editingCard = null,
                            bankInput = "",
                            aliasInput = "",
                            lastFourInput = "",
                            diaCorteInput = null,
                            notificarCorte = false,
                            horaNotificacionCorteInput = "09:00",
                            diaPagoInput = null,
                            notificarPago = false,
                            horaNotificacionPagoInput = "20:00",
                            message = msg,
                            isError = false,
                        )
                    }
                    refresh()
                },
                onFailure = { e ->
                    val msg = if (editing != null) "No se pudo actualizar la tarjeta." else "No se pudo agregar la tarjeta."
                    fail(e, msg, isDelete = false)
                },
            )
        }
    }

    fun requestDelete(card: CardDto) = _state.update { it.copy(pendingDelete = card) }

    fun cancelDelete() = _state.update { it.copy(pendingDelete = null) }

    fun confirmDelete() {
        val target = _state.value.pendingDelete ?: return
        _state.update { it.copy(pendingDelete = null, isSaving = true, message = null) }
        viewModelScope.launch {
            repository.delete(target.id).fold(
                onSuccess = {
                    CardNotificationNotifier.cancelCardReminders(target.id)
                    _state.update { it.copy(isSaving = false, message = "Tarjeta eliminada.", isError = false) }
                    refresh()
                },
                onFailure = { e -> fail(e, "No se pudo eliminar la tarjeta.", isDelete = true) },
            )
        }
    }

    private fun fail(e: Throwable, fallback: String, isDelete: Boolean) {
        val error = (e as? CardException)?.error ?: CardError.Unknown
        _state.update {
            it.copy(
                isLoading = false,
                isSaving = false,
                isError = true,
                sessionExpired = error == CardError.Unauthorized,
                message = when (error) {
                    CardError.Network -> "Sin conexión con el servidor. Inténtalo de nuevo."
                    CardError.Invalid -> "Revisa los datos de la tarjeta."
                    CardError.NotFound ->
                        if (isDelete) "Esa tarjeta ya no existe."
                        else "El servidor no tiene disponible la sección de tarjetas. Reinicia o actualiza el servidor."
                    CardError.Unauthorized -> "Tu sesión expiró."
                    CardError.Unknown -> fallback
                },
            )
        }
    }
}
