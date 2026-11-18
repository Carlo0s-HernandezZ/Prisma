package com.example.prisma.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prisma.api.CardDto
import com.example.prisma.api.SpendingDashboardDto
import com.example.prisma.api.StatementAnalysisDto
import com.example.prisma.cards.CardRepository
import com.example.prisma.cards.StatementUploadFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AnalysisUiState(
    val cards: List<CardDto> = emptyList(),
    /** Tarjeta elegida para subir y filtrar; null = todas. */
    val selectedCardId: String? = null,
    val analyses: List<StatementAnalysisDto> = emptyList(),
    val aiAvailable: Boolean = true,
    val isLoading: Boolean = true,
    val uploading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val expandedId: String? = null,
    /** Tarjeta del dashboard; null = todas. */
    val dashboardCardId: String? = null,
    val dashboard: SpendingDashboardDto? = null,
    val dashboardLoading: Boolean = false,
    val dashboardError: String? = null,
    val sessionExpired: Boolean = false,
) {
    val selectedCard: CardDto? get() = cards.firstOrNull { it.id == selectedCardId }

    /** Análisis visibles con el filtro de tarjeta. */
    val visible: List<StatementAnalysisDto>
        get() = if (selectedCardId == null) analyses else analyses.filter { it.tarjetaId == selectedCardId }

    /** El más reciente de cada tarjeta: base del resumen general. */
    val latestPerCard: List<StatementAnalysisDto>
        get() = analyses.groupBy { it.tarjetaId }.values.map { list -> list.maxBy { it.periodo } }
}

class AnalysisViewModel(
    private val repository: AnalysisRepository,
    private val cards: CardRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AnalysisUiState())
    val state: StateFlow<AnalysisUiState> = _state.asStateFlow()

    private var dashboardJob: Job? = null

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val cardList = cards.list().getOrElse { emptyList() }
            repository.list().fold(
                onSuccess = { response ->
                    _state.update { s ->
                        s.copy(
                            cards = cardList,
                            analyses = response.analisis,
                            aiAvailable = response.iaDisponible,
                            isLoading = false,
                            selectedCardId = s.selectedCardId?.takeIf { id -> cardList.any { it.id == id } }
                                ?: cardList.singleOrNull()?.id,
                            expandedId = s.expandedId ?: response.analisis.firstOrNull()?.id,
                        )
                    }
                },
                onFailure = ::fail,
            )
        }
        loadDashboard(_state.value.dashboardCardId)
    }

    fun selectCard(id: String?) {
        _state.update { it.copy(selectedCardId = id, message = null, error = null) }
    }

    fun toggle(id: String) {
        _state.update { it.copy(expandedId = if (it.expandedId == id) null else id) }
    }

    fun fileError(message: String) {
        _state.update { it.copy(error = message, message = null) }
    }

    fun upload(files: List<StatementUploadFile>) {
        val cardId = _state.value.selectedCardId ?: run {
            fileError("Elige primero la tarjeta a la que pertenece el estado de cuenta.")
            return
        }
        if (_state.value.uploading) return
        viewModelScope.launch {
            _state.update { it.copy(uploading = true, error = null, message = null) }
            repository.upload(cardId, files).fold(
                onSuccess = { analysis ->
                    _state.update { s ->
                        s.copy(
                            uploading = false,
                            analyses = (s.analyses.filterNot {
                                it.id == analysis.id || (it.tarjetaId == analysis.tarjetaId && it.periodo == analysis.periodo)
                            } + analysis).sortedByDescending { it.periodo },
                            expandedId = analysis.id,
                            message = "Listo: analizamos tu estado de ${analysis.banco}.",
                        )
                    }
                    loadDashboard(_state.value.dashboardCardId)
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
                onSuccess = {
                    _state.update { s ->
                        s.copy(analyses = s.analyses.filterNot { it.id == id }, message = "Análisis eliminado.")
                    }
                    loadDashboard(_state.value.dashboardCardId)
                },
                onFailure = ::fail,
            )
        }
    }

    fun loadDashboard(cardId: String?) {
        dashboardJob?.cancel()
        dashboardJob = viewModelScope.launch {
            _state.update { it.copy(dashboardCardId = cardId, dashboardLoading = true, dashboardError = null) }
            repository.dashboard(cardId).fold(
                onSuccess = { d -> _state.update { it.copy(dashboard = d, dashboardLoading = false) } },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            dashboardLoading = false,
                            dashboardError = e.message,
                            sessionExpired = it.sessionExpired || (e as? AnalysisException)?.sessionExpired == true,
                        )
                    }
                },
            )
        }
    }

    private fun fail(e: Throwable) {
        _state.update {
            it.copy(
                isLoading = false,
                error = e.message ?: "Ocurrió un error. Inténtalo de nuevo.",
                message = null,
                sessionExpired = it.sessionExpired || (e as? AnalysisException)?.sessionExpired == true,
            )
        }
    }
}
