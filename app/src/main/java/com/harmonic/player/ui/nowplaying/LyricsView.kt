package com.harmonic.player.ui.nowplaying

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.harmonic.player.data.LyricLine
import com.harmonic.player.data.LyricsResult
import kotlinx.coroutines.launch

/**
 * Mostra a letra da música, com a linha correspondente ao instante atual
 * destacada e centralizada automaticamente na tela — como em qualquer
 * player com letra sincronizada (Spotify, Apple Music, etc). Quando a
 * letra não é sincronizada (.txt simples) ou não existe, mostra o texto
 * corrido ou uma mensagem, sem tentar "simular" sincronismo que não existe.
 *
 * Além da linha "tocando agora" (destacada na cor de destaque), o usuário
 * pode TOCAR numa linha pra selecioná-la (fundo translúcido) — essa é a
 * linha usada pelo botão de compartilhar no topo da tela.
 */
/**
 * Sombra difusa escura por trás do texto da letra — sem isso, quando a cor
 * da letra (branca, ou a cor de destaque na linha atual) fica parecida com
 * o tom do fundo escolhido pelo usuário (tema/papel de parede/imagem da
 * galeria), o texto quase desaparece. Uma sombra suave cria contraste em
 * qualquer fundo, sem precisar adivinhar a cor de fundo certa caso a caso.
 */
private val lyricTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.75f),
    offset = Offset(0f, 1f),
    blurRadius = 10f
)

@Composable
fun LyricsView(
    lyrics: LyricsResult,
    positionMs: Long,
    selectedIndex: Int? = null,
    onLineClick: ((Int, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    when (lyrics) {
        is LyricsResult.Synced -> SyncedLyrics(lyrics.lines, positionMs, selectedIndex, onLineClick, modifier)
        is LyricsResult.PlainText -> Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
            Text(
                lyrics.text,
                style = MaterialTheme.typography.bodyLarge.copy(shadow = lyricTextShadow),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        }
        is LyricsResult.NotFound -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                "Nenhuma letra encontrada pra essa música.\n" +
                "Coloque um arquivo .lrc (sincronizado) ou .txt com o mesmo\n" +
                "nome do arquivo de áudio, na mesma pasta.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}

@Composable
private fun SyncedLyrics(
    lines: List<LyricLine>,
    positionMs: Long,
    selectedIndex: Int?,
    onLineClick: ((Int, String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Última linha cujo timestamp já passou é a linha "atual".
    val currentIndex = remember(lines, positionMs) {
        lines.indexOfLast { it.timestampMs <= positionMs }.coerceAtLeast(0)
    }

    LaunchedEffect(currentIndex) {
        scope.launch {
            // Centraliza a linha atual na tela (offset negativo empurra ela
            // pro meio, em vez de deixar colada no topo da lista).
            listState.animateScrollToItem(
                index = (currentIndex - 2).coerceAtLeast(0)
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 120.dp, horizontal = 24.dp)
    ) {
        itemsIndexed(lines) { index, line ->
            val isCurrent = index == currentIndex
            val isSelected = index == selectedIndex
            val color by animateColorAsState(
                targetValue = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
                label = "lyric_line_color"
            )
            Text(
                line.text.ifBlank { "♪" },
                style = (if (isCurrent) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge)
                    .copy(shadow = lyricTextShadow),
                color = color,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (isSelected) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                        else Modifier
                    )
                    .then(
                        if (onLineClick != null) Modifier.clickable { onLineClick(index, line.text) }
                        else Modifier
                    )
                    .padding(vertical = 8.dp)
            )
        }
    }
}
