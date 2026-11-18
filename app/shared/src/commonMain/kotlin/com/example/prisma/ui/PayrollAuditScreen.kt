package com.example.prisma.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.prisma.api.AlertSeverity
import com.example.prisma.api.PayrollAuditDto
import com.example.prisma.api.PayrollConceptDto
import com.example.prisma.api.PayrollConceptKind
import com.example.prisma.api.ReminderKind
import com.example.prisma.components.SoftCard
import com.example.prisma.components.bottomNavBarSpace
import com.example.prisma.payroll.PayrollAuditViewModel
import com.example.prisma.payroll.RemindersViewModel
import com.example.prisma.ui.charts.HBar
import com.example.prisma.ui.charts.SectionLabel
import com.example.prisma.ui.charts.StackedBar
import com.example.prisma.ui.charts.dayMonth
import com.example.prisma.ui.charts.monthLong
import com.example.prisma.ui.charts.toAmount

/** Auditoría de nómina: desglose explicado de cada concepto, irregularidades y recordatorios. */
@Composable
fun PayrollAuditScreen(
    viewModel: PayrollAuditViewModel,
    reminders: RemindersViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberFilePicker { name, _, bytes ->
        if (bytes.size > 10 * 1024 * 1024) viewModel.fileError("El archivo puede pesar hasta 10 MB.") else viewModel.upload(name, bytes)
    }

    Box(Modifier.fillMaxSize().background(Prisma.colors.background), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.screenContainer().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                Text("Auditoría de nómina", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() })
            }

            SoftCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Entiende tu recibo de nómina", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Sube el PDF de tu recibo o el XML (CFDI) de tu nómina. Te explicamos cada pago y cada descuento, " +
                            "y te avisamos si algo no cuadra.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Shield, null, tint = Prisma.colors.statusHealthy, modifier = Modifier.size(18.dp))
                        Text("El XML es más preciso: trae las claves oficiales del SAT. Tu recibo se guarda cifrado.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = picker.launchDocumentPicker,
                        enabled = !state.uploading,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = PrismaPalette.Dorado, contentColor = PrismaPalette.Grafito),
                    ) {
                        Icon(Icons.Filled.Description, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Subir PDF o XML", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (state.uploading) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = PrismaPalette.NaranjaPrisma)
                Text("Revisando tu nómina…", style = MaterialTheme.typography.bodySmall)
            }
            if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = PrismaPalette.NaranjaPrisma)
            state.error?.let { Text(it, color = Prisma.colors.statusCritical) }
            state.message?.let { Text(it, color = Prisma.colors.statusHealthy) }

            state.selected?.let { AuditDetail(it) }

            if (state.audits.size > 1 || (state.audits.size == 1 && state.selected?.id != state.audits.first().id)) {
                SectionLabel("Nóminas revisadas")
                state.audits.forEach { audit -> HistoryRow(audit, audit.id == state.selected?.id, { viewModel.select(audit.id) }, { viewModel.delete(audit.id) }) }
            } else if (state.audits.size == 1) {
                val only = state.audits.first()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    androidx.compose.material3.TextButton(onClick = { viewModel.delete(only.id) }) {
                        Icon(Icons.Filled.Delete, null, tint = Prisma.colors.statusCritical, modifier = Modifier.size(18.dp))
                        Text(" Eliminar esta auditoría", color = Prisma.colors.statusCritical)
                    }
                }
            }

            RemindersPanel(reminders, ReminderKind.NOMINA)
            Spacer(Modifier.height(bottomNavBarSpace))
        }
    }
}

@Composable
private fun AuditDetail(a: PayrollAuditDto) {
    val earned = a.totalPercepciones.toAmount() + a.totalOtrosPagos.toAmount()
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (a.periodo.isNotBlank()) "Nómina de ${monthLong(a.periodo)}" else "Tu nómina",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    listOfNotNull(
                        a.fechaInicial?.let { start -> a.fechaFinal?.let { end -> "Del ${dayMonth(start)} al ${dayMonth(end)}" } },
                        a.periodicidad,
                        a.fechaPago?.let { "pagada el ${dayMonth(it)}" },
                        if (a.fuente == "XML") "CFDI (XML)" else "Recibo PDF",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(a.resumen, style = MaterialTheme.typography.bodyMedium)
            a.aviso?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = Prisma.colors.onSurface.copy(alpha = 0.7f)) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile("Ganaste", formatMxn(earned.toPlainMoney()), Prisma.colors.statusHealthy, Modifier.weight(1f))
                Tile("Descuentos", formatMxn(a.totalDeducciones), Prisma.colors.statusCritical, Modifier.weight(1f))
                Tile("Recibes", formatMxn(a.netoCalculado), null, Modifier.weight(1f))
            }

            // ¿A dónde se va tu sueldo? Cada descuento y lo que te queda, en una sola barra.
            if (earned > 0) {
                Section("¿A dónde se va lo que ganas?") {
                    val parts = a.deducciones.sortedByDescending { it.importe.toAmount() }.take(5)
                        .map { HBar(it.nombre.substringBefore(" (").take(40), it.importe.toAmount()) } +
                        listOfNotNull(
                            a.deducciones.drop(5).sumOf { it.importe.toAmount() }.takeIf { it > 0 }?.let { HBar("Otros descuentos", it) },
                            HBar("Lo que recibes", a.netoCalculado.toAmount(), color = Prisma.colors.statusHealthy),
                        )
                    StackedBar(parts)
                }
            }

            Section(if (a.alertas.isEmpty()) "Todo en orden" else "Puntos por revisar") {
                if (a.alertas.isEmpty()) {
                    Text("No encontramos irregularidades en esta nómina.", color = Prisma.colors.statusHealthy, style = MaterialTheme.typography.bodyMedium)
                }
                a.alertas.forEach { alert ->
                    val color = when (alert.severidad) {
                        AlertSeverity.ALTA -> Prisma.colors.statusCritical
                        AlertSeverity.MEDIA -> PrismaPalette.NaranjaAmbar
                        AlertSeverity.BAJA -> Prisma.colors.onSurface.copy(alpha = 0.6f)
                    }
                    Row(
                        Modifier.fillMaxWidth().background(color.copy(alpha = 0.08f), RoundedCornerShape(12.dp)).padding(10.dp)
                            .semantics(mergeDescendants = true) {},
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = "Importancia ${alert.severidad.name.lowercase()}", tint = color, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text(alert.titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(alert.detalle, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            ConceptSection("Lo que te pagan (percepciones)", a.percepciones)
            if (a.otrosPagos.any { it.importe.toAmount() > 0 }) ConceptSection("Otros pagos", a.otrosPagos.filter { it.importe.toAmount() > 0 })
            ConceptSection("Lo que te descuentan (deducciones)", a.deducciones)

            if (a.consejos.isNotEmpty()) {
                Section("Consejos") { a.consejos.forEach { AdviceRow(it) } }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(color = Prisma.colors.onSurface.copy(alpha = 0.08f))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun ConceptSection(title: String, concepts: List<PayrollConceptDto>) {
    if (concepts.isEmpty()) return
    Section(title) {
        Text("Toca un concepto para ver qué es.", style = MaterialTheme.typography.labelSmall, color = Prisma.colors.onSurface.copy(alpha = 0.7f))
        concepts.forEach { ConceptRow(it) }
    }
}

@Composable
private fun ConceptRow(c: PayrollConceptDto) {
    var open by remember { mutableStateOf(false) }
    val deduction = c.tipo == PayrollConceptKind.DEDUCCION
    val color = if (deduction) Prisma.colors.statusCritical else Prisma.colors.statusHealthy
    Column(
        Modifier.fillMaxWidth()
            .background(Prisma.colors.onSurface.copy(alpha = if (c.revisar) 0.08f else 0.04f), RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = if (open) "Ocultar explicación" else "Ver explicación") { open = !open }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.concepto, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (c.nombre != c.concepto) Text(c.nombre, style = MaterialTheme.typography.labelSmall, color = Prisma.colors.onSurface.copy(alpha = 0.7f))
            }
            Text((if (deduction) "− " else "+ ") + formatMxn(c.importe), color = color, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        AnimatedVisibility(open) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(c.explicacion, style = MaterialTheme.typography.bodySmall)
                val extra = listOfNotNull(
                    c.claveSat?.let { "Clave SAT $it" },
                    c.gravado?.takeIf { it.toAmount() > 0 }?.let { "Gravado ${formatMxn(it)}" },
                    c.exento?.takeIf { it.toAmount() > 0 }?.let { "Exento de ISR ${formatMxn(it)}" },
                )
                if (extra.isNotEmpty()) Text(extra.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = Prisma.colors.onSurface.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun HistoryRow(a: PayrollAuditDto, selected: Boolean, onSelect: () -> Unit, onDelete: () -> Unit) {
    val alerts = a.alertas.count { it.severidad != AlertSeverity.BAJA }
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) PrismaPalette.NaranjaPrisma.copy(alpha = 0.12f) else Prisma.colors.surface, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onSelect)
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                (if (a.periodo.isNotBlank()) monthLong(a.periodo) else "Nómina") + (a.fechaPago?.let { " · ${dayMonth(it)}" } ?: ""),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Recibiste ${formatMxn(a.netoCalculado)} · " + if (alerts == 0) "sin irregularidades" else "$alerts por revisar",
                style = MaterialTheme.typography.bodySmall,
                color = if (alerts == 0) Prisma.colors.onSurface else Prisma.colors.statusCritical,
            )
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar auditoría", tint = Prisma.colors.statusCritical) }
    }
}

@Composable
private fun Tile(title: String, value: String, color: Color?, modifier: Modifier = Modifier) {
    Column(
        modifier.background(Prisma.colors.onSurface.copy(alpha = 0.05f), RoundedCornerShape(14.dp)).padding(10.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = Prisma.colors.onSurface.copy(alpha = 0.75f))
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = color ?: Prisma.colors.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun Double.toPlainMoney(): String {
    val cents = kotlin.math.round(this * 100).toLong()
    return "${cents / 100}.${(kotlin.math.abs(cents) % 100).toString().padStart(2, '0')}"
}
