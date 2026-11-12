package com.example.prisma.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import prisma.app.shared.generated.resources.Res
import prisma.app.shared.generated.resources.tdsoft_symbol
import prisma.app.shared.generated.resources.tdsoft_wordmark

/** Colores de la presentación de TDSOFT (watch-log-08992977.figma.site). */
private object TdsoftColors {
    val Fondo = Color(0xFF111827)
    val Cian = Color(0xFF51D9E8)
    val Violeta = Color(0xFFB57BEE)
}

/** Duración total de la animación de inicio (ms). */
private const val TOTAL_MS = 4600f

private val EaseOutExpo: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** Avance 0..1 de un tramo [startMs, endMs] de la línea de tiempo. */
private fun seg(t: Float, startMs: Float, endMs: Float, easing: Easing = FastOutSlowInEasing): Float =
    easing.transform(((t - startMs) / (endMs - startMs)).coerceIn(0f, 1f))

/**
 * Animación de inicio: «TDSOFT presenta» → logo de Prisma con «by TDSOFT».
 *
 * Se muestra al abrir la app en Android, iOS y web (la llama [com.example.prisma.App]).
 * Un toque la salta. Respeta el manual de Prisma: el logo se escala y se desvanece,
 * pero nunca se deforma, rota ni recolorea, y va sobre fondo Grafito (alto contraste).
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val time = remember { Animatable(0f) }
    val finish by rememberUpdatedState(onFinished)

    // En la web las imágenes se cargan de forma asíncrona (mientras tanto miden 1×1 px):
    // la línea de tiempo arranca cuando ya están listas, para no perder la fase TDSOFT.
    val symbol = painterResource(Res.drawable.tdsoft_symbol)
    val wordmark = painterResource(Res.drawable.tdsoft_wordmark)
    var timedOut by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(2500); timedOut = true } // si no cargan, no bloquear la app
    val ready = timedOut || (symbol.intrinsicSize.width > 1f && wordmark.intrinsicSize.width > 1f)

    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        time.animateTo(TOTAL_MS, tween(TOTAL_MS.toInt(), easing = LinearEasing))
        finish()
    }

    val t = time.value
    val f = poppins()

    // --- Línea de tiempo (ms) ---
    // 0–900      fondo TDSOFT, halo cian y trazado del símbolo de izquierda a derecha
    // 600–1300   aparece la palabra TDSOFT y el lema «Software con propósito»
    // 1200–1700  «presenta»
    // 1900–2400  sale TDSOFT y el fondo pasa a Grafito Profundo (Prisma)
    // 2300–3100  entra el logo de Prisma, el lema y «by TDSOFT»
    // 4100–4600  todo se desvanece y aparece la app
    val reveal = seg(t, 0f, 900f, EaseOutExpo)
    val symbolIn = seg(t, 0f, 700f)
    val wordIn = seg(t, 600f, 1200f, EaseOutExpo)
    val taglineIn = seg(t, 900f, 1400f)
    val presentsIn = seg(t, 1200f, 1700f)
    val tdsoftOut = seg(t, 1900f, 2400f)
    val bgToPrisma = seg(t, 1900f, 2600f)
    val prismaIn = seg(t, 2300f, 3100f, EaseOutExpo)
    val sloganIn = seg(t, 2700f, 3200f)
    val byIn = seg(t, 2900f, 3400f)
    val exit = seg(t, 4100f, TOTAL_MS)

    val background = lerp(TdsoftColors.Fondo, PrismaPalette.GrafitoProfundo, bgToPrisma)
    val glow = lerp(TdsoftColors.Cian, PrismaPalette.NaranjaPrisma, bgToPrisma)
    val glowPulse = 0.85f + 0.15f * kotlin.math.sin(t / 260f)

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - exit }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { finish() },
            ),
    ) {
        // Fondo con halo radial y anillos que se expanden.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(background)
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension * 0.75f
            drawCircle(
                Brush.radialGradient(
                    listOf(glow.copy(alpha = 0.22f * glowPulse * symbolIn), Color.Transparent),
                    center = c,
                    radius = r,
                ),
                radius = r,
                center = c,
            )
            // Anillos de «señal»: uno en la fase TDSOFT y otro al entrar Prisma.
            listOf(seg(t, 200f, 1600f, LinearEasing) to TdsoftColors.Cian,
                seg(t, 2300f, 3600f, LinearEasing) to PrismaPalette.NaranjaPrisma).forEach { (p, color) ->
                if (p in 0.001f..0.999f) {
                    drawCircle(
                        color = color.copy(alpha = 0.35f * (1f - p)),
                        radius = size.minDimension * (0.12f + 0.55f * p),
                        center = c,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
            val unit = min(maxWidth, maxHeight)

            // ---------- Fase 1: TDSOFT presenta ----------
            if (tdsoftOut < 1f) {
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            alpha = 1f - tdsoftOut
                            val s = 1f + 0.08f * tdsoftOut
                            scaleX = s; scaleY = s
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        symbol,
                        contentDescription = "TDSOFT",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .width(min(unit * 0.5f, 240.dp))
                            .graphicsLayer {
                                alpha = symbolIn
                                val s = 0.85f + 0.15f * symbolIn
                                scaleX = s; scaleY = s
                            }
                            // Trazado: el símbolo se descubre de izquierda a derecha.
                            .drawWithContent {
                                clipRect(right = size.width * reveal) { this@drawWithContent.drawContent() }
                            },
                    )
                    Spacer(Modifier.height(20.dp))
                    Image(
                        wordmark,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .width(min(unit * 0.42f, 200.dp))
                            .graphicsLayer {
                                alpha = wordIn
                                translationY = (1f - wordIn) * 18.dp.toPx()
                                // Efecto de «tracking» que se cierra.
                                scaleX = 1.12f - 0.12f * wordIn
                            },
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.graphicsLayer { alpha = taglineIn },
                    ) {
                        Canvas(Modifier.size(6.dp)) { drawCircle(TdsoftColors.Cian) }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "SOFTWARE CON PROPÓSITO",
                            color = TdsoftColors.Cian,
                            fontFamily = f,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            letterSpacing = 0.2.em,
                        )
                    }
                    Spacer(Modifier.height(36.dp))
                    Text(
                        "presenta",
                        color = Color.White.copy(alpha = 0.85f),
                        fontFamily = f,
                        fontWeight = FontWeight.Normal,
                        fontSize = 15.sp,
                        letterSpacing = 0.12.em,
                        modifier = Modifier.graphicsLayer {
                            alpha = presentsIn
                            translationY = (1f - presentsIn) * 10.dp.toPx()
                        },
                    )
                    // Línea degradada cian → violeta, como en la presentación.
                    Canvas(
                        Modifier
                            .padding(top = 10.dp)
                            .width(120.dp)
                            .height(2.dp),
                    ) {
                        val w = size.width * presentsIn
                        drawLine(
                            Brush.horizontalGradient(listOf(TdsoftColors.Cian, TdsoftColors.Violeta)),
                            start = Offset((size.width - w) / 2f, size.height / 2f),
                            end = Offset((size.width + w) / 2f, size.height / 2f),
                            strokeWidth = size.height,
                        )
                    }
                }
            }

            // ---------- Fase 2: Prisma by TDSOFT ----------
            if (prismaIn > 0f) {
                Column(
                    Modifier.align(Alignment.Center).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PrismaLogo(
                        onDark = true,
                        modifier = Modifier
                            .width(min(unit * 0.42f, 200.dp))
                            .graphicsLayer {
                                alpha = prismaIn
                                val s = 0.8f + 0.2f * prismaIn
                                scaleX = s; scaleY = s // escala uniforme: el logo no se deforma
                            },
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Tu futuro en tus manos",
                        color = PrismaPalette.Crema,
                        fontFamily = f,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.graphicsLayer {
                            alpha = sloganIn
                            translationY = (1f - sloganIn) * 12.dp.toPx()
                        },
                    )
                }

                // Firma inferior: «by TDSOFT».
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                        .graphicsLayer { alpha = byIn },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "BY",
                        color = PrismaPalette.Crema.copy(alpha = 0.6f),
                        fontFamily = f,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        letterSpacing = 0.2.em,
                    )
                    Spacer(Modifier.width(10.dp))
                    Image(
                        symbol,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.height(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Image(
                        wordmark,
                        contentDescription = "by TDSOFT",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.height(11.dp),
                    )
                }
            }
        }
    }
}
