package com.example.prisma.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.prisma.components.*
import com.example.prisma.ui.Prisma
import com.example.prisma.ui.PrismaPalette
import com.example.prisma.ui.SkeletonBox

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        DashboardSkeleton(modifier = modifier)
    } else {
        Box(modifier.fillMaxSize().background(Prisma.colors.background), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.errorMessage != null) {
                    ErrorCard(state.errorMessage, onRetry, Modifier.weight(1f))
                } else {
                    Header(state.userName, state.status)

                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        RadarCard(state)
                        AlertsCard(state)
                        Spacer(Modifier.height(BottomNavBarReservedSpace))
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardSkeleton(
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Prisma.colors.background), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header Skeleton
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonBox(Modifier.width(140.dp), height = 44.dp, shape = RoundedCornerShape(16.dp))
                SkeletonBox(Modifier.width(120.dp), height = 44.dp, shape = RoundedCornerShape(16.dp))
            }

            // Cards Skeleton
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Radar Card Skeleton
                SoftCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SkeletonBox(Modifier.width(70.dp), height = 16.dp)
                            SkeletonBox(Modifier.width(70.dp), height = 16.dp)
                            SkeletonBox(Modifier.width(70.dp), height = 16.dp)
                        }
                        SkeletonBox(Modifier.fillMaxWidth().aspectRatio(1.05f), shape = RoundedCornerShape(20.dp))
                    }
                }

                // Alerts Card Skeleton
                SoftCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SkeletonBox(Modifier.fillMaxWidth(), height = 48.dp, shape = RoundedCornerShape(16.dp))
                        SkeletonBox(Modifier.fillMaxWidth(), height = 48.dp, shape = RoundedCornerShape(16.dp))
                        SkeletonBox(Modifier.fillMaxWidth(), height = 52.dp, shape = RoundedCornerShape(16.dp))
                    }
                }
            }

        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        SoftCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = PrismaPalette.Dorado)) {
                    Text("Reintentar", color = PrismaPalette.Grafito)
                }
            }
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
        val greeting = if (userName.isNotBlank()) "Hola, $userName" else "Hola."
        Pill { Text(greeting, style = MaterialTheme.typography.bodyLarge) }
        Pill {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Estado:", style = MaterialTheme.typography.bodyLarge)
                Box(
                    Modifier
                        .size(20.dp)
                        .background(status.color(), CircleShape)
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

private fun Double.toMxn(): String {
    val grouped = toLong().toString().reversed().chunked(3).joinToString(",").reversed()
    return "\$$grouped"
}
