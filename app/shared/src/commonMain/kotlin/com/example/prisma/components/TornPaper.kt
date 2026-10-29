package com.example.prisma.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Papel rasgado: rellena el borde superior del Canvas y deja abajo un borde irregular,
 * con una franja de fibra y una sombra suave, como en el mockup.
 * El borde baja un poco hacia la derecha. Usa una semilla fija: siempre se ve igual.
 */
@Composable
fun TornPaper(
    paperColor: Color,
    fringeColor: Color,
    modifier: Modifier = Modifier,
    shadowColor: Color = Color.Black.copy(alpha = 0.16f),
    seed: Int = 7,
) {
    val points = 160
    val mainNoise = remember(seed) { Random(seed).let { r -> List(points) { r.nextFloat() } } }
    val fringeNoise = remember(seed) { Random(seed + 1).let { r -> List(points) { r.nextFloat() } } }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val amp = 3.5.dp.toPx()

        fun edge(offsetPx: Float, noise: List<Float>, amplitude: Float): Path = Path().apply {
            moveTo(0f, 0f)
            lineTo(w, 0f)
            for (i in points - 1 downTo 0) {
                val t = i / (points - 1f)
                val base = h * (0.72f + 0.10f * t)                    // inclinación leve
                val wave = sin(t * 2f * PI.toFloat() * 2.3f) * 3.dp.toPx() // ondulación suave
                lineTo(w * t, base + wave + offsetPx + (noise[i] - 0.5f) * 2f * amplitude)
            }
            close()
        }

        drawPath(edge(10.dp.toPx(), fringeNoise, amp * 1.4f), shadowColor)
        drawPath(edge(4.dp.toPx(), fringeNoise, amp * 1.3f), fringeColor)
        drawPath(edge(0f, mainNoise, amp), paperColor)
    }
}
