package com.example.prisma.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import com.example.prisma.ui.Prisma
import com.example.prisma.ui.PrismaPalette
import com.example.prisma.ui.isDesktopLayout
import kotlin.math.roundToInt

enum class NavDestination(val icon: ImageVector, val description: String, val label: String) {
    Home(Icons.Filled.Home, "Inicio", "Inicio"),
    Stats(Icons.Filled.PieChart, "Estadísticas", "Resumen"),
    Assistant(Icons.Filled.SmartToy, "Asistente IA", "Asistente"),
    Cards(Icons.Filled.CreditCard, "Tarjetas", "Tarjetas"),
    Profile(Icons.Filled.Person, "Perfil", "Perfil"),
}

/**
 * Espacio que las pantallas deben dejar al final de su contenido desplazable para que
 * nada quede tapado por la barra flotante (que es semitransparente y va encima).
 */
val BottomNavBarReservedSpace = 96.dp

/** Espacio inferior que deja cada pantalla: en escritorio (web) no hay barra inferior. */
val bottomNavBarSpace: Dp
    @Composable get() = if (isDesktopLayout) 32.dp else BottomNavBarReservedSpace

private val BarHeight = 68.dp
private val BarPadding = 6.dp
private val BarShape = CircleShape // cápsula
private val BubbleShape = CircleShape

/** Colores del "vidrio" según el tema, siempre dentro de la paleta del Manual de Identidad. */
private class GlassColors(
    val fill: Brush,
    val tint: Brush,
    val rim: Brush,
    val bubble: Brush,
    val bubbleRim: Brush,
    val active: Color,
    val inactive: Color,
    val shadow: Color,
)

@Composable
private fun glassColors(): GlassColors {
    val colors = Prisma.colors
    val light = colors.background.luminance() > 0.5f
    return if (light) {
        GlassColors(
            // Vidrio claro: Crema translúcida con brillo blanco arriba.
            fill = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.78f), PrismaPalette.Crema.copy(alpha = 0.66f))),
            // Reflejo iridiscente sutil con los naranjas de la marca.
            tint = Brush.linearGradient(
                listOf(PrismaPalette.NaranjaPrisma.copy(alpha = 0.10f), Color.Transparent, PrismaPalette.Dorado.copy(alpha = 0.14f), PrismaPalette.Ambar.copy(alpha = 0.10f)),
            ),
            rim = Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.95f), PrismaPalette.Ambar.copy(alpha = 0.55f), PrismaPalette.NaranjaPrisma.copy(alpha = 0.35f), Color.White.copy(alpha = 0.85f)),
            ),
            bubble = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.98f), PrismaPalette.Crema.copy(alpha = 0.90f))),
            bubbleRim = Brush.linearGradient(listOf(Color.White, PrismaPalette.Dorado.copy(alpha = 0.75f), Color.White.copy(alpha = 0.9f))),
            active = PrismaPalette.NaranjaAmbar,
            inactive = colors.navInactive.copy(alpha = 0.62f),
            shadow = PrismaPalette.Grafito.copy(alpha = 0.30f),
        )
    } else {
        GlassColors(
            // Vidrio oscuro: Grafito translúcido.
            fill = Brush.verticalGradient(listOf(Color(0xFF3A393E).copy(alpha = 0.82f), PrismaPalette.Grafito.copy(alpha = 0.78f))),
            tint = Brush.linearGradient(
                listOf(PrismaPalette.NaranjaPrisma.copy(alpha = 0.14f), Color.Transparent, PrismaPalette.Dorado.copy(alpha = 0.10f), PrismaPalette.Ambar.copy(alpha = 0.12f)),
            ),
            rim = Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.30f), PrismaPalette.Ambar.copy(alpha = 0.45f), PrismaPalette.NaranjaPrisma.copy(alpha = 0.30f), Color.White.copy(alpha = 0.22f)),
            ),
            bubble = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.07f))),
            bubbleRim = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.45f), PrismaPalette.Dorado.copy(alpha = 0.55f), Color.White.copy(alpha = 0.25f))),
            active = PrismaPalette.Dorado,
            inactive = colors.navInactive,
            shadow = Color.Black.copy(alpha = 0.55f),
        )
    }
}

/**
 * Barra de navegación flotante estilo "Liquid Glass".
 *
 * - **Toque**: elige la sección.
 * - **Deslizar el dedo** sobre la barra (como en iPhone): la burbuja sigue al dedo, vibra al pasar
 *   por cada sección y, al soltar, abre la sección donde quedó la burbuja.
 *
 * Debe existir UNA sola instancia (en HomeScreen) para que la burbuja pueda moverse entre pestañas.
 */
@Composable
fun BottomNavBar(
    selected: NavDestination,
    onSelect: (NavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = glassColors()
    val destinations = NavDestination.entries
    val selectedIndex = destinations.indexOf(selected)
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentSelected by rememberUpdatedState(selected)

    /** Posición X (px) del dedo dentro de la barra mientras se arrastra; null = sin arrastre. */
    var dragX by remember { mutableStateOf<Float?>(null) }
    /** Sección bajo el dedo durante el arrastre (para resaltarla y vibrar al cambiar). */
    var hoverIndex by remember { mutableStateOf(selectedIndex) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight)
            .shadow(elevation = 22.dp, shape = BarShape, ambientColor = glass.shadow, spotColor = glass.shadow)
            .clip(BarShape)
            .background(glass.fill)
            .background(glass.tint)
            .border(1.2.dp, glass.rim, BarShape),
    ) {
        // Brillo especular en la mitad superior (el "reflejo" del vidrio).
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent))),
        )

        BoxWithConstraints(Modifier.fillMaxSize().padding(BarPadding)) {
            val widthPx = with(density) { maxWidth.toPx() }
            val slotPx = widthPx / destinations.size
            val slotWidth = maxWidth / destinations.size
            fun indexAt(x: Float) = (x / slotPx).toInt().coerceIn(0, destinations.lastIndex)

            // Centro de la burbuja: sigue al dedo (sin animación) o se acomoda con resorte en la sección elegida.
            val dragging = dragX != null
            val targetCenter = dragX?.coerceIn(slotPx / 2, widthPx - slotPx / 2) ?: (slotPx * selectedIndex + slotPx / 2)
            val bubbleCenter by animateFloatAsState(
                targetValue = targetCenter,
                animationSpec = if (dragging) snap() else spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow),
                label = "bubbleCenter",
            )
            // Al arrastrar, la burbuja "se levanta" como una lupa de vidrio.
            val bubbleScale by animateFloatAsState(
                targetValue = if (dragging) 1.12f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "bubbleScale",
            )

            Box(
                Modifier
                    .offset { IntOffset((bubbleCenter - slotPx / 2).roundToInt(), 0) }
                    .width(slotWidth)
                    .fillMaxHeight()
                    .graphicsLayer { scaleX = bubbleScale; scaleY = bubbleScale }
                    .shadow(6.dp, BubbleShape, ambientColor = glass.shadow, spotColor = glass.shadow)
                    .clip(BubbleShape)
                    .background(glass.bubble)
                    .border(1.dp, glass.bubbleRim, BubbleShape),
            )

            Row(
                Modifier
                    .fillMaxSize()
                    // Deslizar el dedo para cambiar de sección (el toque lo atiende cada ícono).
                    .pointerInput(destinations.size, widthPx) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                dragX = offset.x
                                hoverIndex = indexAt(offset.x)
                            },
                            onHorizontalDrag = { change, delta ->
                                change.consume()
                                val x = ((dragX ?: change.position.x) + delta).coerceIn(0f, widthPx)
                                dragX = x
                                val i = indexAt(x)
                                if (i != hoverIndex) {
                                    hoverIndex = i
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            onDragEnd = {
                                val target = destinations[hoverIndex]
                                dragX = null
                                if (target != currentSelected) currentOnSelect(target)
                            },
                            onDragCancel = {
                                dragX = null
                                hoverIndex = destinations.indexOf(currentSelected)
                            },
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destinations.forEachIndexed { index, dest ->
                    val isActive = if (dragging) index == hoverIndex else dest == selected
                    NavItem(
                        destination = dest,
                        active = isActive,
                        selected = dest == selected,
                        glass = glass,
                        onClick = {
                            hoverIndex = index
                            if (dest != selected) currentOnSelect(dest)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    destination: NavDestination,
    active: Boolean,
    selected: Boolean,
    glass: GlassColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint by animateColorAsState(
        targetValue = if (active) glass.active else glass.inactive,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "navTint",
    )
    val scale by animateFloatAsState(
        targetValue = if (active) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "navScale",
    )
    Column(
        modifier = modifier
            .fillMaxHeight()
            .semantics { this.selected = selected }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = destination.description,
            tint = tint,
            modifier = Modifier.size(24.dp).scale(scale),
        )
        Text(
            text = destination.label,
            color = tint,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                letterSpacing = 0.2.sp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
