package com.example.prisma.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Colores base del Manual de Identidad Corporativa Prisma (pág. 7). */
object PrismaPalette {
    val NaranjaAmbar = Color(0xFFEB6520) // acento profundo
    val NaranjaPrisma = Color(0xFFFE7B07) // color principal de marca
    val Ambar = Color(0xFFFDAD3E)
    val Dorado = Color(0xFFFBC156)
    val Grafito = Color(0xFF2C2B30)
    val Crema = Color(0xFFF8ECDC)

    // Derivados (no están en el manual, se usan solo para superficies y estados)
    val CremaClara = Color(0xFFFFFAF2) // tarjetas sobre crema
    val GrafitoProfundo = Color(0xFF1B1B1D) // fondo oscuro de las páginas del manual
    val EstadoSaludable = Color(0xFF3E9B5F)
    val EstadoCritico = Color(0xFFD32F2F)
}

@Immutable
data class PrismaColors(
    val background: Color,
    val surface: Color,
    val onSurface: Color,
    val shadow: Color,
    val grid: Color,
    val navActive: Color,
    val navInactive: Color,
    val income: Color,
    val expense: Color,
    val investment: Color,
    val statusHealthy: Color,
    val statusWarning: Color,
    val statusCritical: Color,
)

private val LightColors = PrismaColors(
    background = PrismaPalette.Crema,
    surface = PrismaPalette.CremaClara,
    onSurface = PrismaPalette.Grafito,
    shadow = PrismaPalette.Grafito,
    grid = PrismaPalette.Grafito.copy(alpha = 0.35f),
    navActive = PrismaPalette.NaranjaAmbar,
    navInactive = PrismaPalette.Grafito.copy(alpha = 0.45f),
    income = PrismaPalette.NaranjaAmbar,
    expense = PrismaPalette.Dorado,
    investment = PrismaPalette.Grafito,
    statusHealthy = PrismaPalette.EstadoSaludable,
    statusWarning = PrismaPalette.Dorado,
    statusCritical = PrismaPalette.EstadoCritico,
)

private val DarkColors = PrismaColors(
    background = PrismaPalette.GrafitoProfundo,
    surface = PrismaPalette.Grafito,
    onSurface = PrismaPalette.Crema,
    shadow = Color.Black,
    grid = PrismaPalette.Crema.copy(alpha = 0.30f),
    navActive = PrismaPalette.NaranjaPrisma,
    navInactive = PrismaPalette.Crema.copy(alpha = 0.50f),
    income = PrismaPalette.NaranjaPrisma,
    expense = PrismaPalette.Dorado,
    investment = PrismaPalette.Crema,
    statusHealthy = PrismaPalette.EstadoSaludable,
    statusWarning = PrismaPalette.Dorado,
    statusCritical = PrismaPalette.EstadoCritico,
)

private val LocalPrismaColors = staticCompositionLocalOf { LightColors }

/** Acceso a los colores de marca: `Prisma.colors.navActive` */
object Prisma {
    val colors: PrismaColors
        @Composable get() = LocalPrismaColors.current
}

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val c = if (darkTheme) DarkColors else LightColors
    val scheme = if (darkTheme) {
        darkColorScheme(background = c.background, surface = c.surface, onSurface = c.onSurface,
            onBackground = c.onSurface, primary = c.navActive)
    } else {
        lightColorScheme(background = c.background, surface = c.surface, onSurface = c.onSurface,
            onBackground = c.onSurface, primary = c.navActive)
    }

    CompositionLocalProvider(LocalPrismaColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = prismaTypography()) {
            // Define el color de texto por defecto (necesario para que el modo oscuro se lea).
            Surface(modifier = Modifier.fillMaxSize(), color = c.background, contentColor = c.onSurface) {
                content()
            }
        }
    }
}
