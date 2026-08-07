package com.harmonic.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.harmonic.player.data.DefaultWallpaper
import com.harmonic.player.data.GradientTheme
import com.harmonic.player.data.SettingsRepository

/**
 * Desenha o fundo do app inteiro: uma imagem (padrão ou escolhida da
 * galeria) ou um gradiente (mais leve, sem decodificar nenhuma imagem —
 * por isso é o padrão de fábrica do app). Sombra e blur são ajustáveis
 * pelo usuário nas configurações de Aparência.
 *
 * Aplicado uma única vez, no topo da árvore de composição: cada tela usa
 * `containerColor = Color.Transparent` no Scaffold, e cada item de lista
 * usa fundo transparente, pra deixar esse fundo aparecer atrás de tudo.
 *
 * Nota: o blur (`Modifier.blur`) só tem efeito real no Android 12+ (API 31+)
 * — em aparelhos mais antigos a chamada não quebra nada, só não borra a
 * imagem, já que depende do RenderEffect do sistema.
 */
@Composable
fun AppBackground(settings: SettingsRepository, content: @Composable () -> Unit) {
    val defaultWallpaperName by settings.defaultWallpaper.collectAsState(initial = null)
    val customBackgroundUri by settings.backgroundUri.collectAsState(initial = null)
    val gradientThemeName by settings.gradientTheme.collectAsState(initial = null)
    val blurRadius by settings.backgroundBlurRadius.collectAsState(initial = 0)
    val scrimAlphaPercent by settings.backgroundScrimAlpha.collectAsState(initial = 45)

    Box(modifier = Modifier.fillMaxSize()) {
        val imageModel: Any? = when {
            customBackgroundUri != null -> customBackgroundUri
            defaultWallpaperName != null ->
                DefaultWallpaper.values().find { it.name == defaultWallpaperName }
                    ?.let { "file:///android_asset/${it.assetPath}" }
            else -> null
        }

        if (imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (blurRadius > 0) Modifier.blur(blurRadius.dp) else Modifier)
            )
        } else {
            // Sem imagem escolhida: gradiente como padrão (mais leve — sem
            // decodificar JPEG nenhum). Usa o tema salvo, ou "Meia-noite"
            // se o usuário nunca mexeu nisso.
            val theme = GradientTheme.values().find { it.name == gradientThemeName } ?: GradientTheme.APP_ICON
            val colors = theme.colorsArgb.map { Color(it) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(colors))
                    .then(if (blurRadius > 0) Modifier.blur(blurRadius.dp) else Modifier)
            )
        }

        // Véu escuro ajustável pra garantir legibilidade do texto sobre o fundo
        if (scrimAlphaPercent > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlphaPercent / 100f))
            )
        }

        content()
    }
}
