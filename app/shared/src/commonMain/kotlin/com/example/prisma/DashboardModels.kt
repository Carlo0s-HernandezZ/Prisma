package com.example.prisma.dashboard

import com.example.prisma.analysis.AnalysisException
import com.example.prisma.analysis.AnalysisRepository
import com.example.prisma.analysis.KtorAnalysisRepository
import com.example.prisma.api.HomeSummaryDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Estado de Inicio: el resumen real que calcula el servidor (sin datos de ejemplo). */
data class DashboardUiState(
    val summary: HomeSummaryDto? = null,
    val isLoading: Boolean = true,
    /** Mensaje si no se pudo cargar el resumen (no se muestran datos inventados). */
    val errorMessage: String? = null,
    /** El servidor rechazó el token: la app debe volver al login. */
    val sessionExpired: Boolean = false,
)

interface DashboardRepository {
    fun observeDashboard(): Flow<DashboardUiState>
}

class KtorDashboardRepository(
    accessToken: String,
    private val analysis: AnalysisRepository = KtorAnalysisRepository(accessToken),
) : DashboardRepository {

    override fun observeDashboard(): Flow<DashboardUiState> = flow {
        emit(
            analysis.home().fold(
                onSuccess = { DashboardUiState(summary = it, isLoading = false) },
                onFailure = { e ->
                    DashboardUiState(
                        isLoading = false,
                        errorMessage = e.message ?: "No se pudo cargar tu resumen. Inténtalo de nuevo.",
                        sessionExpired = (e as? AnalysisException)?.sessionExpired == true,
                    )
                },
            ),
        )
    }
}
