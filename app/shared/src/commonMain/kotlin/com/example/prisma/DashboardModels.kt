package com.example.prisma.dashboard

import com.example.prisma.api.DashboardResponse
import com.example.prisma.defaultBaseUrl
import com.example.prisma.network.prismaHttpClient
import com.example.prisma.network.withToken
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlin.coroutines.cancellation.CancellationException

enum class FinancialStatus { Healthy, Warning, Critical }

enum class RadarSeriesType { Income, Expense, Investment }

data class RadarSeriesData(
    val type: RadarSeriesType,
    val label: String,
    /** Un valor 0..10 por cada eje, en el mismo orden que radarAxes. */
    val values: List<Float>,
)

data class PaymentAlert(
    val bankName: String,
    val lastFour: String,
    val daysLeft: Int,
)

data class DashboardUiState(
    val userName: String = "",
    val status: FinancialStatus = FinancialStatus.Healthy,
    val radarAxes: List<String> = emptyList(),
    val radarSeries: List<RadarSeriesData> = emptyList(),
    val alerts: List<PaymentAlert> = emptyList(),
    val totalToPayMxn: Double = 0.0,
    val isLoading: Boolean = true,
    /** Mensaje si no se pudo cargar el reporte (no se muestran datos inventados). */
    val errorMessage: String? = null,
    /** El servidor rechazó el token: la app debe volver al login. */
    val sessionExpired: Boolean = false,
)

interface DashboardRepository {
    fun observeDashboard(): Flow<DashboardUiState>
}

private val DEFAULT_RADAR_AXES = listOf(
    "Capacidad de Pago", "Alertas MSI", "Nivel de Ahorro", "Control de Gastos", "Uso de Crédito",
)

// TODO: el servidor aún no envía las series del radar; mientras tanto se usan valores de ejemplo.
private val SAMPLE_RADAR_SERIES = listOf(
    RadarSeriesData(RadarSeriesType.Investment, "Inversión", listOf(9.5f, 10f, 6f, 6f, 4.5f)),
    RadarSeriesData(RadarSeriesType.Expense, "Egresos", listOf(4f, 5f, 9f, 8f, 3f)),
    RadarSeriesData(RadarSeriesType.Income, "Ingresos", listOf(2f, 1.5f, 1.5f, 3f, 8f)),
)

class FakeDashboardRepository : DashboardRepository {
    override fun observeDashboard(): Flow<DashboardUiState> = flowOf(
        DashboardUiState(
            status = FinancialStatus.Critical,
            radarAxes = DEFAULT_RADAR_AXES,
            radarSeries = SAMPLE_RADAR_SERIES,
            alerts = listOf(
                PaymentAlert("Citi", "1635", 5),
                PaymentAlert("Nu", "3489", 3),
                PaymentAlert("MPago", "7630", 9),
            ),
            totalToPayMxn = 15_000.0,
            isLoading = false,
        )
    )
}

class KtorDashboardRepository(
    private val accessToken: String,
    private val baseUrl: String = defaultBaseUrl(),
    private val client: HttpClient = prismaHttpClient,
) : DashboardRepository {

    override fun observeDashboard(): Flow<DashboardUiState> = flow {
        val result = try {
            val response = client.get("$baseUrl/api/v1/reports/latest") { withToken(accessToken) }
            when {
                response.status.isSuccess() -> response.body<DashboardResponse>().toUiState()
                response.status == HttpStatusCode.Unauthorized ->
                    DashboardUiState(isLoading = false, sessionExpired = true, errorMessage = "Tu sesión expiró.")
                else -> DashboardUiState(isLoading = false, errorMessage = "No se pudo cargar tu resumen. Inténtalo de nuevo.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            DashboardUiState(isLoading = false, errorMessage = "Sin conexión con el servidor. Revisa tu internet e inténtalo de nuevo.")
        }
        emit(result)
    }

    private fun DashboardResponse.toUiState() = DashboardUiState(
        userName = userName,
        status = when (status) {
            "Critical" -> FinancialStatus.Critical
            "Warning" -> FinancialStatus.Warning
            else -> FinancialStatus.Healthy
        },
        radarAxes = radarAxes,
        radarSeries = SAMPLE_RADAR_SERIES,
        alerts = alerts.map { PaymentAlert(it.bankName, it.lastFour, it.daysLeft) },
        totalToPayMxn = totalToPayMxn,
        isLoading = false,
    )
}
