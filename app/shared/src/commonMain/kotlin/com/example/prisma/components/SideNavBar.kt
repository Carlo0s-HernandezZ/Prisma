package com.example.prisma.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.prisma.ui.BrandLabel
import com.example.prisma.ui.PrismaLogo
import com.example.prisma.ui.PrismaPalette

val SideNavBarWidth = 248.dp
private val ItemHeight = 48.dp
private val ItemSpacing = 6.dp

/**
 * Menú lateral del diseño de escritorio (solo web).
 *
 * Fondo Grafito con el logotipo para fondo oscuro (manual pág. 4) y la misma lógica de la barra
 * inferior móvil: un solo indicador que se desliza hasta la sección elegida.
 */
@Composable
fun SideNavBar(
    selected: NavDestination,
    onSelect: (NavDestination) -> Unit,
    userEmail: String,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val destinations = NavDestination.entries
    val selectedIndex = destinations.indexOf(selected)

    Column(
        modifier
            .width(SideNavBarWidth)
            .fillMaxHeight()
            .background(PrismaPalette.Grafito)
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        // Logotipo completo: ≥ 100 px de ancho y con área de seguridad (manual pág. 9).
        PrismaLogo(
            Modifier.padding(horizontal = 36.dp).fillMaxWidth().height(132.dp).align(Alignment.CenterHorizontally),
            onDark = true,
        )

        Spacer(Modifier.height(32.dp))
        BrandLabel("Menú", color = PrismaPalette.Crema.copy(alpha = 0.55f), modifier = Modifier.padding(start = 12.dp, bottom = 12.dp))

        Box {
            // Indicador que se desliza verticalmente hasta la sección seleccionada.
            val indicatorOffset by animateDpAsState(
                targetValue = (ItemHeight + ItemSpacing) * selectedIndex,
                animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
                label = "sideNavIndicator",
            )
            Row(
                Modifier
                    .offset(y = indicatorOffset)
                    .fillMaxWidth()
                    .height(ItemHeight)
                    .clip(RoundedCornerShape(14.dp))
                    .background(PrismaPalette.Crema.copy(alpha = 0.10f)),
            ) {
                Box(Modifier.width(4.dp).fillMaxHeight().background(PrismaPalette.NaranjaPrisma))
            }

            Column(verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
                destinations.forEach { dest ->
                    SideNavItem(dest, isSelected = dest == selected, onClick = { if (dest != selected) onSelect(dest) })
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Cuenta y cierre de sesión
        Text(
            userEmail,
            style = MaterialTheme.typography.bodyMedium,
            color = PrismaPalette.Crema.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Spacer(Modifier.height(8.dp))
        val logoutInteraction = remember { MutableInteractionSource() }
        val logoutHovered by logoutInteraction.collectIsHoveredAsState()
        Row(
            Modifier
                .fillMaxWidth()
                .height(ItemHeight)
                .clip(RoundedCornerShape(14.dp))
                .background(if (logoutHovered) PrismaPalette.Crema.copy(alpha = 0.08f) else Color.Transparent)
                .hoverable(logoutInteraction)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(interactionSource = logoutInteraction, indication = null, role = Role.Button, onClick = onLogout)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = PrismaPalette.NaranjaPrisma, modifier = Modifier.size(22.dp))
            Text("Cerrar sesión", style = MaterialTheme.typography.titleMedium, color = PrismaPalette.Crema)
        }
    }
}

@Composable
private fun SideNavItem(destination: NavDestination, isSelected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val tint by animateColorAsState(
        targetValue = when {
            isSelected -> PrismaPalette.Dorado
            hovered -> PrismaPalette.Crema
            else -> PrismaPalette.Crema.copy(alpha = 0.6f)
        },
        label = "sideNavTint",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .height(ItemHeight)
            .clip(RoundedCornerShape(14.dp))
            .semantics { this.selected = isSelected }
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(destination.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(
            destination.description,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium),
            color = tint,
        )
    }
}
