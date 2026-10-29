package com.example.prisma.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.prisma.ui.Prisma

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
    SoftCard(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), elevation = 8.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavDestination.entries.forEach { dest ->
                Icon(
                    imageVector = dest.icon,
                    contentDescription = dest.description,
                    tint = if (dest == selected) colors.navActive else colors.navInactive,
                    modifier = Modifier.size(40.dp).clickable { onSelect(dest) },
                )
            }
        }
    }
}
