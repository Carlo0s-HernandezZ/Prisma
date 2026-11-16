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
import com.example.prisma.ui.SkeletonCircle
import com.example.prisma.ui.SkeletonText
import com.example.prisma.ui.SkeletonTextFraction
import com.example.prisma.ui.drawSkeletonShine
import com.example.prisma.ui.loadingSemantics
import com.example.prisma.ui.rememberSkeletonProgress
import com.example.prisma.ui.skeletonColors
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import com.example.prisma.ui.isDesktopLayout
import com.example.prisma.ui.screenContainer

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
                modifier = Modifier.screenContainer(),
                verticalArrangement = Arrangement.spacedBy(if (isDesktopLayout) 24.dp else 16.dp),
            ) {
                if (state.errorMessage != null) {
                    ErrorCard(state.errorMessage, onRetry, Modifier.weight(1f))
                } else {
                    Header(state.userName, state.status)

                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        DashboardCards(state)
                        Spacer(Modifier.height(bottomNavBarSpace))
                    }
                }
            }
        }
    }
}

/**
 * Esqueleto del Inicio: misma estructura que la pantalla real (saludo y estado arriba,
 * radar con su leyenda y alertas; lado a lado en escritorio ancho).
 */
@Composable
fun DashboardSkeleton(
    modifier: Modifier = Modifier,
) {
    val type = MaterialTheme.typography
    Box(
        modifier.fillMaxSize().background(Prisma.colors.background).loadingSemantics("Cargando tu resumen"),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.screenContainer(),
            verticalArrangement = Arrangement.spacedBy(if (isDesktopLayout) 24.dp else 16.dp),
        ) {
            // Encabezado: "Hola, …" y "Estado: ●"
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pill { SkeletonText(type.bodyLarge, 110.dp) }
                Pill {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeletonText(type.bodyLarge, 58.dp)
                        SkeletonCircle(20.dp)
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (!isDesktopLayout) {
                    RadarCardSkeleton()
                    AlertsCardSkeleton()
                } else {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        if (maxWidth >= 960.dp) {
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                                RadarCardSkeleton(Modifier.weight(1f).widthIn(max = 620.dp))
                                AlertsCardSkeleton(Modifier.weight(1f))
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                RadarCardSkeleton(Modifier.widthIn(max = 620.dp).align(Alignment.CenterHorizontally))
                                AlertsCardSkeleton()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RadarCardSkeleton(modifier: Modifier = Modifier) {
    SoftCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Leyenda: Ingresos · Gastos · Inversión
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(52.dp, 44.dp, 56.dp).forEach { w ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SkeletonCircle(8.dp)
                        SkeletonText(MaterialTheme.typography.labelSmall, w)
                    }
                }
            }
            RadarSkeleton(
                axes = 5,
                modifier = Modifier
                    .padding(horizontal = if (isDesktopLayout) 24.dp else 0.dp)
                    .fillMaxWidth()
                    .aspectRatio(1.05f),
            )
        }
    }
}

/** Pentágono del radar y etiquetas de sus ejes, con la misma geometría que RadarChart. */
@Composable
private fun RadarSkeleton(axes: Int, modifier: Modifier = Modifier) {
    val colors = skeletonColors()
    val progress = rememberSkeletonProgress()
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width / 2f - 56.dp.toPx(), size.height / 2f - 22.dp.toPx())
        fun point(i: Int, r: Float): Offset {
            val a = (-PI / 2 + i * 2 * PI / axes).toFloat()
            return Offset(center.x + r * cos(a), center.y + r * sin(a))
        }
        val polygon = Path().apply {
            for (i in 0 until axes) {
                val p = point(i, radius)
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
        clipPath(polygon) {
            drawRect(colors.base)
            drawSkeletonShine(progress.value, colors.shine)
        }
        // Etiquetas de los ejes (texto de 10 sp en el radar real).
        val labelW = 64.dp.toPx()
        val labelH = 8.dp.toPx()
        for (i in 0 until axes) {
            val p = point(i, radius + 16.dp.toPx())
            val left = (p.x - labelW / 2f).coerceIn(0f, size.width - labelW)
            drawRoundRect(
                color = colors.base,
                topLeft = Offset(left, p.y - labelH / 2f),
                size = Size(labelW, labelH),
                cornerRadius = CornerRadius(labelH / 2f),
            )
        }
    }
}

@Composable
private fun AlertsCardSkeleton(modifier: Modifier = Modifier) {
    val type = MaterialTheme.typography
    SoftCard(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // "Tu tarjeta … vence en … días." (dos alertas de ejemplo)
            listOf(0.85f, 0.7f).forEach { f ->
                Pill(Modifier.fillMaxWidth()) { SkeletonTextFraction(type.bodyMedium, f) }
            }
            // "Tu total a pagar este mes es de $…"
            Pill(Modifier.fillMaxWidth()) { SkeletonTextFraction(type.titleMedium, 0.9f) }
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

/**
 * Radar y alertas: apiladas en móvil; lado a lado en escritorio cuando hay al menos 960 dp.
 */
@Composable
private fun DashboardCards(state: DashboardUiState) {
    if (!isDesktopLayout) {
        RadarCard(state)
        AlertsCard(state)
        return
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 960.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                RadarCard(state, Modifier.weight(1f).widthIn(max = 620.dp))
                AlertsCard(state, Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                RadarCard(state, Modifier.widthIn(max = 620.dp).align(Alignment.CenterHorizontally))
                AlertsCard(state)
            }
        }
    }
}

@Composable
private fun RadarCard(state: DashboardUiState, modifier: Modifier = Modifier) {
    SoftCard(modifier.fillMaxWidth()) {
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
                // En escritorio la tarjeta es más ancha: margen extra para que no se corten las etiquetas.
                modifier = Modifier
                    .padding(horizontal = if (isDesktopLayout) 24.dp else 0.dp)
                    .fillMaxWidth()
                    .aspectRatio(1.05f),
            )
        }
    }
}

@Composable
private fun AlertsCard(state: DashboardUiState, modifier: Modifier = Modifier) {
    SoftCard(modifier.fillMaxWidth()) {
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
