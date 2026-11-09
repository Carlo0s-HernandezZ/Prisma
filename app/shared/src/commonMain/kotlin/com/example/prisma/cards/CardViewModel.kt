package com.example.prisma.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prisma.api.CardDto
import com.example.prisma.api.CardRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CardUiState(
    val bankInput: String = "",
    val aliasInput: String = "",
    val lastFourInput: String = "",
    val cards: List<CardDto> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val editingCard: CardDto? = null,
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
                onSuccess = { list ->
                    _state.update { it.copy(isLoading = false, cards = list) }
                },
                onFailure = { e -> fail(e, "No se pudieron cargar tus tarjetas.", isDelete = false) }
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
        val digitsOnly = raw.filter { it.isDigit() }.take(CardRules.LAST_FOUR_LENGTH)
        _state.update { it.copy(lastFourInput = digitsOnly, message = null) }
    }

    fun startEditing(card: CardDto) {
        _state.update {
            it.copy(
                editingCard = card,
                bankInput = card.banco,
                aliasInput = card.alias,
                lastFourInput = card.ultimosCuatro.orEmpty(),
                message = null
            )
        }
    }

    fun cancelEditing() {
        _state.update {
            it.copy(editingCard = null, bankInput = "", aliasInput = "", lastFourInput = "", message = null)
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
            return _state.update { it.copy(message = "Escribe un alias para la tarjeta.", isError = true) }
        }
        val lastFour = s.lastFourInput.trim().ifEmpty { null }
        if (lastFour != null && lastFour.length != CardRules.LAST_FOUR_LENGTH) {
            return _state.update { it.copy(message = "Ingresa exactamente 4 dígitos o déjalo vacío.", isError = true) }
        }

        _state.update { it.copy(isSaving = true, message = null) }
        viewModelScope.launch {
            val editing = s.editingCard
            val result = if (editing != null) {
                repository.update(editing.id, bank, alias, lastFour)
            } else {
                repository.add(bank, alias, lastFour)
            }

            result.fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            isSaving = false,
                            editingCard = null,
                            bankInput = "",
                            aliasInput = "",
                            lastFourInput = "",
                            message = if (editing != null) "Tarjeta actualizada." else "Tarjeta agregada.",
                            isError = false
                        )
                    }
                    refresh()
                },
                onFailure = { e -> fail(e, "No se pudo guardar la tarjeta.", isDelete = false) }
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
                    _state.update { it.copy(isSaving = false, message = "Tarjeta eliminada.", isError = false) }
                    refresh()
                },
                onFailure = { e -> fail(e, "No se pudo eliminar la tarjeta.", isDelete = true) }
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
                    CardError.NotFound -> if (isDelete) "Esa tarjeta ya no existe." else "No se encontró el recurso."
                    CardError.Unauthorized -> "Tu sesión expiró."
                    CardError.Unknown -> fallback
                }
            )
        }
    }
}