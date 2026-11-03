package com.example.prisma.routes

import com.example.prisma.api.DashboardResponse
import com.example.prisma.api.PaymentAlertDto
import com.example.prisma.auth.SessionStore
import com.example.prisma.db.DatabaseFactory.dbQuery
import com.example.prisma.http.authenticatedUser
import com.example.prisma.models.DatosPersonalesTable
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll

fun Route.dashboardRoutes(sessions: SessionStore) {
    get("/api/v1/reports/latest") {
        val userId = call.authenticatedUser(sessions) ?: return@get

        val userName = dbQuery {
            DatosPersonalesTable.selectAll()
                .where { DatosPersonalesTable.usuarioId eq userId }
                .singleOrNull()
                ?.get(DatosPersonalesTable.nombre)
                .orEmpty()
        }

        // TODO: calcular estado, alertas y total a partir de TARJETAS_CREDITO / TRANSACCIONES / RESUMEN_GASTOS.
        // Por ahora el nombre es real y el resto son datos de ejemplo fijos.
        call.respond(
            DashboardResponse(
                userName = userName,
                status = "Critical",
                radarAxes = listOf("Capacidad de Pago", "Alertas MSI", "Nivel de Ahorro", "Control de Gastos", "Uso de Crédito"),
                alerts = listOf(
                    PaymentAlertDto("Citi", "1635", 5),
                    PaymentAlertDto("Nu", "3489", 3),
                    PaymentAlertDto("MPago", "7630", 9),
                ),
                totalToPayMxn = 15000.0,
            )
        )
    }
}
