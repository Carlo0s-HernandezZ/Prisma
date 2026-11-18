package com.example.prisma.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.prisma.api.FinancialHealthDto
import com.example.prisma.api.HealthLevel
import com.example.prisma.api.HomeCardDto
import com.example.prisma.api.HomeMessageDto
import com.example.prisma.api.HomeMessageType
import com.example.prisma.api.HomeSummaryDto
import com.example.prisma.components.SoftCard
import com.example.prisma.components.bottomNavBarSpace
import com.example.prisma.ui.Prisma
import com.example.prisma.ui.PrismaPalette
import com.example.prisma.ui.SkeletonCircle
import com.example.prisma.ui.SkeletonTextFraction
import com.example.prisma.ui.SourceLink
import com.example.prisma.ui.charts.HealthGauge
import com.example.prisma.ui.charts.MonthBar
import com.example.prisma.ui.charts.MonthlySpendChart
import com.example.prisma.ui.charts.SectionLabel
import com.example.prisma.ui.charts.compactMxn
import com.example.prisma.ui.charts.dayMonth
import com.example.prisma.ui.charts.toAmount
import com.example.prisma.ui.formatMxn
import com.example.prisma.ui.isDesktopLayout
import com.example.prisma.ui.loadingSemantics
import com.example.prisma.ui.screenContainer

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onRetry: () -> Unit = {},
    onOpenAnalysis: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Prisma.colors.background), contentAlignment = Alignment.TopCenter) {
        val summary = state.summary
        when {
            state.isLoading -> DashboardSkeleton()
            summary == null -> ErrorCard(state.errorMessage ?: "No se pudo cargar tu resumen.", onRetry)
            else -> HomeContent(summary, onOpenAnalysis)
        }
    }
}

@Composable
private fun HomeContent(s: HomeSummaryDto, onOpenAnalysis: () -> Unit) {
    val desktop = isDesktopLayout
    Column(
        Modifier.screenContainer().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(if (desktop) 24.dp else 16.dp),
    ) {
        Column {
            Text(
                if (s.userName.isNotBlank()) "Hola, ${s.userName}" else "Hola",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            SectionLabel("Tu futuro en tus manos")
        }

        if (desktop) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    WelcomeCard(s, onOpenAnalysis)
                    CardsPanel(s.tarjetas)
                    SpendingPanel(s, onOpenAnalysis)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    MessagesPanel(s.mensajes)
                    HealthFactorsPanel(s.salud)
                }
            }
        } else {
            WelcomeCard(s, onOpenAnalysis)
            MessagesPanel(s.mensajes)
            CardsPanel(s.tarjetas)
            SpendingPanel(s, onOpenAnalysis)
            HealthFactorsPanel(s.salud)
        }
        Spacer(Modifier.height(bottomNavBarSpace))
    }
}

// ---------------------------------------------------------------------------------------------
// Tarjeta de bienvenida: salud financiera + tarjetas + ingresos + gasto
// ---------------------------------------------------------------------------------------------

@Composable
private fun HealthLevel.color(): Color = when (this) {
    HealthLevel.SALUDABLE -> Prisma.colors.statusHealthy
    HealthLevel.ALERTA -> PrismaPalette.NaranjaPrisma
    HealthLevel.CRITICA -> Prisma.colors.statusCritical
    HealthLevel.SIN_DATOS -> Prisma.colors.onSurface.copy(alpha = 0.5f)
}

private fun HealthLevel.label(): String = when (this) {
    HealthLevel.SALUDABLE -> "Saludable"
    HealthLevel.ALERTA -> "En alerta"
    HealthLevel.CRITICA -> "Crítica"
    HealthLevel.SIN_DATOS -> "Sin datos"
}

@Composable
private fun WelcomeCard(s: HomeSummaryDto, onOpenAnalysis: () -> Unit) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionLabel("Tu salud financiera")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HealthGauge(s.salud.puntaje, s.salud.nivel.label(), s.salud.nivel.color(), Modifier.width(150.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Metric(
                        "Deuda en tarjetas",
                        formatMxn(s.deudaTotal),
                        "${s.tarjetas.size} tarjeta${if (s.tarjetas.size == 1) "" else "s"}",
                    )
                    Metric(
                        "Ingresos del mes",
                        formatMxn(s.ingresosMes),
                        s.ingresoMensualEstimado.takeIf { it != s.ingresosMes && it.toAmount() > 0 }
                            ?.let { "Estimado mensual: ${compactMxn(it.toAmount())}" },
                    )
                }
            }
            SpendComparison(s)
            Text(s.salud.resumen, style = MaterialTheme.typography.bodyMedium)
            if (s.salud.nivel == HealthLevel.SIN_DATOS) {
                Button(
                    onClick = onOpenAnalysis,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = PrismaPalette.Dorado, contentColor = PrismaPalette.Grafito),
                ) { Text("Subir mi estado de cuenta", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun Metric(title: String, value: String, caption: String?) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = Prisma.colors.onSurface.copy(alpha = 0.75f))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        caption?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Prisma.colors.onSurface.copy(alpha = 0.7f)) }
    }
}

@Composable
private fun SpendComparison(s: HomeSummaryDto) {
    val last = s.gastoUltimoMes?.toAmount() ?: return
    // El % lo calcula el servidor solo con las tarjetas que tienen estado en ambos meses.
    val change = s.cambioPorcentaje
    val up = change != null && change > 0
    val color = when {
        change == null -> Prisma.colors.onSurface
        up -> Prisma.colors.statusCritical
        else -> Prisma.colors.statusHealthy
    }
    Row(
        Modifier.fillMaxWidth().background(color.copy(alpha = 0.08f), RoundedCornerShape(14.dp)).padding(12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (change != null) {
            Icon(if (up) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown, contentDescription = null, tint = color)
        }
        Text(
            buildString {
                append("Tu último mes: gastaste ${compactMxn(last)}")
                when {
                    change == null -> append(".")
                    change >= 0 -> append(", $change% más que el anterior")
                    else -> append(", ${-change}% menos que el anterior")
                }
                if (change != null) append(if (s.comparacionParcial) " (en las tarjetas con estado en ambos meses)." else ".")
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Mensajes: pagos, cortes, cargos raros, meses por terminar y consejo del día
// ---------------------------------------------------------------------------------------------

@Composable
private fun MessagesPanel(messages: List<HomeMessageDto>) {
    Panel("Mensajes") {
        if (messages.isEmpty()) {
            Text("No tienes avisos pendientes.", style = MaterialTheme.typography.bodyMedium)
        }
        messages.forEach { MessageRow(it) }
    }
}

@Composable
private fun MessageRow(m: HomeMessageDto) {
    val uriHandler = LocalUriHandler.current
    val (icon, color) = when (m.tipo) {
        HomeMessageType.PAGO -> Icons.Filled.Payments to (if (m.urgente) Prisma.colors.statusCritical else PrismaPalette.NaranjaAmbar)
        HomeMessageType.CORTE -> Icons.Filled.Event to PrismaPalette.NaranjaPrisma
        HomeMessageType.ALERTA -> Icons.Filled.Warning to Prisma.colors.statusCritical
        HomeMessageType.MESES -> Icons.Filled.CalendarMonth to Prisma.colors.statusHealthy
        HomeMessageType.CONSEJO -> Icons.Filled.Lightbulb to PrismaPalette.Dorado
        HomeMessageType.NOMINA -> Icons.Filled.Description to (if (m.urgente) Prisma.colors.statusCritical else PrismaPalette.NaranjaAmbar)
        HomeMessageType.RECORDATORIO -> Icons.Filled.Alarm to PrismaPalette.NaranjaPrisma
    }
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.08f)),
    ) {
        // Franja de color a la izquierda: rojo si es urgente.
        Box(Modifier.width(4.dp).fillMaxHeight().background(color))
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (m.urgente) {
                        Text(
                            "URGENTE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.background(Prisma.colors.statusCritical, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(m.texto, style = MaterialTheme.typography.bodySmall)
                if (m.fuenteNombre != null && m.fuenteUrl != null) {
                    SourceLink(m.fuenteNombre!!, m.fuenteUrl!!) { runCatching { uriHandler.openUri(m.fuenteUrl!!) } }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Tarjetas y próximos pagos
// ---------------------------------------------------------------------------------------------

@Composable
private fun CardsPanel(cards: List<HomeCardDto>) {
    Panel("Tus tarjetas") {
        if (cards.isEmpty()) {
            Text("Agrega tus tarjetas en la sección Tarjetas.", style = MaterialTheme.typography.bodyMedium)
        }
        cards.forEach { card ->
            Row(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        card.banco + (card.ultimosCuatro?.let { " •••• $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        card.deuda?.let { "Debes ${formatMxn(it)}" } ?: "Sin estado de cuenta analizado",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                PaymentChip(card)
            }
        }
    }
}

@Composable
private fun PaymentChip(card: HomeCardDto) {
    val days = card.diasParaPago
    val (text, color) = when {
        days == null -> "Sin fecha de pago" to Prisma.colors.onSurface.copy(alpha = 0.55f)
        days == 0 -> "Pagar hoy" to Prisma.colors.statusCritical
        days <= 3 -> "Pagar en $days d" to Prisma.colors.statusCritical
        days <= 7 -> "Pagar en $days d" to PrismaPalette.NaranjaAmbar
        else -> "Pago ${card.proximoPago?.let(::dayMonth) ?: "en $days d"}" to Prisma.colors.statusHealthy
    }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

// ---------------------------------------------------------------------------------------------
// Gastos recientes y factores de salud
// ---------------------------------------------------------------------------------------------

@Composable
private fun SpendingPanel(s: HomeSummaryDto, onOpenAnalysis: () -> Unit) {
    if (s.mesesGasto.isEmpty()) return
    Panel("Tus gastos de los últimos meses") {
        MonthlySpendChart(
            months = s.mesesGasto.map { MonthBar(it.periodo, it.total.toAmount()) },
            projection = null,
            average = null,
            chartHeight = 130.dp,
        )
        TextButton(onClick = onOpenAnalysis) { Text("Ver análisis completo") }
    }
}

@Composable
private fun HealthFactorsPanel(health: FinancialHealthDto) {
    if (health.factores.isEmpty()) return
    Panel("¿Qué influye en tu salud financiera?") {
        health.factores.sortedBy { it.puntaje }.forEach { f ->
            val color = when {
                f.puntaje >= 70 -> Prisma.colors.statusHealthy
                f.puntaje >= 45 -> PrismaPalette.NaranjaPrisma
                else -> Prisma.colors.statusCritical
            }
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text(f.nombre, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(
                        when {
                            f.puntaje >= 70 -> "Bien"
                            f.puntaje >= 45 -> "Mejorable"
                            else -> "Atención"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Prisma.colors.onSurface.copy(alpha = 0.08f))) {
                    Box(Modifier.fillMaxWidth((f.puntaje / 100f).coerceAtLeast(0.03f)).fillMaxHeight().background(color, RoundedCornerShape(50)))
                }
                Text(f.detalle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Carga y error
// ---------------------------------------------------------------------------------------------

/** Esqueleto de Inicio con la misma estructura: saludo, tarjeta de bienvenida y mensajes. */
@Composable
fun DashboardSkeleton(modifier: Modifier = Modifier) {
    val type = MaterialTheme.typography
    Column(
        modifier.screenContainer().loadingSemantics("Cargando tu resumen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SkeletonTextFraction(type.headlineSmall, 0.45f)
        SoftCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                SkeletonCircle(120.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SkeletonTextFraction(type.titleMedium, 0.9f)
                    SkeletonTextFraction(type.titleMedium, 0.7f)
                    SkeletonTextFraction(type.bodySmall, 0.8f)
                }
            }
        }
        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(0.5f, 0.95f, 0.85f, 0.9f).forEach { SkeletonTextFraction(type.bodyMedium, it) }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Box(Modifier.screenContainer(), contentAlignment = Alignment.Center) {
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
