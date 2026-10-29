package com.example.prisma.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.prisma.components.*
import com.example.prisma.ui.Prisma

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onNavigate: (NavDestination) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Centrado y con ancho máximo para que en Web/escritorio se vea como el mockup móvil.
    Box(modifier.fillMaxSize().background(Prisma.colors.background), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(state.userName, state.status)

            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                RadarCard(state)
                AlertsCard(state)
            }

            BottomNavBar(selected = NavDestination.Home, onSelect = onNavigate)
        }
    }
}

@Composable
private fun Header(userName: String, status: FinancialStatus) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Pill { Text("Hola, $userName", style = MaterialTheme.typography.bodyLarge) }
        Pill {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Estado:", style = MaterialTheme.typography.bodyLarge)
                Box(
                    Modifier
                        .size(20.dp)
                        .background(status.color(), CircleShape)
                        // El estado no debe depender solo del color (accesibilidad).
                        .semantics { contentDescription = "Estado financiero: ${status.label()}" },
                )
            }
        }
    }
}

@Composable
private fun RadarCard(state: DashboardUiState) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                state.radarSeries.sortedBy { it.type.ordinal }.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(8.dp).background(s.type.color(), CircleShape))
                        Text(s.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            RadarChart(
                axes = state.radarAxes,
                series = state.radarSeries.map { RadarSeries(it.type.color(), it.values) },
                modifier = Modifier.fillMaxWidth().aspectRatio(1.05f),
            )
        }
    }
}

@Composable
private fun AlertsCard(state: DashboardUiState) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            state.alerts.forEach { a ->
                val days = if (a.daysLeft == 1) "1 día" else "${a.daysLeft} días"
                Pill(Modifier.fillMaxWidth()) {
                    Text("Tu tarjeta ${a.bankName} ${a.lastFour} vence en $days.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Pill(Modifier.fillMaxWidth()) {
                Text(
                    "Tu total a pagar este mes es de ${state.totalToPayMxn.toMxn()}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

/** Cápsula con sombra usada por saludo, estado y cada mensaje. */
@Composable
private fun Pill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    SoftCard(modifier, shape = RoundedCornerShape(16.dp), elevation = 8.dp) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) { content() }
    }
}

@Composable
private fun FinancialStatus.color(): Color = when (this) {
    FinancialStatus.Healthy -> Prisma.colors.statusHealthy
    FinancialStatus.Warning -> Prisma.colors.statusWarning
    FinancialStatus.Critical -> Prisma.colors.statusCritical
}

private fun FinancialStatus.label(): String = when (this) {
    FinancialStatus.Healthy -> "saludable"
    FinancialStatus.Warning -> "en alerta"
    FinancialStatus.Critical -> "crítico"
}

@Composable
private fun RadarSeriesType.color(): Color = when (this) {
    RadarSeriesType.Income -> Prisma.colors.income
    RadarSeriesType.Expense -> Prisma.colors.expense
    RadarSeriesType.Investment -> Prisma.colors.investment
}

/** 15000.0 -> "$15,000" */
private fun Double.toMxn(): String {
    val grouped = toLong().toString().reversed().chunked(3).joinToString(",").reversed()
    return "\$$grouped"
}
