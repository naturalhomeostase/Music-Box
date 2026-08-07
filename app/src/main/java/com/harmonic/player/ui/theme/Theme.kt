package com.harmonic.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

// Paleta nova do tema padrão "Music Box" — trocada por completo a pedido do
// usuário (a antiga causava problema atrás de problema: rosa órfão herdado
// de um tema removido, Material You do sistema disfarçado de "padrão", e
// agora o roxo de fábrica do Material 3 vazando nos containers). Em vez de
// tentar consertar remendo por remendo, a cor em si mudou: um dourado mais
// vivo e saturado (perto do metal do gramofone no ícone do app), com um
// castanho bem mais escuro/rico no modo claro pra manter contraste.
private val fallbackDarkColors = darkColorScheme(
    primary = Color(0xFFE3A63E),      // dourado vivo, inspirado no ícone (tema padrão "Music Box")
    secondary = Color(0xFFE3A63E),
    tertiary = Color(0xFFE3A63E),
    background = Color(0xFF000000),
    surface = Color(0xFF1A1A1D)
)

private val fallbackLightColors = lightColorScheme(
    primary = Color(0xFF8B5A1F),
    secondary = Color(0xFF8B5A1F),
    tertiary = Color(0xFF8B5A1F)
)

enum class ThemeMode { LIGHT, DARK, AMOLED, SYSTEM }

/**
 * Decide se um texto/ícone branco ou escuro tem mais contraste em cima da
 * cor passada — usando a fórmula de luminância relativa (a mesma ideia por
 * trás das recomendações de contraste do WCAG). Sem isso, o "+" de
 * adicionar músicas (e qualquer outro ícone/texto sobre a cor de destaque)
 * ficava branco fixo, o que sumia quando a cor de destaque escolhida era
 * clara (pastel, amarelo claro etc).
 */
private fun contrastingOn(accent: Color): Color {
    val luminance = 0.2126f * accent.red + 0.7152f * accent.green + 0.0722f * accent.blue
    return if (luminance > 0.6f) Color(0xFF1A1A1A) else Color.White
}

/**
 * Aplica UMA cor de destaque em todos os "papéis" de cor do Material3 que
 * normalmente ficariam roxos por padrão (secondary/tertiary e seus
 * containers) quando só o `primary` é customizado. Sem isso, componentes
 * como o Switch, que usam `secondary`/`tertiary` em vez de `primary` em
 * alguns estados, mostram o roxo padrão do Material Design em vez da cor
 * escolhida pelo usuário.
 */
fun ColorScheme.withSingleAccent(accent: Color): ColorScheme {
    val onAccent = contrastingOn(accent)
    return copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent,
        onPrimaryContainer = onAccent,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = accent.copy(alpha = 0.3f),
        onSecondaryContainer = onAccent,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = accent.copy(alpha = 0.3f),
        onTertiaryContainer = onAccent,
        inversePrimary = accent
    )
}

/**
 * Tema principal do Harmonic.
 *
 * Prioridade de cores:
 * 1. Se o usuário escolheu uma cor de destaque manual -> usa ela sobre o
 *    esquema claro/escuro base (funciona em qualquer versão do Android).
 * 2. Senão, se houver uma cor extraída da capa do álbum -> usa ela do
 *    mesmo jeito.
 * 3. Por fim, cai no fallback fixo definido acima (o dourado/bronze do
 *    tema padrão "Music Box").
 */
@Composable
fun HarmonicTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    customAccentColor: Color? = null,
    albumArtSeedColor: Color? = null,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> systemDark
    }

    val fallbackScheme = if (useDark) fallbackDarkColors else fallbackLightColors

    /*
     * Histórico dos bugs que passaram por aqui, pra quem for mexer depois:
     * 1) Cor rosê órfã presa no DataStore de um tema removido (Rosé) — sem
     *    relação com este arquivo, resolvido na migração do
     *    SettingsRepository.
     * 2) Material You do Android (cor tirada do papel de parede do
     *    SISTEMA) disfarçado de tema "padrão" sempre que nenhuma cor manual
     *    estava escolhida — removido; esse app não tem essa opção nas
     *    configurações, então nunca foi uma escolha real do usuário.
     * 3) Por causa da correção acima, o fallback passou a pular o
     *    `withSingleAccent` — e como `darkColorScheme(primary = ...)` só
     *    define o `primary`, todo o resto (como `primaryContainer`/
     *    `onPrimaryContainer`, usados pelo botão "+") caía nas cores de
     *    FÁBRICA do Material 3, que são roxas. Corrigido chamando
     *    `withSingleAccent` também nesse caminho, pra TODOS os papéis de
     *    cor virem da mesma cor de destaque.
     */
    val baseScheme = when {
        customAccentColor != null -> fallbackScheme.withSingleAccent(customAccentColor)
        albumArtSeedColor != null -> fallbackScheme.withSingleAccent(albumArtSeedColor)
        else -> fallbackScheme.withSingleAccent(fallbackScheme.primary)
    }

    val colorScheme = if (themeMode == ThemeMode.AMOLED) {
        baseScheme.copy(background = Color.Black, surface = Color.Black)
    } else baseScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HarmonicTypography,
        content = content
    )
}

/** Utilitário para converter uma Color do Compose num Int ARGB (usado ao salvar no DataStore). */
fun Color.toArgbInt(): Int = this.toArgb()
