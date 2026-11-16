@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.prisma.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prisma.api.StatementPayment
import com.example.prisma.api.StatementPaymentsDto
import com.example.prisma.api.StatementPaymentsRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

data class StatementPaymentEditor(
    val id: String,
    val fecha: String = "",
    val monto: String = "",
    val error: String? = null,
)

data class StatementPaymentsUiState(
    val data: StatementPaymentsDto? = null,
    val draft: List<StatementPayment>? = null,
    val editor: StatementPaymentEditor? = null,
    val acknowledged: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val conflict: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val sessionExpired: Boolean = false,
)

class StatementPaymentsViewModel(
    private val cardId: String,
    private val periodo: String,
    private val repository: StatementPaymentsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(
        StatementPaymentsUiState(),
    )

    val state = _state.asStateFlow()

    init {
        reload()
    }

    private fun busy(
        state: StatementPaymentsUiState,
    ): Boolean =
        state.isLoading || state.isSaving

    private fun editable(
        state: StatementPaymentsUiState,
    ): Boolean =
        state.data != null &&
                state.draft != null &&
                !busy(state) &&
                !state.conflict &&
                !state.sessionExpired

    fun reload() {
        val current = _state.value

        if (busy(current) || current.sessionExpired) {
            return
        }

        if (current.draft != null || current.editor != null) {
            _state.update {
                it.copy(
                    error = "Descarta la edición antes de recargar los pagos.",
                )
            }
            return
        }

        _state.update {
            it.copy(
                isLoading = true,
                error = null,
                message = null,
            )
        }

        viewModelScope.launch {
            repository.get(cardId, periodo).fold(
                onSuccess = { dto ->
                    if (!validData(dto)) {
                        fail(
                            StatementPaymentsException(
                                null,
                                "Los pagos recibidos no son válidos. Recarga los datos.",
                                requiresReload = true,
                            ),
                        )
                    } else {
                        _state.update {
                            it.copy(
                                data = dto,
                                isLoading = false,
                                conflict = false,
                                error = null,
                            )
                        }
                    }
                },
                onFailure = ::fail,
            )
        }
    }

    fun beginEdit() {
        val current = _state.value
        val data = current.data ?: return

        if (
            busy(current) ||
            current.conflict ||
            current.sessionExpired ||
            current.draft != null
        ) {
            return
        }

        _state.update {
            it.copy(
                draft = data.pagos.toList(),
                acknowledged = false,
                error = null,
                message = null,
            )
        }
    }

    fun addPayment() {
        val current = _state.value

        if (!editable(current) || current.editor != null) {
            return
        }

        if (current.draft!!.size >= 100) {
            _state.update {
                it.copy(
                    error = "Puedes registrar hasta 100 pagos por estado.",
                )
            }
            return
        }

        _state.update {
            it.copy(
                editor = StatementPaymentEditor(
                    id = Uuid.random().toString(),
                    fecha = current.data!!.fechaReferencia,
                ),
                acknowledged = false,
                error = null,
                message = null,
            )
        }
    }

    fun editPayment(id: String) {
        val current = _state.value

        if (!editable(current) || current.editor != null) {
            return
        }

        val payment = current.draft!!.singleOrNull {
            it.id == id
        } ?: return

        _state.update {
            it.copy(
                editor = StatementPaymentEditor(
                    id = payment.id,
                    fecha = payment.fechaAplicacion,
                    monto = payment.monto,
                ),
                acknowledged = false,
                error = null,
                message = null,
            )
        }
    }

    fun removePayment(id: String) {
        _state.update {
            if (!editable(it) || it.editor != null) {
                it
            } else {
                it.copy(
                    draft = it.draft!!.filterNot { payment ->
                        payment.id == id
                    },
                    acknowledged = false,
                    error = null,
                    message = null,
                )
            }
        }
    }

    fun onDateChange(value: String) =
        editEditor {
            it.copy(fecha = value)
        }

    fun onAmountChange(value: String) =
        editEditor {
            it.copy(monto = value.take(40))
        }

    private fun editEditor(
        transform: (StatementPaymentEditor) -> StatementPaymentEditor,
    ) {
        _state.update {
            if (!editable(it) || it.editor == null) {
                it
            } else {
                it.copy(
                    editor = transform(it.editor).copy(
                        error = null,
                    ),
                    acknowledged = false,
                    error = null,
                )
            }
        }
    }

    fun closeEditor() {
        if (!busy(_state.value)) {
            _state.update {
                it.copy(editor = null)
            }
        }
    }

    fun savePayment() {
        val current = _state.value

        if (!editable(current)) {
            return
        }

        val editor = current.editor ?: return
        val cutoff = date(current.data!!.estado.campos.fechaCorte)
        val today = date(current.data.fechaReferencia)
        val applied = date(editor.fecha)
        val amount = money(editor.monto)

        val error = when {
            applied == null ||
                    cutoff == null ||
                    today == null ||
                    applied <= cutoff ||
                    applied > today ->
                "Selecciona una fecha posterior al corte y hasta hoy."

            amount == null ->
                "Escribe un importe mayor a cero, con punto decimal y hasta dos decimales."

            else -> null
        }

        if (error != null) {
            _state.update {
                it.copy(
                    editor = editor.copy(error = error),
                )
            }
            return
        }

        val payment = StatementPayment(
            id = editor.id,
            fechaAplicacion = applied!!.toString(),
            monto = amount!!,
        )

        _state.update {
            it.copy(
                draft = (
                        it.draft!!.filterNot { item ->
                            item.id == payment.id
                        } + payment
                        ).sortedWith(
                        compareBy(
                            { item -> item.fechaAplicacion },
                            { item -> item.id },
                        ),
                    ),
                editor = null,
                acknowledged = false,
                error = null,
                message = null,
            )
        }
    }

    fun setAcknowledged(value: Boolean) {
        _state.update {
            if (!editable(it) || it.editor != null) {
                it
            } else {
                it.copy(
                    acknowledged = value,
                    error = null,
                )
            }
        }
    }

    fun cancelEdit() {
        _state.update {
            if (busy(it)) {
                it
            } else {
                it.copy(
                    draft = null,
                    editor = null,
                    acknowledged = false,
                    error = if (it.conflict) it.error else null,
                    message = null,
                )
            }
        }
    }

    fun discardAndReload() {
        if (busy(_state.value)) {
            return
        }

        _state.update {
            it.copy(
                draft = null,
                editor = null,
                acknowledged = false,
            )
        }

        reload()
    }

    fun save() {
        val current = _state.value

        if (!editable(current) || current.editor != null) {
            return
        }

        if (!current.acknowledged) {
            _state.update {
                it.copy(
                    error = "Confirma que revisaste los pagos aplicados a este estado.",
                )
            }
            return
        }

        val data = current.data!!

        if (data.version == Int.MAX_VALUE) {
            _state.update {
                it.copy(
                    error = "Se alcanzó el límite de revisiones de estos pagos.",
                )
            }
            return
        }

        val request = StatementPaymentsRequest(
            pagos = current.draft!!,
            version = data.version,
            versionEstado = data.estado.version,
            confirmadoPorUsuario = true,
        )

        _state.update {
            it.copy(
                isSaving = true,
                error = null,
                message = null,
            )
        }

        viewModelScope.launch {
            repository.save(
                cardId,
                periodo,
                request,
            ).fold(
                onSuccess = { dto ->
                    if (
                        !validData(dto) ||
                        dto.version != data.version + 1 ||
                        dto.versionEstado != data.estado.version ||
                        dto.estado.version != data.estado.version ||
                        dto.requiereRevision ||
                        dto.resumen == null ||
                        dto.pagos.sortedBy { it.id } !=
                        request.pagos.sortedBy { it.id }
                    ) {
                        fail(
                            StatementPaymentsException(
                                null,
                                "No se pudo comprobar el guardado. " +
                                        "Recarga antes de continuar.",
                                requiresReload = true,
                            ),
                        )
                    } else {
                        _state.update {
                            it.copy(
                                data = dto,
                                draft = null,
                                editor = null,
                                acknowledged = false,
                                isSaving = false,
                                conflict = false,
                                error = null,
                                message = "Pagos registrados y estimación actualizada.",
                            )
                        }
                    }
                },
                onFailure = ::fail,
            )
        }
    }

    private fun fail(error: Throwable) {
        val failure = error as? StatementPaymentsException

        _state.update {
            it.copy(
                isLoading = false,
                isSaving = false,
                error = error.message
                    ?: "No se pudo completar la solicitud de pagos.",
                conflict = it.conflict ||
                        failure?.status == 409 ||
                        failure?.requiresReload == true,
                sessionExpired = it.sessionExpired ||
                        failure?.status == 401,
            )
        }
    }

    private fun validData(
        dto: StatementPaymentsDto,
    ): Boolean {
        val fields = dto.estado.campos
        val cutoff = date(fields.fechaCorte) ?: return false
        val due = date(fields.fechaLimitePago) ?: return false
        val today = date(dto.fechaReferencia) ?: return false

        if (
            dto.estado.tarjetaId != cardId ||
            fields.periodo != periodo ||
            cutoff.toString().take(7) != periodo ||
            dto.estado.version <= 0 ||
            dto.estado.fechaConfirmacion.isBlank() ||
            due < cutoff ||
            fields.pagoMinimo.isNullOrBlank() ||
            fields.pagoParaNoGenerarIntereses.isNullOrBlank() ||
            dto.version < 0 ||
            dto.pagos.size > 100 ||
            dto.pagos.map { it.id }.distinct().size != dto.pagos.size
        ) {
            return false
        }

        if (
            dto.pagos.any {
                !canonicalId(it.id) ||
                        date(it.fechaAplicacion)?.let { applied ->
                            applied > today
                        } != false ||
                        money(it.monto) != it.monto
            }
        ) {
            return false
        }

        if (dto.version == 0) {
            return dto.versionEstado == null &&
                    dto.pagos.isEmpty() &&
                    dto.resumen == null &&
                    !dto.requiereRevision
        }

        val anchor = dto.versionEstado ?: return false

        if (
            anchor <= 0 ||
            anchor > dto.estado.version ||
            dto.requiereRevision != (anchor != dto.estado.version)
        ) {
            return false
        }

        if (dto.requiereRevision) {
            return dto.resumen == null
        }

        val summary = dto.resumen ?: return false

        return dto.pagos.none {
            date(it.fechaAplicacion)!! <= cutoff
        } && listOf(
            summary.totalAplicado,
            summary.aplicadoHastaFechaLimite,
            summary.faltanteMinimo,
            summary.faltanteParaNoGenerarIntereses,
            summary.faltanteMinimoAlVencimiento,
            summary.faltanteParaNoGenerarInteresesAlVencimiento,
        ).all {
            units(
                it,
                positive = false,
                maximum = 99_999_999_999_900L,
            ) != null
        } && summary.plazoVencido == (today > due)
    }

    private fun canonicalId(value: String): Boolean =
        runCatching {
            Uuid.parse(value).toString() == value
        }.getOrDefault(false)

    private fun date(value: String?): LocalDate? {
        if (
            value == null ||
            !Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(value)
        ) {
            return null
        }

        return runCatching {
            LocalDate.parse(value).takeIf {
                it.year in 1..9999
            }
        }.getOrNull()
    }

    private fun money(value: String): String? =
        units(value)?.let {
            (it / 100).toString() +
                    "." +
                    (it % 100).toString().padStart(2, '0')
        }

    private fun units(
        value: String,
        positive: Boolean = true,
        maximum: Long = 999_999_999_999L,
    ): Long? {
        val text = value.trim()

        if (
            text.length > 40 ||
            !Regex(
                "(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]{1,2})?",
            ).matches(text)
        ) {
            return null
        }

        val parts = text.replace(",", "").split('.')
        val whole = parts[0].toLongOrNull() ?: return null

        if (whole > maximum / 100) {
            return null
        }

        val cents = whole * 100 +
                parts.getOrElse(1) {
                    ""
                }.padEnd(2, '0').toLong()

        return cents.takeIf {
            it <= maximum &&
                    if (positive) it > 0 else it >= 0
        }
    }
}