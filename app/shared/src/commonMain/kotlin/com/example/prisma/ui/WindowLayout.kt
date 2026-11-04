package com.example.prisma.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.prisma.getPlatform

/**
 * Diseño de la app según la plataforma y el ancho de la ventana.
 *
 * - [Compact]: diseño móvil (Android, iOS y ventanas web angostas). Es el diseño original.
 * - [Desktop]: solo en la web con ventanas de [DESKTOP_MIN_WIDTH] o más: menú lateral,
 *   contenido en columnas y ancho máximo para monitores grandes.
 */
enum class LayoutMode { Compact, Desktop }

/** Ancho a partir del cual la web usa el diseño de escritorio. */
val DESKTOP_MIN_WIDTH: Dp = 900.dp

/** Ancho máximo del contenido en escritorio, para que no se estire en monitores muy grandes. */
val DESKTOP_CONTENT_MAX_WIDTH: Dp = 1280.dp

private val LocalLayoutMode = staticCompositionLocalOf { LayoutMode.Compact }

/** true cuando se debe usar el diseño de escritorio (solo web, ventana ancha). */
val isDesktopLayout: Boolean
    @Composable get() = LocalLayoutMode.current == LayoutMode.Desktop

/** Calcula el modo de diseño con el ancho disponible y lo comparte con toda la app. */
@Composable
fun ProvideLayoutMode(content: @Composable () -> Unit) {
    val isWeb = remember { getPlatform().isWeb }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val mode = if (isWeb && maxWidth >= DESKTOP_MIN_WIDTH) LayoutMode.Desktop else LayoutMode.Compact
        CompositionLocalProvider(LocalLayoutMode provides mode) { content() }
    }
}

/** Ancho máximo del contenido en móvil (el diseño original). */
val COMPACT_CONTENT_MAX_WIDTH: Dp = 480.dp

/**
 * Contenedor estándar de una pantalla: ancho máximo, márgenes y áreas seguras.
 * Móvil: 480 dp de ancho, márgenes de 16/12 dp (sin cambios). Escritorio: hasta 1280 dp y márgenes de 40/32 dp.
 */
@Composable
fun Modifier.screenContainer(): Modifier {
    val desktop = isDesktopLayout
    return this
        .widthIn(max = if (desktop) DESKTOP_CONTENT_MAX_WIDTH else COMPACT_CONTENT_MAX_WIDTH)
        .fillMaxSize()
        .safeDrawingPadding()
        .padding(horizontal = if (desktop) 40.dp else 16.dp, vertical = if (desktop) 32.dp else 12.dp)
}
