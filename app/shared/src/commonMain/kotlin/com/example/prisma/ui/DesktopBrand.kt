package com.example.prisma.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Etiqueta de marca: MAYÚSCULAS con tracking +200 (manual pág. 8, "Etiquetas").
 * +200 en unidades de diseño equivale a 0.2 em.
 */
@Composable
fun BrandLabel(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.2.em),
        color = color,
        modifier = modifier,
    )
}

private data class BrandValue(val title: String, val description: String, val background: Color, val ink: Color)

/** Valores de marca (manual pág. 2), con los mismos colores alternados del manual. */
private val BRAND_VALUES = listOf(
    BrandValue("Inteligencia", "Decisiones claras basadas en conocimiento.", PrismaPalette.NaranjaPrisma, Color.White),
    BrandValue("Agilidad", "Rapidez y adaptación a cada situación.", PrismaPalette.Ambar, PrismaPalette.Grafito),
    BrandValue("Control", "Seguridad y dominio sobre cada paso.", PrismaPalette.NaranjaPrisma, Color.White),
    BrandValue("Crecimiento", "Progreso constante, personal y de negocio.", PrismaPalette.Ambar, PrismaPalette.Grafito),
)

/**
 * Panel de marca del login en escritorio (solo web): fondo Grafito, logotipo para fondo oscuro,
 * promesa "Tu futuro en tus manos" y los cuatro valores de marca.
 */
@Composable
fun DesktopBrandPanel(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.background(PrismaPalette.Grafito)) {
        val wide = maxWidth >= 440.dp
        val panelHeight = maxHeight
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        // Centrado vertical en monitores altos; con scroll si la ventana es baja.
        Column(
            Modifier
                .heightIn(min = panelHeight)
                .widthIn(max = 680.dp)
                .padding(horizontal = if (wide) 56.dp else 32.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
        ) {
            PrismaLogo(Modifier.height(if (panelHeight >= 820.dp) 220.dp else 160.dp), onDark = true)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Prisma da forma a tu futuro",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = if (wide) 40.sp else 32.sp,
                        lineHeight = if (wide) 48.sp else 40.sp,
                    ),
                    color = PrismaPalette.Crema,
                )
                Text(
                    "Inteligencia · Agilidad · Control",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 18.sp),
                    color = PrismaPalette.Dorado,
                )
            }

            // Valores de marca: 2 columnas si hay espacio, si no una.
            val rows = if (wide) BRAND_VALUES.chunked(2) else BRAND_VALUES.map { listOf(it) }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        row.forEach { value ->
                            Column(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(value.background)
                                    .padding(horizontal = 18.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    value.title.uppercase(),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = value.ink,
                                )
                                Text(value.description, style = MaterialTheme.typography.bodyMedium, color = value.ink)
                            }
                        }
                    }
                }
            }

            BrandLabel("Tu futuro en tus manos", color = PrismaPalette.Crema.copy(alpha = 0.8f))
        }
        }
    }
}

