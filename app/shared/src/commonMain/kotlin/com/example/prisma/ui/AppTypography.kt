package com.example.prisma.ui

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import prisma.app.shared.generated.resources.Res
import prisma.app.shared.generated.resources.poppins_medium
import prisma.app.shared.generated.resources.poppins_regular
import prisma.app.shared.generated.resources.poppins_semibold

// TODO: agrega el import de `Res` (Alt+Enter sobre "Res" en Android Studio).
// Se genera al compilar, tras poner las fuentes en composeResources/font/.

/**
 * Manual de identidad (pág. 8): titulares Poppins SemiBold, subtítulos Poppins Medium,
 * texto Poppins/Inter Regular.
 *
 * Archivos requeridos en composeApp/src/commonMain/composeResources/font/:
 *   poppins_regular.ttf · poppins_medium.ttf · poppins_semibold.ttf
 */
@Composable
fun poppins(): FontFamily = FontFamily(
    Font(Res.font.poppins_regular, FontWeight.Normal),
    Font(Res.font.poppins_medium, FontWeight.Medium),
    Font(Res.font.poppins_semibold, FontWeight.SemiBold),
)

@Composable
fun prismaTypography(): Typography {
    val f = poppins()
    return Typography(
        // Todos los estilos usan Poppins (manual pág. 8). Los que no se definían caían en la
        // fuente por defecto (Roboto): botones, montos grandes, etiquetas y textos pequeños.
        headlineLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp),
        headlineMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
        headlineSmall = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
        titleLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
        titleSmall = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        labelMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
        titleMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp),
        bodyLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
        labelSmall = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = 10.sp, lineHeight = 14.sp),
    )
}
