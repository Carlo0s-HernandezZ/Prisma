package com.example.prisma.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.prisma.ui.Prisma
import com.example.prisma.ui.PrismaPalette

enum class NavDestination(val icon: ImageVector, val description: String) {
    Home(Icons.Filled.Home, "Inicio"),
    Stats(Icons.Filled.PieChart, "Estadísticas"),
    Assistant(Icons.Filled.SmartToy, "Asistente IA"),
    Cards(Icons.Filled.CreditCard, "Tarjetas"),
    Profile(Icons.Filled.Person, "Perfil"),
}

@Composable
fun BottomNavBar(
    selected: NavDestination,
    onSelect: (NavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Prisma.colors
    SoftCard(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), elevation = 10.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavDestination.entries.forEach { dest ->
                val isSelected = dest == selected

                val animatedScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.15f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )

                val animatedColor by animateColorAsState(
                    targetValue = if (isSelected) PrismaPalette.Dorado else colors.navInactive,
                    animationSpec = spring(stiffness = Spring.StiffnessLow)
                )

                val animatedPillWidth by animateDpAsState(
                    targetValue = if (isSelected) 52.dp else 40.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )

                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .width(animatedPillWidth)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (isSelected) PrismaPalette.Grafito else Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(dest) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = dest.icon,
                        contentDescription = dest.description,
                        tint = animatedColor,
                        modifier = Modifier
                            .size(28.dp)
                            .scale(animatedScale)
                    )
                }
            }
        }
    }
}
