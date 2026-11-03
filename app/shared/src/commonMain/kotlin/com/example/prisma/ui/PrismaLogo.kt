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

/**
 * Logotipo de la Propuesta 1: versión para fondo claro u oscuro.
 *
 * @param onDark fuerza la versión para fondo oscuro (true) o claro (false); null = según el tema.
 * Manual: no deformar (ContentScale.Fit mantiene la proporción), no usar sobre bajo contraste.
 */
@Composable
fun PrismaLogo(modifier: Modifier = Modifier, onDark: Boolean? = null) {
    val dark = onDark ?: (Prisma.colors.background.luminance() <= 0.5f)
    Image(
        painter = painterResource(
            if (dark) Res.drawable.prisma_logo_dark else Res.drawable.prisma_logo_light
        ),
        contentDescription = "Prisma",
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}
