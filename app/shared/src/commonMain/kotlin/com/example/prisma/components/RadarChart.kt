package com.example.prisma.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.prisma.ui.Prisma
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

data class RadarSeries(val color: Color, val values: List<Float>)

/**
 * Gráfica de radar dibujada con Canvas (100% commonMain: Android, iOS y Web).
 * El primer eje queda arriba y los demás siguen el sentido de las manecillas.
 */
@Composable
fun RadarChart(
    axes: List<String>,
    series: List<RadarSeries>,
    modifier: Modifier = Modifier,
    maxValue: Float = 10f,
    levels: Int = 5,
) {
    val colors = Prisma.colors
    val measurer = rememberTextMeasurer()
    val base = MaterialTheme.typography.labelSmall
    val labelStyle = base.copy(fontSize = 10.sp, color = colors.onSurface)
    val tickStyle = base.copy(fontSize = 8.sp, color = colors.onSurface)

    Canvas(modifier) {
        val n = axes.size
        if (n < 3) return@Canvas

        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width / 2f - 56.dp.toPx(), size.height / 2f - 22.dp.toPx())

        fun angle(i: Int): Float = (-PI / 2 + i * 2 * PI / n).toFloat()
        fun point(i: Int, value: Float): Offset {
            val r = radius * (value / maxValue).coerceIn(0f, 1f)
            return Offset(center.x + r * cos(angle(i)), center.y + r * sin(angle(i)))
        }
        fun polygon(values: List<Float>) = Path().apply {
            values.forEachIndexed { i, v ->
                val p = point(i, v)
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }

        // Rejilla punteada
        val dashed = Stroke(
            width = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 7f)),
        )
        for (level in 1..levels) {
            drawPath(polygon(List(n) { maxValue * level / levels }), colors.grid, style = dashed)
        }
        for (i in 0 until n) {
            drawLine(colors.grid, center, point(i, maxValue), strokeWidth = 1.dp.toPx())
        }

        // Series (la primera de la lista queda al fondo)
        series.forEach { s ->
            val path = polygon(s.values)
            drawPath(path, s.color.copy(alpha = 0.35f))
            drawPath(path, s.color, style = Stroke(width = 1.5.dp.toPx()))
        }

        // Escala 0..10 sobre el eje superior
        for (level in 0..levels) {
            val label = measurer.measure((maxValue * level / levels).toInt().toString(), tickStyle)
            drawText(
                label,
                topLeft = Offset(
                    center.x + 3.dp.toPx(),
                    center.y - radius * level / levels - label.size.height / 2f,
                ),
            )
        }

        // Etiquetas de cada eje, alejadas del vértice según su ángulo
        axes.forEachIndexed { i, text ->
            val layout = measurer.measure(text, labelStyle)
            val outer = point(i, maxValue)
            val dx = cos(angle(i))
            val dy = sin(angle(i))
            val gap = 8.dp.toPx()
            drawText(
                layout,
                topLeft = Offset(
                    x = outer.x + dx * gap - layout.size.width * (0.5f - 0.5f * dx),
                    y = outer.y + dy * gap - layout.size.height * (0.5f - 0.5f * dy),
                ),
            )
        }
    }
}
