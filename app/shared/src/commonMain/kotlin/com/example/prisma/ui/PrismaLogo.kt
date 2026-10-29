package com.example.prisma.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import prisma.app.shared.generated.resources.Res
import prisma.app.shared.generated.resources.prisma_logo_dark
import prisma.app.shared.generated.resources.prisma_logo_light

// TODO: agrega aquí el MISMO import de `Res` que usaste en AppTypography.kt
// (Alt+Enter sobre "Res"). Es el único archivo nuevo que lo necesita.

/** Logotipo de la Propuesta 1: versión para fondo claro u oscuro según el tema. */
@Composable
fun PrismaLogo(modifier: Modifier = Modifier) {
    val isLight = Prisma.colors.background.luminance() > 0.5f
    Image(
        painter = painterResource(
            if (isLight) Res.drawable.prisma_logo_light else Res.drawable.prisma_logo_dark
        ),
        contentDescription = "Prisma",
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}
