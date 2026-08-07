package com.harmonic.player.ui.common

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.harmonic.player.data.AlbumArtLoader
import com.harmonic.player.data.Song

/**
 * Cache simples em memória (LRU) pra não recarregar a mesma capa toda vez
 * que a lista rola pra cima e pra baixo — carregar bitmap é uma operação de
 * disco relativamente cara, e sem cache o scroll ficaria com engasgos.
 */
private val albumArtCache = object : LinkedHashMap<Long, Bitmap?>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Bitmap?>?): Boolean = size > 200
}

/**
 * Mostra a capa real do álbum (embutida no arquivo de áudio) ou, se a
 * música não tiver capa, um ícone discreto de nota musical sobre fundo
 * transparente — deixando o papel de parede/gradiente do app aparecer por
 * trás, em vez de uma caixa cinza sólida quebrando a continuidade visual.
 * Quando vazio, agora desenha uma linha fina marcando os limites da caixa
 * (antes ficava só transparente, sem nenhum contorno) — vale pra qualquer
 * lugar que use este componente (lista, mini player, etc.); a tela "Agora
 * Tocando" tem seu próprio vinil e não usa este composable.
 */
@Composable
fun AlbumArt(
    song: Song?,
    modifier: Modifier = Modifier,
    placeholderShape: Shape = RoundedCornerShape(6.dp)
) {
    val context = LocalContext.current

    val bitmap by produceState<Bitmap?>(initialValue = song?.id?.let { albumArtCache[it] }, key1 = song?.id) {
        if (song == null) {
            value = null
            return@produceState
        }
        val cached = albumArtCache[song.id]
        if (cached != null) {
            value = cached
            return@produceState
        }
        val loaded = AlbumArtLoader.load(context, song)
        albumArtCache[song.id] = loaded
        value = loaded
    }

    Box(
        modifier = if (bitmap == null) {
            modifier
                .background(Color.White.copy(alpha = 0.08f), placeholderShape)
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)), placeholderShape)
        } else modifier,
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxSize(0.4f)
            )
        }
    }
}
