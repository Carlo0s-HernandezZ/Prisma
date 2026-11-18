package com.example.prisma.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.prisma.analysis.AnalysisUiState
import com.example.prisma.analysis.AnalysisViewModel
import com.example.prisma.api.AdviceDto
import com.example.prisma.api.AlertSeverity
import com.example.prisma.api.AnalysisSource
import com.example.prisma.api.CardDto
import com.example.prisma.api.InstallmentPurchaseDto
import com.example.prisma.api.SpendingTrend
import com.example.prisma.api.StatementAnalysisDto
import com.example.prisma.budget.BudgetViewModel
import com.example.prisma.api.ReminderKind
import com.example.prisma.payroll.RemindersViewModel
import com.example.prisma.components.SoftCard
import com.example.prisma.components.bottomNavBarSpace
import com.example.prisma.ui.charts.HBar
import com.example.prisma.ui.charts.HorizontalBars
import com.example.prisma.ui.charts.MonthBar
import com.example.prisma.ui.charts.MonthlySpendChart
import com.example.prisma.ui.charts.ProgressLine
import com.example.prisma.ui.charts.SectionLabel
import com.example.prisma.ui.charts.StackedBar
import com.example.prisma.ui.charts.compactMxn
import com.example.prisma.ui.charts.dayMonth
import com.example.prisma.ui.charts.monthLong
import com.example.prisma.ui.charts.toAmount

// =============================================================================================
// Pantalla Análisis: Análisis IA · Mis gastos · Planes de pago
// =============================================================================================

private val ANALYSIS_TABS = listOf("Análisis IA", "Mis gastos", "Planes de pago")

@Composable
fun AnalysisHubScreen(
    viewModel: AnalysisViewModel,
    reminders: RemindersViewModel,
    budgetViewModel: BudgetViewModel,
    token: String,
    onSessionExpired: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val desktop = isDesktopLayout

    // El área segura superior se aplica una sola vez aquí: así ni el encabezado ni las pestañas
    // de abajo (que usan screenContainer) la vuelven a sumar.
    Column(
        Modifier
            .fillMaxSize()
            .background(Prisma.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = if (desktop) DESKTOP_CONTENT_MAX_WIDTH else COMPACT_CONTENT_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(start = if (desktop) 40.dp else 16.dp, end = if (desktop) 40.dp else 16.dp, top = if (desktop) 32.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Análisis",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                SegmentedTabs(ANALYSIS_TABS, tab, onSelect = { tab = it })
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            when (tab) {
                0 -> AiAnalysisTab(viewModel, reminders)
                1 -> SpendingDashboardTab(viewModel)
                // Mismo ancho máximo que el encabezado, para que quede alineada con las otras pestañas.
                else -> Box(
                    Modifier
                        .widthIn(max = if (desktop) DESKTOP_CONTENT_MAX_WIDTH else COMPACT_CONTENT_MAX_WIDTH)
                        .fillMaxSize(),
                ) {
                    CardPlansScreen(
                        viewModel = budgetViewModel,
                        token = token,
                        onSessionExpired = onSessionExpired,
                        showTitle = false,
                    )
                }
            }
        }
    }
}

/** Pestaña "Resumen" de la barra de navegación: el mismo dashboard de gastos. */
@Composable
fun SpendingSummaryScreen(viewModel: AnalysisViewModel) {
    Box(Modifier.fillMaxSize().background(Prisma.colors.background), contentAlignment = Alignment.TopCenter) {
        SpendingDashboardTab(viewModel, title = "Resumen de gastos")
    }
}

@Composable
private fun SegmentedTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Prisma.colors.onSurface.copy(alpha = 0.06f), RoundedCornerShape(50))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .background(if (isSelected) PrismaPalette.Dorado else Color.Transparent, RoundedCornerShape(50))
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .semantics { this.selected = isSelected }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) PrismaPalette.Grafito else Prisma.colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CardChips(
    cards: List<CardDto>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    allLabel: String = "Todas",
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(allLabel, selectedId == null) { onSelect(null) }
        cards.forEach { card ->
            Chip(cardLabel(card.banco, card.ultimosCuatro), selectedId == card.id) { onSelect(card.id) }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(
                if (selected) PrismaPalette.NaranjaPrisma.copy(alpha = 0.18f) else Prisma.colors.surface,
                RoundedCornerShape(50),
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) PrismaPalette.NaranjaAmbar else Prisma.colors.onSurface,
        )
    }
}

private fun cardLabel(bank: String, last4: String?) = bank + (last4?.let { " •••• $it" } ?: "")

// =============================================================================================
// Análisis IA
// =============================================================================================

@Composable
private fun AiAnalysisTab(viewModel: AnalysisViewModel, reminders: RemindersViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberStatementPicker(onFilesSelected = viewModel::upload, onError = viewModel::fileError)
    var confirmDelete by remember { mutableStateOf<StatementAnalysisDto?>(null) }

    Column(
        Modifier.screenContainer().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IntroCard(state.aiAvailable)

        if (!state.isLoading && state.cards.isEmpty()) {
            InfoPanel("Registra una tarjeta en la sección Tarjetas para analizar sus estados de cuenta.")
        } else {
            SectionLabel("Elige la tarjeta")
            CardChips(state.cards, state.selectedCardId, viewModel::selectCard)

            val card = state.selectedCard
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = picker.launchPdfPicker,
                    enabled = card != null && !state.uploading,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = PrismaPalette.Dorado, contentColor = PrismaPalette.Grafito),
                ) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Subir PDF", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = picker.launchImagePicker,
                    enabled = card != null && !state.uploading,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(50),
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Subir fotos")
                }
            }
            Text(
                if (card == null) "Elige una tarjeta para subir su estado de cuenta." else "Se analizará como estado de ${cardLabel(card.banco, card.ultimosCuatro)}.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (state.uploading) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = PrismaPalette.NaranjaPrisma)
                Text("Analizando tu estado de cuenta… puede tardar hasta un minuto.", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = PrismaPalette.NaranjaPrisma)
        state.error?.let { Text(it, color = Prisma.colors.statusCritical) }
        state.message?.let { Text(it, color = Prisma.colors.statusHealthy) }

        if (state.selectedCardId == null && state.latestPerCard.isNotEmpty()) {
            AllCardsOverview(state)
        }

        val visible = state.visible
        if (!state.isLoading && visible.isEmpty() && state.cards.isNotEmpty()) {
            InfoPanel("Aún no hay análisis${if (state.selectedCardId != null) " de esta tarjeta" else ""}. Sube el PDF de tu estado de cuenta para empezar.")
        }
        if (visible.isNotEmpty()) {
            SectionLabel("Estados analizados")
            visible.forEach { analysis ->
                AnalysisPanel(
                    analysis = analysis,
                    expanded = state.expandedId == analysis.id,
                    onToggle = { viewModel.toggle(analysis.id) },
                    onDelete = { confirmDelete = analysis },
                )
            }
        }
        RemindersPanel(reminders, ReminderKind.ESTADO_CUENTA)
        OfficialSourcesNote()
        Spacer(Modifier.height(bottomNavBarSpace))
    }

    confirmDelete?.let { analysis ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("¿Eliminar este análisis?") },
            text = { Text("Se borrará el análisis de ${cardLabel(analysis.banco, analysis.ultimosCuatro)} de ${monthLong(analysis.periodo)} y sus gastos de los dashboards.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(analysis.id); confirmDelete = null }) {
                    Text("Eliminar", color = Prisma.colors.statusCritical)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun IntroCard(aiAvailable: Boolean) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = PrismaPalette.NaranjaPrisma)
                Text("Entiende tu estado de cuenta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Sube el estado de cada tarjeta. Te decimos cuánto debes, tus compras a meses, tus gastos hormiga " +
                    "y los cargos que conviene revisar.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = Prisma.colors.statusHealthy, modifier = Modifier.size(18.dp))
                Text(
                    if (aiAvailable) {
                        "Antes de enviarlo a la IA (Gemini) quitamos tu nombre, números de tarjeta, RFC y domicilio."
                    } else {
                        "Modo lector local: el análisis se hace dentro de Prisma, sin enviar tu documento a la IA."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun InfoPanel(text: String) {
    SoftCard(Modifier.fillMaxWidth(), elevation = 4.dp) {
        Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Resumen general: el estado más reciente de cada tarjeta. */
@Composable
private fun AllCardsOverview(state: AnalysisUiState) {
    val latest = state.latestPerCard
    val debt = latest.sumOf { it.deudaTotal.toAmount() }
    val plans = latest.flatMap { it.comprasMeses }
    val msi = plans.count { !it.conIntereses }
    val mci = plans.count { it.conIntereses }
    val ants = latest.sumOf { it.gastosHormiga.total.toAmount() }
    val alerts = latest.sumOf { a -> a.alertas.count { it.severidad != AlertSeverity.BAJA } }

    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("Todas tus tarjetas")
            Text(
                "Debes ${formatMxn(debt.toPlain())} en ${latest.size} tarjeta${if (latest.size == 1) "" else "s"}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            KpiGrid(
                listOf(
                    Kpi("Compras a meses", "$msi sin intereses", "$mci con intereses"),
                    Kpi("Gastos hormiga", compactMxn(ants), "en tus últimos estados"),
                    Kpi("Cargos por revisar", alerts.toString(), if (alerts == 0) "todo en orden" else "revísalos abajo",
                        if (alerts > 0) Prisma.colors.statusCritical else null),
                    Kpi("Pagar sin intereses", compactMxn(latest.sumOf { it.pagoSinIntereses?.toAmount() ?: 0.0 }), "suma de tus tarjetas"),
                ),
            )
            if (latest.size > 1) {
                HorizontalBars(latest.map { HBar(cardLabel(it.banco, it.ultimosCuatro), it.deudaTotal.toAmount(), "Estado de ${monthLong(it.periodo)}") })
            }
        }
    }
}

private fun Double.toPlain(): String {
    val cents = kotlin.math.round(this * 100).toLong()
    val sign = if (cents < 0) "-" else ""
    val abs = kotlin.math.abs(cents)
    return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
}

private data class Kpi(val title: String, val value: String, val caption: String? = null, val color: Color? = null)

@Composable
private fun KpiGrid(items: List<Kpi>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { kpi -> KpiTile(kpi, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun KpiTile(kpi: Kpi, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Prisma.colors.onSurface.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .padding(12.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(kpi.title, style = MaterialTheme.typography.labelMedium, color = Prisma.colors.onSurface.copy(alpha = 0.75f))
        Text(kpi.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = kpi.color ?: Prisma.colors.onSurface)
        kpi.caption?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Prisma.colors.onSurface.copy(alpha = 0.7f)) }
    }
}

@Composable
private fun AnalysisPanel(
    analysis: StatementAnalysisDto,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = if (expanded) "Ocultar análisis" else "Ver análisis", onClick = onToggle)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(cardLabel(analysis.banco, analysis.ultimosCuatro), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Estado de ${monthLong(analysis.periodo)} · Debes ${formatMxn(analysis.deudaTotal)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    SourceBadge(analysis.fuente)
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                )
            }
            AnimatedVisibility(expanded) {
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    AnalysisBody(analysis)
                    TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.End)) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = Prisma.colors.statusCritical, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Eliminar análisis", color = Prisma.colors.statusCritical)
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceBadge(source: AnalysisSource) {
    val (text, color) = when (source) {
        AnalysisSource.GEMINI -> "Analizado con IA (Gemini)" to PrismaPalette.NaranjaAmbar
        AnalysisSource.LOCAL -> "Lector local de Prisma" to Prisma.colors.onSurface.copy(alpha = 0.7f)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
        if (source == AnalysisSource.GEMINI) Icon(Icons.Filled.AutoAwesome, null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun ColumnScope.AnalysisBody(a: StatementAnalysisDto) {
    Text(a.resumen, style = MaterialTheme.typography.bodyMedium)
    a.aviso?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = Prisma.colors.onSurface.copy(alpha = 0.7f)) }

    KpiGrid(
        listOfNotNull(
            Kpi("Deuda total", formatMxn(a.deudaTotal)),
            a.pagoSinIntereses?.let { Kpi("Para no pagar intereses", formatMxn(it), a.fechaLimitePago?.let { d -> "antes del ${dayMonth(d)}" }) },
            a.pagoMinimo?.let { Kpi("Pago mínimo", formatMxn(it), "pagar solo esto genera intereses") },
            Kpi("Gastaste en el periodo", formatMxn(a.totalGastos), "${a.movimientosLeidos} movimientos leídos"),
        ),
    )

    if (a.deudas.isNotEmpty()) {
        Block("¿De qué se compone tu deuda?") {
            StackedBar(
                a.deudas.filter { it.tipo != "A_FAVOR" }.map { d ->
                    HBar(
                        d.concepto,
                        d.monto.toAmount(),
                        when (d.tipo) {
                            "MSI" -> "Meses sin intereses"
                            "CON_INTERESES" -> "A meses con intereses"
                            else -> "Genera intereses si no lo pagas completo"
                        },
                    )
                },
            )
            a.deudas.firstOrNull { it.tipo == "A_FAVOR" }?.let {
                Text(
                    "Tienes ${formatMxn(it.monto)} a tu favor; ya está descontado de tu deuda total.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Prisma.colors.statusHealthy,
                )
            }
        }
    }

    if (a.comprasMeses.isNotEmpty()) {
        Block("Compras a meses") {
            val free = a.comprasMeses.filter { !it.conIntereses }
            val paid = a.comprasMeses.filter { it.conIntereses }
            if (free.isNotEmpty()) InstallmentGroup("Sin intereses (${free.size})", free, Prisma.colors.statusHealthy)
            if (paid.isNotEmpty()) InstallmentGroup("Con intereses (${paid.size})", paid, Prisma.colors.statusCritical)
            val monthly = a.comprasMeses.sumOf { it.mensualidad?.toAmount() ?: 0.0 }
            if (monthly > 0) {
                Text("Pagas ${formatMxn(monthly.toPlain())} al mes en mensualidades.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            }
        }
    }

    Block("Gastos hormiga") {
        val ants = a.gastosHormiga
        if (ants.compras == 0) {
            Text("No encontramos gastos hormiga en este estado. ¡Bien!", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                "${ants.compras} compras pequeñas suman ${formatMxn(ants.total)}. A este ritmo serían ${formatMxn(ants.proyeccionAnual)} al año.",
                style = MaterialTheme.typography.bodyMedium,
            )
            HorizontalBars(
                ants.comercios.map { HBar(it.nombre, it.total.toAmount(), "${it.veces} ${if (it.veces == 1) "vez" else "veces"}") },
                barColor = PrismaPalette.Ambar,
            )
        }
    }

    Block("Cargos para revisar") {
        if (a.alertas.isEmpty()) {
            Text("No vimos cargos extraños.", style = MaterialTheme.typography.bodyMedium, color = Prisma.colors.statusHealthy)
        } else {
            a.alertas.forEach { alert -> AlertRow(alert.descripcion, alert.monto, alert.fecha, alert.motivo, alert.severidad) }
            Text(
                "Si no reconoces un cargo, llama a tu banco y pide la aclaración por escrito. Si no te resuelven, acude a CONDUSEF.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (a.categorias.isNotEmpty()) {
        Block("¿En qué gastaste?") {
            HorizontalBars(a.categorias.take(8).map { HBar(it.categoria, it.monto.toAmount()) })
        }
    }

    if (a.consejos.isNotEmpty()) {
        Block("Consejos para tu salud financiera") {
            a.consejos.forEach { AdviceRow(it) }
        }
    }
}

@Composable
private fun Block(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(color = Prisma.colors.onSurface.copy(alpha = 0.08f))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun InstallmentGroup(title: String, items: List<InstallmentPurchaseDto>, color: Color) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.SemiBold)
    items.forEach { plan ->
        Column(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(plan.descripcion, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Debes ${formatMxn(plan.saldoPendiente)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
            val done = plan.pagoActual
            val total = plan.pagosTotales
            if (done != null && total != null && total > 0) ProgressLine(done.toFloat() / total, color)
            Text(
                buildString {
                    if (done != null && total != null) append("Pago $done de $total")
                    plan.mensualidad?.let { append(if (isEmpty()) "" else " · "); append("${formatMxn(it)} al mes") }
                    plan.tasaAnual?.takeIf { plan.conIntereses }?.let { append(" · tasa $it% anual") }
                },
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun AlertRow(description: String, amount: String, date: String?, motive: String, severity: AlertSeverity) {
    val color = when (severity) {
        AlertSeverity.ALTA -> Prisma.colors.statusCritical
        AlertSeverity.MEDIA -> PrismaPalette.NaranjaAmbar
        AlertSeverity.BAJA -> Prisma.colors.onSurface.copy(alpha = 0.6f)
    }
    Row(
        Modifier.fillMaxWidth()
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(10.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = "Importancia ${severity.name.lowercase()}", tint = color, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatMxn(amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Text((date?.let { dayMonth(it) + " · " } ?: "") + motive, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun AdviceRow(advice: AdviceDto) {
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(advice.titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(advice.texto, style = MaterialTheme.typography.bodySmall)
        SourceLink(advice.fuenteNombre, advice.fuenteUrl) { runCatching { uriHandler.openUri(advice.fuenteUrl) } }
    }
}

@Composable
internal fun SourceLink(name: String, url: String, onOpen: () -> Unit) {
    Row(
        Modifier
            .clickable(role = Role.Button, onClickLabel = "Abrir $name") { onOpen() }
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Fuente oficial: $name. Abre $url" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Fuente oficial: $name", style = MaterialTheme.typography.labelMedium, color = PrismaPalette.NaranjaAmbar, fontWeight = FontWeight.Medium)
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = PrismaPalette.NaranjaAmbar, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun OfficialSourcesNote() {
    Text(
        "Prisma no recomienda inversiones. Los consejos se basan en fuentes oficiales del Gobierno de México " +
            "(CONDUSEF, Banco de México, PROFECO, CNBV y Buró de Entidades Financieras).",
        style = MaterialTheme.typography.labelSmall,
        color = Prisma.colors.onSurface.copy(alpha = 0.7f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

// =============================================================================================
// Mis gastos: dashboards mes con mes (por tarjeta y general)
// =============================================================================================

@Composable
private fun SpendingDashboardTab(viewModel: AnalysisViewModel, title: String? = null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dashboard = state.dashboard

    Column(
        Modifier.screenContainer().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        title?.let {
            Text(it, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
        }
        if (state.cards.isNotEmpty()) {
            CardChips(state.cards, state.dashboardCardId, viewModel::loadDashboard, allLabel = "Todas las tarjetas")
        }
        if (state.dashboardLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = PrismaPalette.NaranjaPrisma)
        state.dashboardError?.let {
            Text(it, color = Prisma.colors.statusCritical)
            TextButton(onClick = { viewModel.loadDashboard(state.dashboardCardId) }) { Text("Reintentar") }
        }

        if (dashboard != null) {
            TrendCard(dashboard.tendencia, dashboard.mensaje)

            if (dashboard.meses.isNotEmpty()) {
                val last = dashboard.meses.last()
                KpiGrid(
                    listOfNotNull(
                        Kpi("Último mes", compactMxn(last.total.toAmount()), monthLong(last.periodo)),
                        Kpi("Promedio mensual", compactMxn(dashboard.promedio.toAmount()), "${dashboard.meses.size} mes${if (dashboard.meses.size == 1) "" else "es"} con datos"),
                        dashboard.cambioPorcentaje?.let { change ->
                            Kpi(
                                "Vs. mes anterior",
                                (if (change > 0) "+" else "") + "$change%",
                                if (change > 0) "gastaste más" else if (change < 0) "gastaste menos" else "igual",
                                if (change > 5) Prisma.colors.statusCritical else if (change < -5) Prisma.colors.statusHealthy else null,
                            )
                        },
                        dashboard.proyeccion?.let { Kpi("Proyección", compactMxn(it.total.toAmount()), monthLong(it.periodo)) },
                    ),
                )

                DashboardPanel("Gasto mes con mes") {
                    MonthlySpendChart(
                        months = dashboard.meses.map { MonthBar(it.periodo, it.total.toAmount()) },
                        projection = dashboard.proyeccion?.let { MonthBar(it.periodo, it.total.toAmount()) },
                        average = dashboard.promedio.toAmount().takeIf { dashboard.meses.size > 1 },
                    )
                    dashboard.mesMayorGasto?.let {
                        Text(
                            "Gastaste más en ${monthLong(it)}" + (dashboard.mesMenorGasto?.let { low -> " y menos en ${monthLong(low)}." } ?: "."),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                val ants = dashboard.meses.filter { it.hormiga.toAmount() > 0 }
                if (ants.isNotEmpty()) {
                    DashboardPanel("Gastos hormiga por mes") {
                        MonthlySpendChart(
                            months = dashboard.meses.map { MonthBar(it.periodo, it.hormiga.toAmount()) },
                            projection = null,
                            average = null,
                            chartHeight = 120.dp,
                        )
                        Text("Cafés, antojos, tiendas de conveniencia y suscripciones pequeñas.", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (dashboard.categorias.isNotEmpty()) {
                    DashboardPanel("¿En qué gastaste en ${monthLong(last.periodo)}?") {
                        HorizontalBars(dashboard.categorias.take(8).map { HBar(it.categoria, it.monto.toAmount()) })
                    }
                }
            }

            if (dashboard.porTarjeta.isNotEmpty()) {
                DashboardPanel("Gasto por tarjeta (último estado)") {
                    HorizontalBars(
                        dashboard.porTarjeta.map { spend ->
                            // Mismo nombre que en los chips: el banco y sus últimos 4 dígitos.
                            val bank = state.cards.firstOrNull { it.id == spend.tarjetaId }?.banco ?: spend.nombre
                            HBar(cardLabel(bank, spend.ultimosCuatro), spend.gastoUltimoMes.toAmount(), "Deuda: ${formatMxn(spend.deuda)}")
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(bottomNavBarSpace))
    }
}

@Composable
private fun TrendCard(trend: SpendingTrend, message: String) {
    val (icon, color) = when (trend) {
        SpendingTrend.SUBE -> Icons.AutoMirrored.Filled.TrendingUp to Prisma.colors.statusCritical
        SpendingTrend.BAJA -> Icons.AutoMirrored.Filled.TrendingDown to Prisma.colors.statusHealthy
        else -> Icons.AutoMirrored.Filled.TrendingFlat to PrismaPalette.NaranjaPrisma
    }
    SoftCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(color.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color)
            }
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DashboardPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}
