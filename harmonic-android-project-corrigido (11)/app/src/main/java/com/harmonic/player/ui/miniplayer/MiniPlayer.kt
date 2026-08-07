package com.harmonic.player.ui.miniplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harmonic.player.playback.PlaybackUiState

/**
 * Barra fixa com a música atual — fica visível em qualquer lugar da
 * Biblioteca, permitindo pausar/pular sem precisar abrir "Agora Tocando".
 * Toque em qualquer área fora dos botões abre a tela cheia.
 *
 * O fundo é uma vinheta radial (mais escura perto da capa, 100%
 * transparente nas bordas) em vez de um card sólido — assim ele se
 * integra com o fundo do app em vez de flutuar como uma caixa escura por
 * cima. O título usa um gradiente na direção OPOSTA à vinheta (mais claro
 * onde o fundo está mais escuro, mais sutil onde o fundo já clareou),
 * pra manter a legibilidade em vez de competir com o fundo.
 *
 * Retorna null (não desenha nada) quando não há música tocando, para não
 * ocupar espaço à toa na tela.
 */
@Composable
fun MiniPlayer(
    state: PlaybackUiState,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onStop: () -> Unit,
    onOpenNowPlaying: () -> Unit
) {
    val song = state.currentSong ?: return
    val density = LocalDensity.current

    // Gradiente do título: branco cheio à esquerda (onde a vinheta do fundo
    // é mais escura) esmaecendo pra menos opaco à direita (onde a vinheta
    // já sumiu e o fundo original do app aparece por trás) — contraste
    // sempre no sentido contrário ao da vinheta, pra continuar legível não
    // importa a cor por trás.
    // Título na cor de destaque (igual aos botões) com um brilho suave —
    // ajuda a diferenciar o título do mini player dos títulos das músicas
    // na lista logo acima, que não usam essa cor.
    val accentColor = MaterialTheme.colorScheme.primary
    val titleGlowStyle = remember(accentColor) {
        androidx.compose.ui.text.TextStyle(
            shadow = androidx.compose.ui.graphics.Shadow(
                color = accentColor.copy(alpha = 0.7f),
                offset = androidx.compose.ui.geometry.Offset.Zero,
                blurRadius = 18f
            )
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val vignetteBrush = remember(widthPx, heightPx) {
            Brush.radialGradient(
                colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Black.copy(alpha = 0f)),
                center = Offset(widthPx * 0.24f, heightPx * 0.5f),
                radius = (widthPx * 0.85f).coerceAtLeast(1f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(vignetteBrush)
                .clickable(onClick = onOpenNowPlaying)
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Capa real do álbum — cai no ícone de nota musical quando não há capa embutida.
            com.harmonic.player.ui.common.AlbumArt(
                song = song,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = accentColor,
                    style = MaterialTheme.typography.bodyLarge.merge(titleGlowStyle)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Indica que há música(s) inserida(s) manualmente na
                    // fila ("Tocar a seguir" / "Adicionar à fila") ainda
                    // não tocadas — sem esse ícone não tinha como saber
                    // isso de relance, só abrindo a tela de fila.
                    if (state.manuallyQueuedSongIds.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Filled.QueueMusic,
                            contentDescription = "Há músicas na fila",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        song.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            // Botões com no mínimo 48dp de área de toque (tamanho recomendado
            // pelo Material Design), mesmo que o ícone visual seja menor.
            // Cor de destaque + um brilho suave atrás — ajuda o mini player
            // a se destacar um pouco da lista de músicas logo acima dele.
            val accent = MaterialTheme.colorScheme.primary
            // Botão de stop agora com o mesmo tratamento visual dos outros
            // dois (glow radial atrás + tingido na cor de destaque) — antes
            // ele era menor e sem brilho, destoando dos outros dois.
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(
                            Brush.radialGradient(listOf(accent.copy(alpha = 0.25f), Color.Transparent))
                        )
                )
                IconButton(onClick = onStop, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Stop, contentDescription = "Parar", tint = accent.copy(alpha = 0.9f))
                }
            }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(
                            Brush.radialGradient(listOf(accent.copy(alpha = 0.25f), Color.Transparent))
                        )
                )
                IconButton(onClick = onSkipPrevious, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Anterior", tint = accent.copy(alpha = 0.9f))
                }
            }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            Brush.radialGradient(listOf(accent.copy(alpha = 0.4f), Color.Transparent))
                        )
                )
                IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pausar" else "Tocar",
                        tint = accent
                    )
                }
            }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(
                            Brush.radialGradient(listOf(accent.copy(alpha = 0.25f), Color.Transparent))
                        )
                )
                IconButton(onClick = onSkipNext, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Próxima", tint = accent.copy(alpha = 0.9f))
                }
            }
        }
    }
}
