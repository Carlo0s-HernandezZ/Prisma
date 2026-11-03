package com.example.prisma.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.prisma.components.bottomNavBarSpace
import com.example.prisma.components.NavDestination
import com.example.prisma.components.SoftCard

/** Ventanas de la barra que todavía no están implementadas (Estadísticas, Asistente, Tarjetas). */
@Composable
fun ComingSoonScreen(destination: NavDestination, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(Prisma.colors.background)
            .safeDrawingPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomNavBarSpace),
        contentAlignment = Alignment.Center,
    ) {
        SoftCard(Modifier.widthIn(max = 448.dp).fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = destination.icon,
                    contentDescription = null,
                    tint = PrismaPalette.NaranjaPrisma,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    destination.description,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Prisma.colors.onSurface,
                )
                Text(
                    "Esta sección estará disponible muy pronto.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Prisma.colors.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
