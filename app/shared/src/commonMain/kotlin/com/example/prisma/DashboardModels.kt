package com.example.prisma.dashboard

import com.example.prisma.defaultBaseUrl
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable

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
)

interface DashboardRepository {
    fun observeDashboard(): Flow<DashboardUiState>
}

class FakeDashboardRepository : DashboardRepository {
    override fun observeDashboard(): Flow<DashboardUiState> = flowOf(
        DashboardUiState(
            userName = "Casandra",
            status = FinancialStatus.Critical,
            radarAxes = listOf(
                "Capacidad de Pago", "Alertas MSI", "Nivel de Ahorro",
                "Control de Gastos", "Uso de Crédito",
            ),
            radarSeries = listOf(
                RadarSeriesData(RadarSeriesType.Investment, "Inversión", listOf(9.5f, 10f, 6f, 6f, 4.5f)),
                RadarSeriesData(RadarSeriesType.Expense, "Egresos", listOf(4f, 5f, 9f, 8f, 3f)),
                RadarSeriesData(RadarSeriesType.Income, "Ingresos", listOf(2f, 1.5f, 1.5f, 3f, 8f)),
            ),
            alerts = listOf(
                PaymentAlert("Citi", "1635", 5),
                PaymentAlert("Nu", "3489", 3),
                PaymentAlert("MPago", "7630", 9),
            ),
            totalToPayMxn = 15_000.0,
        )
    )
}

@Serializable
data class DashboardResponseDto(
    val userName: String,
    val status: String,
    val radarAxes: List<String>,
    val alerts: List<PaymentAlertDto>,
    val totalToPayMxn: Double
)

@Serializable
data class PaymentAlertDto(val bankName: String, val lastFour: String, val daysLeft: Int)

class KtorDashboardRepository(private val baseUrl: String = "http://127.0.0.1:8080") : DashboardRepository {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json()
        }
    }

    override fun observeDashboard(): Flow<DashboardUiState> = flow {
        try {
            val response = client.get("$baseUrl/api/v1/reports/latest")
            if (response.status.isSuccess()) {
                val dto: DashboardResponseDto = response.body()
                emit(
                    DashboardUiState(
                        userName = dto.userName,
                        status = when (dto.status) {
                            "Critical" -> FinancialStatus.Critical
                            "Warning" -> FinancialStatus.Warning
                            else -> FinancialStatus.Healthy
                        },
                        radarAxes = dto.radarAxes,
                        radarSeries = listOf(
                            RadarSeriesData(RadarSeriesType.Investment, "Inversión", listOf(9.5f, 10f, 6f, 6f, 4.5f)),
                            RadarSeriesData(RadarSeriesType.Expense, "Egresos", listOf(4f, 5f, 9f, 8f, 3f)),
                            RadarSeriesData(RadarSeriesType.Income, "Ingresos", listOf(2f, 1.5f, 1.5f, 3f, 8f))
                        ),
                        alerts = dto.alerts.map { PaymentAlert(it.bankName, it.lastFour, it.daysLeft) },
                        totalToPayMxn = dto.totalToPayMxn
                    )
                )
                return@flow
            }
        } catch (_: Exception) {}

        emit(
            DashboardUiState(
                userName = "Casandra",
                status = FinancialStatus.Critical,
                radarAxes = listOf(
                    "Capacidad de Pago", "Alertas MSI", "Nivel de Ahorro",
                    "Control de Gastos", "Uso de Crédito",
                ),
                radarSeries = listOf(
                    RadarSeriesData(RadarSeriesType.Investment, "Inversión", listOf(9.5f, 10f, 6f, 6f, 4.5f)),
                    RadarSeriesData(RadarSeriesType.Expense, "Egresos", listOf(4f, 5f, 9f, 8f, 3f)),
                    RadarSeriesData(RadarSeriesType.Income, "Ingresos", listOf(2f, 1.5f, 1.5f, 3f, 8f)),
                ),
                alerts = listOf(
                    PaymentAlert("Citi", "1635", 5),
                    PaymentAlert("Nu", "3489", 3),
                    PaymentAlert("MPago", "7630", 9),
                ),
                totalToPayMxn = 15_000.0,
            )
        )
    }
}
