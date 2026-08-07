package com.harmonic.player.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harmonic.player.data.MusicDatabase
import kotlinx.coroutines.launch

/**
 * Lista as músicas ocultadas individualmente (menu "Ocultar música"/
 * "Ocultar álbum") com um botão pra reexibir cada uma — sem essa tela, uma
 * música escondida por engano ficava impossível de recuperar, já que ela
 * some de toda a biblioteca (Músicas, Artistas, Álbuns...) e não tem mais
 * nenhum jeito de encontrá-la de novo pra desfazer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenSongsScreen(database: MusicDatabase, onBack: () -> Unit) {
    val dao = database.songDao()
    val scope = rememberCoroutineScope()
    val hiddenSongs by dao.getHiddenSongs().collectAsState(initial = emptyList())

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Músicas ocultas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (hiddenSongs.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("Nenhuma música oculta no momento", color = Color.White.copy(alpha = 0.6f))
            }
            return@Scaffold
        }

        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(hiddenSongs, key = { it.id }) { song ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        com.harmonic.player.ui.common.AlbumArt(
                            song = song,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                        )
                    },
                    headlineContent = {
                        Text(song.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(song.artist, color = Color.White.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    trailingContent = {
                        TextButton(onClick = { scope.launch { dao.setSongHidden(song.id, false) } }) {
                            Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reexibir")
                        }
                    }
                )
            }
        }
        com.harmonic.player.ui.common.FastScrollbar(
            listState = listState,
            itemCount = hiddenSongs.size,
            modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd)
        )
        }
    }
}
