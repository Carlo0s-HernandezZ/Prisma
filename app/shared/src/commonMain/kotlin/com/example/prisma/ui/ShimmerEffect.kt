package com.example.prisma.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified

/*
 * Esqueleto de carga (diseño "diseñoEsqueleto"): bloques suaves con esquinas redondeadas
 * y un brillo diagonal que los recorre de izquierda a derecha.
 * Cada pantalla arma su esqueleto con la MISMA estructura que el contenido real
 * (mismos contenedores, rellenos y alturas de texto), así nada "salta" al terminar de cargar.
 */

@Immutable
data class SkeletonColors(val base: Color, val shine: Color)

/** Colores según el tema: grafito muy tenue sobre Crema; crema muy tenue sobre Grafito. */
@Composable
fun skeletonColors(): SkeletonColors {
    val dark = Prisma.colors.background.luminance() < 0.5f
    return if (dark) {
        SkeletonColors(base = PrismaPalette.Crema.copy(alpha = 0.08f), shine = PrismaPalette.Crema.copy(alpha = 0.16f))
    } else {
        SkeletonColors(base = PrismaPalette.Grafito.copy(alpha = 0.07f), shine = Color.White.copy(alpha = 0.85f))
    }
}

/** Avance 0..1 del brillo (1.4 s por pasada y una pausa corta, en bucle). */
@Composable
fun rememberSkeletonProgress(): State<Float> =
    rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1400, delayMillis = 250, easing = LinearEasing), RepeatMode.Restart),
        label = "skeletonShine",
    )

/** Dibuja la banda de brillo diagonal sobre el área actual (ya recortada a la forma). */
fun DrawScope.drawSkeletonShine(progress: Float, shine: Color) {
    val band = maxOf(size.width * 0.45f, 160f)
    val x = -band + (size.width + 2 * band) * progress
    drawRect(
        Brush.linearGradient(
            colors = listOf(Color.Transparent, shine, Color.Transparent),
            start = Offset(x - band / 2f, 0f),
            end = Offset(x + band / 2f, size.height),
        ),
    )
}

/** Convierte cualquier elemento en un bloque de esqueleto con la forma [shape]. */
fun Modifier.skeleton(shape: Shape = RoundedCornerShape(12.dp)): Modifier = composed {
    val colors = skeletonColors()
    val progress = rememberSkeletonProgress()
    clip(shape)
        .background(colors.base)
        .drawBehind { drawSkeletonShine(progress.value, colors.shine) }
}

/** Compatibilidad con el nombre anterior. */
fun Modifier.shimmerEffect(shape: Shape = RoundedCornerShape(12.dp)): Modifier = skeleton(shape)

/** Marca un contenedor como "cargando" para lectores de pantalla. */
fun Modifier.loadingSemantics(label: String = "Cargando"): Modifier = semantics { contentDescription = label }

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    height: Dp = 20.dp,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    Box(modifier.height(height).skeleton(shape))
}

/**
 * Línea de texto falsa: ocupa exactamente la altura de una línea de [style] (como el Text real)
 * y dibuja una barra redondeada del alto de las letras, centrada.
 */
@Composable
fun SkeletonText(style: TextStyle, width: Dp, modifier: Modifier = Modifier) {
    SkeletonTextLine(style, modifier.width(width))
}

/** Igual que [SkeletonText] pero con ancho relativo al contenedor (0..1). */
@Composable
fun SkeletonTextFraction(style: TextStyle, fraction: Float, modifier: Modifier = Modifier) {
    SkeletonTextLine(style, modifier.fillMaxWidth(fraction))
}

@Composable
private fun SkeletonTextLine(style: TextStyle, modifier: Modifier) {
    val density = LocalDensity.current
    val fontSize = if (style.fontSize.isSpecified) style.fontSize else MaterialTheme.typography.bodyMedium.fontSize
    val lineHeight = if (style.lineHeight.isSpecified) style.lineHeight else fontSize * 1.4f
    val lineDp = with(density) { lineHeight.toDp() }
    val barDp = with(density) { (fontSize * 0.78f).toDp() }
    Box(modifier.height(lineDp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(barDp).skeleton(RoundedCornerShape(50)))
    }
}

@Composable
fun SkeletonCircle(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).skeleton(CircleShape))
}

/** Hueco de un IconButton (48 dp) con el ícono de 24 dp como bloque. */
@Composable
fun SkeletonIconButton(modifier: Modifier = Modifier) {
    Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(24.dp).skeleton(RoundedCornerShape(8.dp)))
    }
}
