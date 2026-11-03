package com.example.prisma.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.prisma.ui.Prisma
import com.example.prisma.ui.PrismaPalette
import com.example.prisma.ui.isDesktopLayout

enum class NavDestination(val icon: ImageVector, val description: String) {
    Home(Icons.Filled.Home, "Inicio"),
    Stats(Icons.Filled.PieChart, "Estadísticas"),
    Assistant(Icons.Filled.SmartToy, "Asistente IA"),
    Cards(Icons.Filled.CreditCard, "Tarjetas"),
    Profile(Icons.Filled.Person, "Perfil"),
}

/**
 * Espacio que las pantallas deben dejar al final de su contenido desplazable para que
 * nada quede tapado por la barra flotante (que es semitransparente y va encima).
 */
val BottomNavBarReservedSpace = 88.dp

/** Espacio inferior que deja cada pantalla: en escritorio (web) no hay barra inferior. */
val bottomNavBarSpace: Dp
    @Composable get() = if (isDesktopLayout) 32.dp else BottomNavBarReservedSpace

private val BarShape = RoundedCornerShape(28.dp)
private val IndicatorWidth = 52.dp
private val IndicatorHeight = 48.dp

/** Opacidad del fondo de la barra: deja ver el contenido detrás sin perder legibilidad. */
private const val BAR_ALPHA = 0.78f

/**
 * Barra de navegación flotante y semitransparente.
 *
 * Debe existir UNA sola instancia (en HomeScreen) para que el indicador pueda deslizarse
 * de una pestaña a otra; si cada pantalla dibuja la suya, la animación nunca se ve.
 */
@Composable
fun BottomNavBar(
    selected: NavDestination,
    onSelect: (NavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Prisma.colors
    val destinations = NavDestination.entries
    val selectedIndex = destinations.indexOf(selected)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(BarShape)
            .background(colors.surface.copy(alpha = BAR_ALPHA))
            .border(1.dp, colors.onSurface.copy(alpha = 0.08f), BarShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        val slotWidth = maxWidth / destinations.size

        // Indicador que se desliza hasta la pestaña seleccionada.
        val indicatorOffset by animateDpAsState(
            targetValue = slotWidth * selectedIndex + (slotWidth - IndicatorWidth) / 2,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
            label = "navIndicatorOffset",
        )
        Box(
            Modifier
                .offset(x = indicatorOffset)
                .size(IndicatorWidth, IndicatorHeight)
                .clip(RoundedCornerShape(24.dp))
                .background(PrismaPalette.Grafito.copy(alpha = 0.92f))
        )

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            destinations.forEach { dest ->
                val isSelected = dest == selected

                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.15f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "navIconScale",
                )
                val tint by animateColorAsState(
                    targetValue = if (isSelected) PrismaPalette.Dorado else colors.navInactive,
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "navIconTint",
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(IndicatorHeight)
                        .semantics { this.selected = isSelected }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { if (!isSelected) onSelect(dest) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = dest.icon,
                        contentDescription = dest.description,
                        tint = tint,
                        modifier = Modifier.size(28.dp).scale(scale),
                    )
                }
            }
        }
    }
}
