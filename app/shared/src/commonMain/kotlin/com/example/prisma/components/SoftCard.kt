package com.example.prisma.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.prisma.ui.Prisma

/** Superficie con sombra suave: el "look" de todas las tarjetas del mockup (claro y oscuro). */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    elevation: Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    val colors = Prisma.colors
    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = colors.shadow.copy(alpha = 0.25f),
                spotColor = colors.shadow.copy(alpha = 0.35f),
            )
            .background(colors.surface, shape),
    ) { content() }
}
