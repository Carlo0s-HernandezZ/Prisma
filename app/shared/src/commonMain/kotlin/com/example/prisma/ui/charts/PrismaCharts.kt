package com.example.prisma.ui.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.prisma.ui.Prisma
import com.example.prisma.ui.PrismaPalette
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// ---------------------------------------------------------------------------------------------
// Formatos
// ---------------------------------------------------------------------------------------------

private val MONTHS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
private val MONTHS_LONG = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

/** "2026-10" -> "oct". */
fun monthShort(period: String): String =
    period.substringAfter('-').take(2).toIntOrNull()?.let { MONTHS.getOrNull(it - 1) } ?: period

/** "2026-10" -> "octubre 2026". */
fun monthLong(period: String): String {
    val month = period.substringAfter('-').take(2).toIntOrNull()?.let { MONTHS_LONG.getOrNull(it - 1) } ?: return period
    return "$month ${period.take(4)}"
}

/** "2026-10-28" -> "28 oct". */
fun dayMonth(isoDate: String): String {
    val parts = isoDate.split('-')
    if (parts.size < 3) return isoDate
    return "${parts[2].trimStart('0')} ${monthShort(parts[0] + "-" + parts[1])}"
}

/** Pesos compactos para gráficas: $850, $12.3 mil, $1.2 M. */
fun compactMxn(value: Double): String {
    val v = abs(value)
    val sign = if (value < 0) "-" else ""
    return sign + when {
        v >= 1_000_000 -> "\$" + oneDecimal(v / 1_000_000) + " M"
        v >= 1_000 -> "\$" + oneDecimal(v / 1_000) + " mil"
        else -> "\$" + v.roundToLong()
    }
}

private fun oneDecimal(v: Double): String {
    val tenths = (v * 10).roundToLong()
    return if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
}

fun String.toAmount(): Double = toDoubleOrNull() ?: 0.0

// ---------------------------------------------------------------------------------------------
// Etiqueta de sección (manual de marca: mayúsculas con tracking amplio)
// ---------------------------------------------------------------------------------------------

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Medium),
        color = Prisma.colors.onSurface.copy(alpha = 0.7f),
        modifier = modifier,
    )
}

/** Valor que crece de 0 al valor final la primera vez que se dibuja. */
@Composable
private fun animatedFraction(target: Float): Float {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val value by animateFloatAsState(if (started) target else 0f, tween(700), label = "chartGrow")
    return value
}

// ---------------------------------------------------------------------------------------------
// Barras de gasto por mes, con proyección y promedio
// ---------------------------------------------------------------------------------------------

data class MonthBar(val period: String, val value: Double)

/** Espacio sobre la barra más alta para su monto; la línea del promedio usa la misma escala. */
private val LABEL_SPACE = 18.dp

/**
 * Gasto mes con mes: una barra por mes. El mes con más gasto va en naranja profundo,
 * el de menos gasto en verde, la proyección en ámbar claro y una línea punteada marca el promedio.
 */
@Composable
fun MonthlySpendChart(
    months: List<MonthBar>,
    projection: MonthBar?,
    average: Double?,
    modifier: Modifier = Modifier,
    chartHeight: androidx.compose.ui.unit.Dp = 170.dp,
) {
    if (months.isEmpty()) return
    val colors = Prisma.colors
    val all = months + listOfNotNull(projection)
    val max = (all.maxOf { it.value }).coerceAtLeast(1.0)
    // Si todos los meses gastaron lo mismo no se resalta ninguno.
    val tied = months.all { it.value == months.first().value }
    val highest = months.maxBy { it.value }.period.takeIf { months.size > 1 && !tied }
    val lowest = months.minBy { it.value }.period.takeIf { months.size > 1 && !tied }
    val grow = animatedFraction(1f)

    val description = buildString {
        append("Gasto por mes: ")
        append(months.joinToString { "${monthLong(it.period)} ${compactMxn(it.value)}" })
        projection?.let { append(". Proyección para ${monthLong(it.period)}: ${compactMxn(it.value)}") }
        average?.let { append(". Promedio ${compactMxn(it)}") }
    }

    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .drawBehind {
                    // Línea punteada del promedio, en la misma escala que las barras:
                    // arriba quedan LABEL_SPACE dp para el monto y el resto es el alto máximo de barra.
                    if (average != null && average > 0) {
                        val top = LABEL_SPACE.toPx()
                        val usable = size.height - top
                        val y = top + usable * (1f - (average / max).toFloat())
                        drawLine(
                            color = colors.onSurface.copy(alpha = 0.45f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                        )
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            all.forEach { bar ->
                val isProjection = bar === projection
                val color = when {
                    isProjection -> PrismaPalette.Ambar.copy(alpha = 0.45f)
                    bar.period == highest -> PrismaPalette.NaranjaAmbar
                    bar.period == lowest -> colors.statusHealthy
                    else -> PrismaPalette.NaranjaPrisma.copy(alpha = 0.75f)
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        compactMxn(bar.value),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        color = colors.onSurface.copy(alpha = 0.8f),
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.78f)
                            .height((chartHeight - LABEL_SPACE) * ((bar.value / max).toFloat() * grow).coerceIn(0.02f, 1f))
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(color)
                            .then(
                                if (isProjection) {
                                    Modifier.drawBehind {
                                        drawRoundRect(
                                            color = PrismaPalette.NaranjaAmbar,
                                            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                                        )
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            all.forEach { bar ->
                Text(
                    if (bar === projection) "${monthShort(bar.period)}*" else monthShort(bar.period),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (bar.period == highest) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
        // Leyenda en palabras sencillas.
        FlowLegend(
            buildList {
                if (highest != null) {
                    add(PrismaPalette.NaranjaAmbar to "Mes con más gasto")
                    add(colors.statusHealthy to "Mes con menos gasto")
                }
                if (projection != null) add(PrismaPalette.Ambar.copy(alpha = 0.6f) to "* Proyección")
                if (average != null) add(colors.onSurface.copy(alpha = 0.45f) to "--- Promedio")
            },
        )
    }
}

@Composable
fun FlowLegend(items: List<Pair<Color, String>>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    // Dos columnas para que quepa en pantallas angostas.
    Column(modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { (color, label) ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(10.dp).background(color, CircleShape))
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Barras horizontales (categorías, comercios, tarjetas)
// ---------------------------------------------------------------------------------------------

data class HBar(val label: String, val value: Double, val detail: String? = null, val color: Color? = null)

/** Ranking fácil de leer: nombre, barra proporcional y monto. */
@Composable
fun HorizontalBars(
    items: List<HBar>,
    modifier: Modifier = Modifier,
    barColor: Color = PrismaPalette.NaranjaPrisma,
    format: (Double) -> String = ::compactMxn,
) {
    if (items.isEmpty()) return
    val max = items.maxOf { it.value }.coerceAtLeast(1.0)
    val total = items.sumOf { it.value }.coerceAtLeast(1.0)
    val grow = animatedFraction(1f)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { item ->
            val share = (item.value / total * 100).roundToInt()
            Column(
                Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "${item.label}: ${format(item.value)}, $share por ciento"
                },
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.label,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        format(item.value) + "  ·  $share%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50))
                        .background(Prisma.colors.onSurface.copy(alpha = 0.08f)),
                ) {
                    Box(
                        Modifier.fillMaxWidth(((item.value / max).toFloat() * grow).coerceIn(0.01f, 1f)).fillMaxHeight()
                            .clip(RoundedCornerShape(50)).background(item.color ?: barColor),
                    )
                }
                item.detail?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Prisma.colors.onSurface.copy(alpha = 0.7f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Barra apilada: de qué se compone la deuda
// ---------------------------------------------------------------------------------------------

/** Una sola barra dividida en partes de colores (más fácil de leer que una dona). */
@Composable
fun StackedBar(parts: List<HBar>, modifier: Modifier = Modifier) {
    val total = parts.sumOf { it.value }
    if (total <= 0) return
    val palette = listOf(
        PrismaPalette.NaranjaAmbar, PrismaPalette.NaranjaPrisma, PrismaPalette.Ambar,
        PrismaPalette.Dorado, Prisma.colors.onSurface.copy(alpha = 0.55f), Prisma.colors.onSurface.copy(alpha = 0.3f),
    )
    val colored = parts.mapIndexed { i, p -> p.copy(color = p.color ?: palette[i % palette.size]) }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(50))
                .clearAndSetSemantics {
                    contentDescription = colored.joinToString { "${it.label} ${compactMxn(it.value)}" }
                },
        ) {
            colored.forEach { p ->
                Box(Modifier.weight((p.value / total).toFloat().coerceAtLeast(0.01f)).fillMaxHeight().background(p.color!!))
            }
        }
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            colored.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).background(p.color!!, CircleShape))
                    Text(p.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 2)
                    Text(compactMxn(p.value), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
                p.detail?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 18.dp),
                        color = Prisma.colors.onSurface.copy(alpha = 0.7f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Progreso de una compra a meses
// ---------------------------------------------------------------------------------------------

@Composable
fun ProgressLine(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val grow = animatedFraction(fraction.coerceIn(0f, 1f))
    Box(
        modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50))
            .background(Prisma.colors.onSurface.copy(alpha = 0.08f)),
    ) {
        Box(Modifier.fillMaxWidth(grow.coerceAtLeast(0.02f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
    }
}

// ---------------------------------------------------------------------------------------------
// Medidor de salud financiera (semicírculo de 0 a 100)
// ---------------------------------------------------------------------------------------------

@Composable
fun HealthGauge(
    score: Int?,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val sweep = animatedFraction((score ?: 0) / 100f)
    val track = Prisma.colors.onSurface.copy(alpha = 0.1f)
    Box(
        modifier
            .width(180.dp)
            .aspectRatio(1.6f)
            .clearAndSetSemantics {
                contentDescription = if (score == null) "Salud financiera sin datos" else "Salud financiera: $score de 100, $label"
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1.6f)) {
            val stroke = 16.dp.toPx()
            val diameter = minOf(size.width, size.height * 2) - stroke
            val topLeft = Offset((size.width - diameter) / 2f, stroke / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(track, 180f, 180f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (score != null) {
                drawArc(color, 180f, 180f * sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 2.dp)) {
            Text(
                score?.toString() ?: "--",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (score == null) Prisma.colors.onSurface.copy(alpha = 0.5f) else color,
            )
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
    }
}
