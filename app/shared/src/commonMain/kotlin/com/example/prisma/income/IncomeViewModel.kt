package com.example.prisma.income

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prisma.api.IncomeDto
import com.example.prisma.api.IncomeRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Ventana emergente de edición. Paso 1: elegir el ingreso ([selected] = null).
 * Paso 2: editar nombre y monto del ingreso elegido.
 */
data class IncomeEditorState(
    val selected: IncomeDto? = null,
    val nameInput: String = "",
    val amountInput: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
)

data class IncomeUiState(
    /** Nombre o concepto del ingreso, p. ej. "Salario". */
    val nameInput: String = "",
    /** Lo que el usuario escribe en el campo de monto (solo dígitos y un punto). */
    val amountInput: String = "",
    val ingresos: List<IncomeDto> = emptyList(),
    val totalMes: String = "0.00",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    /** Ingreso pendiente de confirmar para eliminar. */
    val pendingDelete: IncomeDto? = null,
    /** Ventana de edición abierta (null = cerrada). */
    val editor: IncomeEditorState? = null,
    /** Filtro de la lista "Ingresos registrados" (por mes, año o monto). */
    val filter: IncomeFilter = IncomeFilter.All,
    /** El servidor rechazó el token: la app debe volver al login. */
    val sessionExpired: Boolean = false,
)

class IncomeViewModel(private val repository: IncomeRepository) : ViewModel() {

    private val _state = MutableStateFlow(IncomeUiState())
    val state: StateFlow<IncomeUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            repository.list().fold(
                onSuccess = { summary ->
                    _state.update { it.copy(isLoading = false, ingresos = summary.ingresos, totalMes = summary.totalMes) }
                },
                onFailure = { e -> fail(e, "No se pudieron cargar tus ingresos.", isDelete = false) },
            )
        }
    }

    fun onNameChange(raw: String) {
        _state.update { it.copy(nameInput = raw.take(IncomeRules.MAX_NAME_LENGTH), message = null) }
    }

    fun onAmountChange(raw: String) {
        val value = sanitizeAmount(raw) ?: return
        _state.update { it.copy(amountInput = value, message = null) }
    }

    fun save() {
        val s = _state.value
        if (s.isSaving) return
        val name = s.nameInput.trim()
        if (name.isEmpty()) {
            return _state.update { it.copy(message = "Escribe el nombre del ingreso, por ejemplo \"Salario\".", isError = true) }
        }
        val amount = normalizedAmount(s.amountInput)
            ?: return _state.update { it.copy(message = "Escribe un monto mayor a \$0.", isError = true) }

        _state.update { it.copy(isSaving = true, message = null) }
        viewModelScope.launch {
            repository.add(name, amount).fold(
                onSuccess = {
                    _state.update {
                        it.copy(isSaving = false, nameInput = "", amountInput = "", message = "Ingreso \"$name\" guardado.", isError = false)
                    }
                    // El total del mes lo calcula el servidor: se recarga la lista.
                    refresh()
                },
                onFailure = { e -> fail(e, "No se pudo guardar el ingreso.", isDelete = false) },
            )
        }
    }

    fun requestDelete(income: IncomeDto) = _state.update { it.copy(pendingDelete = income) }

    fun cancelDelete() = _state.update { it.copy(pendingDelete = null) }

    fun confirmDelete() {
        val target = _state.value.pendingDelete ?: return
        _state.update { it.copy(pendingDelete = null, isSaving = true, message = null) }
        viewModelScope.launch {
            repository.delete(target.id).fold(
                onSuccess = {
                    _state.update { it.copy(isSaving = false, message = "Ingreso eliminado.", isError = false) }
                    refresh()
                },
                onFailure = { e -> fail(e, "No se pudo eliminar el ingreso.", isDelete = true) },
            )
        }
    }

    fun setFilter(filter: IncomeFilter) = _state.update { it.copy(filter = filter) }

    // ---------- Edición (ventana emergente) ----------

    /** Abre la ventana. Con [income] salta directo a editarlo; sin él, primero se elige cuál. */
    fun openEditor(income: IncomeDto? = null) {
        _state.update { it.copy(editor = IncomeEditorState(), message = null) }
        income?.let(::selectForEdit)
    }

    fun selectForEdit(income: IncomeDto) = _state.update {
        it.copy(
            editor = IncomeEditorState(
                selected = income,
                nameInput = income.nombre,
                amountInput = income.montoNeto.removeSuffix(".00"),
            ),
        )
    }

    fun backToSelection() = _state.update { s ->
        if (s.editor?.isSaving == true) s else s.copy(editor = IncomeEditorState())
    }

    fun closeEditor() = _state.update { s -> if (s.editor?.isSaving == true) s else s.copy(editor = null) }

    fun onEditNameChange(raw: String) = _state.update { s ->
        s.copy(editor = s.editor?.copy(nameInput = raw.take(IncomeRules.MAX_NAME_LENGTH), error = null))
    }

    fun onEditAmountChange(raw: String) {
        val value = sanitizeAmount(raw) ?: return
        _state.update { s -> s.copy(editor = s.editor?.copy(amountInput = value, error = null)) }
    }

    fun saveEdit() {
        val editor = _state.value.editor ?: return
        val target = editor.selected ?: return
        if (editor.isSaving) return
        val name = editor.nameInput.trim()
        if (name.isEmpty()) return setEditorError("Escribe el nombre del ingreso.")
        val amount = normalizedAmount(editor.amountInput) ?: return setEditorError("Escribe un monto mayor a \$0.")

        // Sin cambios: no hace falta llamar al servidor.
        if (name == target.nombre && amount.toDoubleOrNull() == target.montoNeto.toDoubleOrNull()) {
            return _state.update { it.copy(editor = null) }
        }

        _state.update { s -> s.copy(editor = s.editor?.copy(isSaving = true, error = null)) }
        viewModelScope.launch {
            repository.update(target.id, name, amount).fold(
                onSuccess = {
                    _state.update { it.copy(editor = null, message = "Ingreso \"$name\" actualizado.", isError = false) }
                    refresh()
                },
                onFailure = { e ->
                    val error = (e as? IncomeException)?.error ?: IncomeError.Unknown
                    if (error == IncomeError.Unauthorized) {
                        _state.update { it.copy(editor = null, sessionExpired = true) }
                        return@fold
                    }
                    _state.update { s ->
                        s.copy(
                            editor = s.editor?.copy(
                                isSaving = false,
                                error = when (error) {
                                    IncomeError.Network -> "Sin conexión con el servidor. Inténtalo de nuevo."
                                    IncomeError.Invalid -> "Revisa los datos: nombre obligatorio y monto mayor a \$0 con máximo 2 decimales."
                                    IncomeError.NotFound -> "Ese ingreso ya no existe."
                                    else -> "No se pudo actualizar el ingreso."
                                },
                            ),
                        )
                    }
                    if (error == IncomeError.NotFound) refresh()
                },
            )
        }
    }

    private fun setEditorError(message: String) =
        _state.update { s -> s.copy(editor = s.editor?.copy(error = message)) }

    private fun fail(e: Throwable, fallback: String, isDelete: Boolean) {
        val error = (e as? IncomeException)?.error ?: IncomeError.Unknown
        _state.update {
            it.copy(
                isLoading = false,
                isSaving = false,
                isError = true,
                sessionExpired = error == IncomeError.Unauthorized,
                message = when (error) {
                    IncomeError.Network -> "Sin conexión con el servidor. Inténtalo de nuevo."
                    IncomeError.Invalid -> "Revisa los datos: nombre obligatorio y monto mayor a \$0 con máximo 2 decimales."
                    // Al borrar, 404 significa que el ingreso ya no existe. Al listar o guardar, que el
                    // servidor no tiene la ruta (por ejemplo, sigue corriendo una versión anterior).
                    IncomeError.NotFound ->
                        if (isDelete) "Ese ingreso ya no existe."
                        else "El servidor no tiene disponible la sección de ingresos. Reinicia o actualiza el servidor."
                    IncomeError.Unauthorized -> "Tu sesión expiró."
                    IncomeError.Unknown -> fallback
                },
            )
        }
    }

    private companion object {
        const val MAX_INT_DIGITS = 10

        /** Solo dígitos y un punto decimal, máximo 10 enteros y 2 decimales (NUMERIC(12,2)); null si no es válido. */
        fun sanitizeAmount(raw: String): String? {
            val cleaned = raw.replace(',', '.').filter { it.isDigit() || it == '.' }
            val parts = cleaned.split('.')
            if (parts.size > 2) return null
            val intPart = parts[0].take(MAX_INT_DIGITS)
            return if (parts.size == 2) "$intPart.${parts[1].take(2)}" else intPart
        }

        /** "1500." -> "1500", ".5" -> "0.5"; null si no es mayor a 0. */
        fun normalizedAmount(input: String): String? {
            val trimmed = input.trimEnd('.').let { if (it.startsWith('.')) "0$it" else it }
            val value = trimmed.toDoubleOrNull() ?: return null
            return if (value > 0) trimmed else null
        }
    }
}
