package com.example.prisma.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.prisma.api.CardDto
import com.example.prisma.api.ReminderDto
import com.example.prisma.api.ReminderFrequency
import com.example.prisma.api.ReminderKind
import com.example.prisma.api.ReminderSchedule
import com.example.prisma.components.SoftCard
import com.example.prisma.getPlatform
import com.example.prisma.payroll.RemindersViewModel

private fun cardName(card: CardDto) = card.banco + (card.ultimosCuatro?.let { " •••• $it" } ?: "")

/** Recordatorios para subir la nómina ([ReminderKind.NOMINA]) o estados de cuenta ([ReminderKind.ESTADO_CUENTA]). */
@Composable
fun RemindersPanel(viewModel: RemindersViewModel, kind: ReminderKind, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ReminderDto?>(null) }
    val isWeb = remember { getPlatform().isWeb }
    val list = state.reminders.filter { it.tipo == kind }

    SoftCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Alarm, contentDescription = null, tint = PrismaPalette.NaranjaPrisma)
                Text("Recordatorios", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            }
            Text(
                (if (kind == ReminderKind.NOMINA) "Te avisamos cuando sea momento de subir tu nómina, en las fechas que elijas."
                else "Te avisamos cuando sea momento de subir el estado de cuenta de cada tarjeta.") +
                    if (isWeb) " En la web los verás en Inicio; en el teléfono llegan como notificación." else "",
                style = MaterialTheme.typography.bodySmall,
            )
            if (list.isEmpty() && !state.isLoading) {
                Text("Aún no tienes recordatorios.", style = MaterialTheme.typography.bodyMedium)
            }
            list.forEach { r ->
                Row(
                    Modifier.fillMaxWidth().background(Prisma.colors.onSurface.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button, onClickLabel = "Editar recordatorio") { editing = r }
                        .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(r.titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            ReminderSchedule.describe(r) + (r.tarjetaId?.let { id -> state.cards.firstOrNull { it.id == id }?.let { " · ${cardName(it)}" } } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = r.activo,
                        onCheckedChange = { viewModel.toggle(r) },
                        enabled = !state.saving,
                        colors = SwitchDefaults.colors(checkedTrackColor = PrismaPalette.NaranjaPrisma),
                        modifier = Modifier.semantics { contentDescription = if (r.activo) "Recordatorio activo" else "Recordatorio en pausa" },
                    )
                    IconButton(onClick = { viewModel.delete(r.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar recordatorio", tint = Prisma.colors.statusCritical)
                    }
                }
            }
            state.error?.let { Text(it, color = Prisma.colors.statusCritical, style = MaterialTheme.typography.bodySmall) }
            OutlinedButton(
                onClick = { editing = defaultReminder(kind, state.cards) },
                enabled = kind == ReminderKind.NOMINA || state.cards.isNotEmpty(),
                shape = RoundedCornerShape(50),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Agregar recordatorio")
            }
        }
    }

    editing?.let { draft ->
        ReminderEditor(
            initial = draft,
            cards = state.cards,
            saving = state.saving,
            onDismiss = { editing = null },
            onSave = { r -> viewModel.save(r) { editing = null } },
        )
    }
}

private fun defaultReminder(kind: ReminderKind, cards: List<CardDto>): ReminderDto =
    if (kind == ReminderKind.NOMINA) {
        ReminderDto(tipo = kind, titulo = "Subir mi nómina", frecuencia = ReminderFrequency.QUINCENAL, dia = 15, dia2 = 30, hora = "20:00")
    } else {
        val card = cards.firstOrNull()
        suggestionFor(card)
    }

/** Para estados de cuenta: un día después del corte (cuando el banco ya lo publicó). */
private fun suggestionFor(card: CardDto?): ReminderDto =
    ReminderDto(
        tipo = ReminderKind.ESTADO_CUENTA,
        tarjetaId = card?.id,
        titulo = card?.let { "Subir estado de ${it.banco}" } ?: "Subir estado de cuenta",
        frecuencia = ReminderFrequency.MENSUAL,
        dia = card?.diaCorte?.let { if (it >= 31) 1 else it + 1 } ?: 5,
        hora = "20:00",
    )

@Composable
private fun ReminderEditor(
    initial: ReminderDto,
    cards: List<CardDto>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (ReminderDto) -> Unit,
) {
    var r by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    val (hour, minute) = r.hora.split(':').mapNotNull(String::toIntOrNull).let { (it.getOrNull(0) ?: 9) to (it.getOrNull(1) ?: 0) }
    fun setTime(h: Int, m: Int) { r = r.copy(hora = "${h.mod(24).toString().padStart(2, '0')}:${m.mod(60).toString().padStart(2, '0')}") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            Modifier.widthIn(max = 520.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Prisma.colors.surface,
            contentColor = Prisma.colors.onSurface,
        ) {
            Column(
                Modifier.heightIn(max = 680.dp).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (r.tipo == ReminderKind.NOMINA) "Recordatorio de nómina" else "Recordatorio de estado de cuenta",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = r.titulo,
                    onValueChange = { r = r.copy(titulo = it.take(80)) },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (r.tipo == ReminderKind.ESTADO_CUENTA) {
                    Label("Tarjeta")
                    ChipRow(cards.map { cardName(it) to (it.id == r.tarjetaId) }) { i ->
                        val s = suggestionFor(cards[i])
                        r = r.copy(tarjetaId = s.tarjetaId, titulo = s.titulo, dia = if (r.frecuencia == ReminderFrequency.MENSUAL) s.dia else r.dia)
                    }
                    cards.firstOrNull { it.id == r.tarjetaId }?.diaCorte?.let {
                        Text("Tu tarjeta corta el día $it: el estado suele estar listo al día siguiente.", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Label("Frecuencia")
                val freqs = listOf(
                    ReminderFrequency.MENSUAL to "Mensual", ReminderFrequency.QUINCENAL to "Quincenal",
                    ReminderFrequency.SEMANAL to "Semanal", ReminderFrequency.CATORCENAL to "Catorcenal",
                )
                ChipRow(freqs.map { it.second to (it.first == r.frecuencia) }) { i ->
                    val f = freqs[i].first
                    r = when (f) {
                        ReminderFrequency.MENSUAL -> r.copy(frecuencia = f, dia = r.dia?.takeIf { it in 1..31 } ?: 5)
                        ReminderFrequency.QUINCENAL -> r.copy(frecuencia = f, dia = 15, dia2 = 30)
                        ReminderFrequency.SEMANAL -> r.copy(frecuencia = f, dia = 5)
                        ReminderFrequency.CATORCENAL -> r.copy(frecuencia = f, fechaInicio = r.fechaInicio ?: todayIsoDate())
                    }
                }

                when (r.frecuencia) {
                    ReminderFrequency.MENSUAL -> Stepper("Día del mes", r.dia ?: 1, 1..31) { r = r.copy(dia = it) }
                    ReminderFrequency.QUINCENAL -> {
                        Stepper("Primer día", r.dia ?: 15, 1..31) { r = r.copy(dia = it) }
                        Stepper("Segundo día", r.dia2 ?: 30, 1..31) { r = r.copy(dia2 = it) }
                    }
                    ReminderFrequency.SEMANAL -> {
                        Label("Día de la semana")
                        val days = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
                        ChipRow(days.mapIndexed { i, d -> d to (r.dia == i + 1) }) { i -> r = r.copy(dia = i + 1) }
                    }
                    ReminderFrequency.CATORCENAL -> PrismaDateField(
                        value = r.fechaInicio.orEmpty(),
                        onValueChange = { r = r.copy(fechaInicio = it) },
                        label = "Tu próximo día de pago",
                        allowFuture = true,
                        supportingText = "Te avisaremos ese día y luego cada 14 días.",
                    )
                }
                if (r.frecuencia == ReminderFrequency.MENSUAL || r.frecuencia == ReminderFrequency.QUINCENAL) {
                    Text("Si el mes no tiene ese día, te avisamos el último día del mes.", style = MaterialTheme.typography.labelSmall)
                }

                Label("Hora del aviso")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stepper("Hora", hour, 0..23, compact = true) { setTime(it, minute) }
                    Stepper("Min.", minute, 0..45, step = 15, compact = true) { setTime(hour, it) }
                }

                error?.let { Text(it, color = Prisma.colors.statusCritical, style = MaterialTheme.typography.bodySmall) }
                Button(
                    onClick = {
                        error = ReminderSchedule.validate(r)
                        if (error == null) onSave(r)
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = PrismaPalette.Dorado, contentColor = PrismaPalette.Grafito),
                ) { Text(if (saving) "Guardando…" else "Guardar recordatorio", fontWeight = FontWeight.SemiBold) }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Cancelar") }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
}

@Composable
private fun ChipRow(items: List<Pair<String, Boolean>>, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { i, (label, selected) ->
            Box(
                Modifier
                    .background(if (selected) PrismaPalette.NaranjaPrisma.copy(alpha = 0.18f) else Prisma.colors.onSurface.copy(alpha = 0.06f), RoundedCornerShape(50))
                    .clickable(role = Role.RadioButton) { onSelect(i) }
                    .semantics { this.selected = selected }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) PrismaPalette.NaranjaAmbar else Prisma.colors.onSurface,
                )
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, step: Int = 1, compact: Boolean = false, onChange: (Int) -> Unit) {
    Row(
        Modifier.background(Prisma.colors.onSurface.copy(alpha = 0.05f), RoundedCornerShape(14.dp)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!compact) Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 6.dp).widthIn(min = 110.dp))
        IconButton(onClick = { onChange(if (value - step < range.first) range.last else value - step) }) {
            Icon(Icons.Filled.Remove, contentDescription = "Menos $label")
        }
        Text(
            if (compact) value.toString().padStart(2, '0') else value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { contentDescription = "$label: $value" },
        )
        IconButton(onClick = { onChange(if (value + step > range.last) range.first else value + step) }) {
            Icon(Icons.Filled.Add, contentDescription = "Más $label")
        }
    }
}
