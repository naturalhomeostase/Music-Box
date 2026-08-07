package com.harmonic.player.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.harmonic.player.R

/** Fonte enviada pelo usuário (baixada do Google Fonts) — aplicada em toda a tipografia do app. */
val GeistPixelFamily = FontFamily(
    Font(R.font.geist_pixel, weight = FontWeight.Normal)
)

private fun TextStyle.withGeistPixel() = copy(fontFamily = GeistPixelFamily)

// Parte dos estilos: mantém os tamanhos/pesos já definidos originalmente,
// só trocando a fonte. O resto do type scale (não usado explicitamente
// antes) cai nos valores padrão do Material3, também com a fonte aplicada.
private val materialDefaults = Typography()

val HarmonicTypography = Typography(
    displayLarge = materialDefaults.displayLarge.withGeistPixel(),
    displayMedium = materialDefaults.displayMedium.withGeistPixel(),
    displaySmall = materialDefaults.displaySmall.withGeistPixel(),
    headlineLarge = materialDefaults.headlineLarge.withGeistPixel(),
    headlineMedium = materialDefaults.headlineMedium.withGeistPixel(),
    headlineSmall = materialDefaults.headlineSmall.withGeistPixel(),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, fontFamily = GeistPixelFamily),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, fontFamily = GeistPixelFamily),
    titleSmall = materialDefaults.titleSmall.withGeistPixel(),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, fontFamily = GeistPixelFamily),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, fontFamily = GeistPixelFamily),
    bodySmall = materialDefaults.bodySmall.withGeistPixel(),
    labelLarge = materialDefaults.labelLarge.withGeistPixel(),
    labelMedium = materialDefaults.labelMedium.withGeistPixel(),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, fontFamily = GeistPixelFamily)
)
