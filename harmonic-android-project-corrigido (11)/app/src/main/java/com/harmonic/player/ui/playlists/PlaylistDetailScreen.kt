package com.harmonic.player.ui.playlists

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import com.harmonic.player.data.PlaylistImportExport
import com.harmonic.player.data.PlaylistSongCrossRef
import com.harmonic.player.data.Song
import com.harmonic.player.data.SongDao
import com.harmonic.player.playback.PlayerController
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    dao: SongDao,
    context: Context,
    playerController: PlayerController,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val songs by dao.getPlaylistSongs(playlistId).collectAsState(initial = emptyList())
    val playlists by dao.getPlaylists().collectAsState(initial = emptyList())
    val playlistName = playlists.find { it.id == playlistId }?.name ?: "Playlist"
    val playbackState by playerController.uiState.collectAsState()
    val sourceKey = "playlist:$playlistId"
    val isThisPlaylistActive = playbackState.sourceKey == sourceKey

    var showAddSongsDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    // Fora do modo de edição, a lista fica limpa (igual a página de
    // Músicas) — os ícones de arrastar/remover só aparecem depois de tocar
    // em "Editar playlist" no menu, pra não poluir a lista à toa.
    var editMode by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val paths = PlaylistImportExport.parseM3U(context, uri)
                val allSongs = dao.getAllSongs().first()
                val matched = allSongs.filter { it.path in paths }
                var position = songs.size
                matched.forEach { song ->
                    dao.addToPlaylist(PlaylistSongCrossRef(playlistId, song.id, position))
                    position++
                }
                dao.touchPlaylist(playlistId)
            }
        }
    }

    fun play(list: List<Song>, index: Int) {
        playerController.requestPlayQueue(list, index, sourceKey, playlistName)
        onOpenNowPlaying()
    }

    Box(modifier = Modifier.fillMaxSize()) {
    // Antes o Scaffold principal (com o nome da playlist no topo) ficava
    // sempre composto, mesmo com a busca de "adicionar músicas" aberta por
    // cima — como os dois têm fundo transparente (pra deixar o gradiente
    // do tema aparecer), o nome da playlist "vazava" por trás do texto de
    // busca, sobrepondo os dois. Agora só um dos dois é composto por vez.
    if (!showAddSongsDialog) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(playlistName, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddSongsDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Adicionar músicas", tint = Color.White)
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções", tint = Color.White)
                    }
                    com.harmonic.player.ui.common.ThemedDropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        val onAccent = MaterialTheme.colorScheme.onSurface
                        DropdownMenuItem(
                            text = { Text(if (editMode) "Concluir edição" else "Editar playlist") },
                            leadingIcon = { Icon(if (editMode) Icons.Filled.Check else Icons.Filled.Edit, contentDescription = null) },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            modifier = Modifier.heightIn(min = 40.dp),
                            onClick = {
                                menuExpanded = false
                                editMode = !editMode
                            }
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = onAccent.copy(alpha = 0.12f))
                        DropdownMenuItem(
                            text = { Text("Exportar como M3U") },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            modifier = Modifier.heightIn(min = 40.dp),
                            onClick = {
                                menuExpanded = false
                                val uri = PlaylistImportExport.exportToM3U(context, playlistName, songs)
                                PlaylistImportExport.shareM3U(context, uri, playlistName)
                            }
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = onAccent.copy(alpha = 0.12f))
                        DropdownMenuItem(
                            text = { Text("Importar de M3U") },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            modifier = Modifier.heightIn(min = 40.dp),
                            onClick = {
                                menuExpanded = false
                                importLauncher.launch("audio/*")
                            }
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = onAccent.copy(alpha = 0.12f))
                        DropdownMenuItem(
                            text = { Text("Excluir playlist") },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            modifier = Modifier.heightIn(min = 40.dp),
                            onClick = {
                                menuExpanded = false
                                scope.launch { dao.deletePlaylist(playlistId) }
                                onBack()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (songs.isNotEmpty()) {
                // Tocar/shuffle/repetir tudo — o "repetir" aqui só faz
                // sentido enquanto a PLAYLIST é o que está tocando; se o
                // usuário trocar pra outro álbum/artista, o repetir dessa
                // playlist não se aplica mais (é resetado, com aviso).
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { play(songs, 0) }) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Tocar",
                            tint = if (isThisPlaylistActive && playbackState.isPlaying) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f)
                        )
                    }
                    IconButton(onClick = {
                        playerController.requestPlayQueueShuffled(songs, sourceKey, playlistName)
                        onOpenNowPlaying()
                    }) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = "Aleatório",
                            tint = if (isThisPlaylistActive && playbackState.shuffleEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f)
                        )
                    }
                    IconButton(onClick = {
                        if (!isThisPlaylistActive) {
                            // Ainda não é essa playlist que tá tocando —
                            // começa a tocar ela primeiro, já com repetir ligado.
                            playerController.requestPlayQueue(songs, 0, sourceKey, playlistName)
                            playerController.cycleRepeatMode()
                        } else {
                            playerController.cycleRepeatMode()
                        }
                    }) {
                        Icon(
                            if (isThisPlaylistActive && playbackState.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                            contentDescription = "Repetir playlist",
                            tint = if (isThisPlaylistActive && playbackState.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            if (songs.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Playlist vazia. Toque em + pra adicionar músicas.", color = Color.White.copy(alpha = 0.6f))
                }
            } else {
                // Lista arrastável: segure o ícone de arrastar (⠿) e mova
                // pra cima/baixo pra reordenar. Não é LazyColumn de
                // propósito — com a virtualização da lazy, itens saindo de
                // tela durante o arraste complicam bastante o cálculo de
                // posição; como playlists raramente têm milhares de
                // músicas, uma Column simples com scroll já basta.
                var localOrder by remember(songs) { mutableStateOf(songs) }
                var draggingIndex by remember { mutableStateOf(-1) }
                var dragOffsetY by remember { mutableStateOf(0f) }
                val density = androidx.compose.ui.platform.LocalDensity.current
                val rowHeightPx = with(density) { 64.dp.toPx() }
                val playlistScrollState = androidx.compose.foundation.rememberScrollState()

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(playlistScrollState)
                ) {
                    localOrder.forEachIndexed { index, song ->
                        val isDragging = index == draggingIndex
                        val isCurrentlyPlaying = isThisPlaylistActive && playbackState.currentSong?.id == song.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .graphicsLayer { translationY = if (isDragging) dragOffsetY else 0f }
                                .zIndex(if (isDragging) 1f else 0f)
                                .background(if (isDragging) Color.White.copy(alpha = 0.06f) else Color.Transparent)
                                .clickable { play(localOrder, index) }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // O ícone de arrastar (reordenar) só aparece no
                            // modo de edição — fora dele, a lista fica igual
                            // à página de Músicas, sem esses ícones extras.
                            if (editMode) {
                                Icon(
                                    Icons.Filled.DragHandle,
                                    contentDescription = "Arrastar pra reordenar",
                                    tint = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .pointerInput(song.id) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    draggingIndex = localOrder.indexOf(song)
                                                    dragOffsetY = 0f
                                                },
                                                onDragEnd = {
                                                    draggingIndex = -1
                                                    dragOffsetY = 0f
                                                    scope.launch { dao.updatePlaylistOrder(playlistId, localOrder.map { it.id }) }
                                                },
                                                onDragCancel = { draggingIndex = -1; dragOffsetY = 0f }
                                            ) { change, delta ->
                                                change.consume()
                                                dragOffsetY += delta.y
                                                val current = draggingIndex
                                                if (current == -1) return@detectDragGesturesAfterLongPress
                                                val steps = (dragOffsetY / rowHeightPx).roundToInt()
                                                val targetIndex = (current + steps).coerceIn(0, localOrder.lastIndex)
                                                if (targetIndex != current) {
                                                    val mutable = localOrder.toMutableList()
                                                    val moved = mutable.removeAt(current)
                                                    mutable.add(targetIndex, moved)
                                                    localOrder = mutable
                                                    dragOffsetY -= (targetIndex - current) * rowHeightPx
                                                    draggingIndex = targetIndex
                                                }
                                            }
                                        }
                                )
                            }
                            com.harmonic.player.ui.common.AlbumArt(
                                song = song,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
                                Text(
                                    song.artist,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Color.White.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            // Indicador de "tocando agora" — igual à página
                            // de Músicas, pra dar consistência visual.
                            if (isCurrentlyPlaying && playbackState.isPlaying) {
                                com.harmonic.player.ui.library.AnimatedEqualizerBars(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            IconButton(onClick = { scope.launch { dao.setFavorite(song.id, !song.isFavorite) } }) {
                                Icon(
                                    if (song.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = "Favoritar",
                                    tint = if (song.isFavorite) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)
                                )
                            }
                            // O botão de remover também só aparece no modo
                            // de edição — evita remover uma música sem
                            // querer com um toque solto na lista normal.
                            if (editMode) {
                                IconButton(onClick = {
                                    scope.launch { dao.removeFromPlaylist(playlistId, song.id) }
                                }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remover da playlist", tint = Color.White.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                }
                com.harmonic.player.ui.common.FastScrollbarPlain(
                    scrollState = playlistScrollState,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
                }
            }
        }
    }
    }

    if (showAddSongsDialog) {
        AddSongsFullScreen(
            dao = dao,
            alreadyInPlaylist = songs.map { it.id }.toSet(),
            onBack = { showAddSongsDialog = false },
            onConfirm = { selected ->
                scope.launch {
                    var position = songs.size
                    selected.forEach { song ->
                        dao.addToPlaylist(PlaylistSongCrossRef(playlistId, song.id, position))
                        position++
                    }
                    dao.touchPlaylist(playlistId)
                }
                showAddSongsDialog = false
            }
        )
    }
    }
}

/**
 * Página cheia (não uma caixa de diálogo) pra escolher quais músicas
 * entram na playlist — visual parecido com a página de Músicas, com busca
 * no topo e checkbox em cada linha, e uma barra inferior com o botão de
 * confirmar já mostrando quantas foram selecionadas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSongsFullScreen(
    dao: SongDao,
    alreadyInPlaylist: Set<Long>,
    onBack: () -> Unit,
    onConfirm: (List<Song>) -> Unit
) {
    val allSongs by dao.getAllSongs().collectAsState(initial = emptyList())
    val selected = remember { mutableStateListOf<Long>() }
    var query by remember { mutableStateOf("") }

    val filtered = remember(allSongs, query) {
        allSongs.filter {
            it.id !in alreadyInPlaylist &&
                (query.isBlank() || it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true))
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Buscar músicas, artistas...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Cancelar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            if (selected.isNotEmpty()) {
                Surface(color = Color.Transparent) {
                    Button(
                        onClick = { onConfirm(allSongs.filter { it.id in selected }) },
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Text("Adicionar ${selected.size} música${if (selected.size == 1) "" else "s"}")
                    }
                }
            }
        }
    ) { padding ->
        if (filtered.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (allSongs.isEmpty()) "Nenhuma música na biblioteca ainda" else "Todas as músicas já estão nessa playlist (ou nenhuma bate com a busca)",
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(filtered, key = { it.id }) { song ->
                val isSelected = song.id in selected
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        com.harmonic.player.ui.common.AlbumArt(
                            song = song,
                            modifier = Modifier.size(44.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        )
                    },
                    headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White) },
                    supportingContent = { Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White.copy(alpha = 0.6f)) },
                    trailingContent = {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                if (checked) selected.add(song.id) else selected.remove(song.id)
                            }
                        )
                    },
                    modifier = Modifier.clickable {
                        if (isSelected) selected.remove(song.id) else selected.add(song.id)
                    }
                )
            }
        }
    }
}
