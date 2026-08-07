package com.harmonic.player.ui.playlists

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.harmonic.player.data.Playlist
import com.harmonic.player.data.PlaylistImportExport
import com.harmonic.player.data.PlaylistSongCrossRef
import com.harmonic.player.data.SongDao
import com.harmonic.player.ui.common.ActionSheet
import com.harmonic.player.ui.common.ActionSheetItem
import com.harmonic.player.ui.common.SortMenuButton
import com.harmonic.player.ui.common.SortOption
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val playlistSortOptions = listOf(
    SortOption("createdAt", "Data adicionada"),
    SortOption("modifiedAt", "Modificada")
)

/**
 * Equivalente a um padding vertical negativo (deixa a linha mais compacta),
 * sem usar Modifier.padding() com valor negativo — o Compose passou a
 * rejeitar isso em tempo de execução (`IllegalArgumentException: Padding
 * must be non-negative`).
 */
private fun Modifier.compactVertical(amount: androidx.compose.ui.unit.Dp): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val reduceBy = (amount.roundToPx() * 2).coerceIn(0, placeable.height)
        layout(placeable.width, placeable.height - reduceBy) {
            placeable.placeRelative(0, -amount.roundToPx())
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    dao: SongDao,
    playerController: com.harmonic.player.playback.PlayerController,
    onBack: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenNowPlaying: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val playlists by dao.getPlaylists().collectAsState(initial = emptyList())
    var showCreateDialog by remember { mutableStateOf(false) }
    // Qual playlist está com um diálogo de renomear/excluir aberto — o menu
    // de opções em si agora vive dentro de cada linha (perto do botão "⋮"),
    // então esse estado só precisa cobrir os diálogos de continuação.
    var playlistForDialog by remember { mutableStateOf<Playlist?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var sortKey by remember { mutableStateOf("createdAt") }
    var sortAscending by remember { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }

    // Importar cria a playlist na hora (usando o nome do próprio arquivo
    // .m3u) e já entra com as músicas encontradas — sem precisar criar uma
    // playlist vazia à mão primeiro pra depois importar dentro dela.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val fileName = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Playlist importada"
                val paths = PlaylistImportExport.parseM3U(context, uri)
                val allSongs = dao.getAllSongs().first()
                val matched = allSongs.filter { it.path in paths }
                val newId = dao.insertPlaylist(Playlist(name = fileName))
                matched.forEachIndexed { index, song ->
                    dao.addToPlaylist(PlaylistSongCrossRef(newId, song.id, index))
                }
                onOpenPlaylist(newId)
            }
        }
    }

    val sortedPlaylists = remember(playlists, sortKey, sortAscending) {
        // Favoritas sempre no topo, dentro do grupo aplica a ordenação escolhida.
        val base = when (sortKey) {
            "modifiedAt" -> playlists.sortedBy { it.modifiedAt }
            else -> playlists.sortedBy { it.createdAt }
        }
        val ordered = if (sortAscending) base else base.reversed()
        ordered.sortedByDescending { it.isFavorite }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Playlists", color = MaterialTheme.colorScheme.primary)
                        Text(
                            "${playlists.size} playlist(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    SortMenuButton(
                        options = playlistSortOptions,
                        selectedKey = sortKey,
                        ascending = sortAscending,
                        onSelect = { sortKey = it },
                        onToggleDirection = { sortAscending = !sortAscending }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { showFabMenu = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Nova playlist")
                }
                ActionSheet(
                    expanded = showFabMenu,
                    onDismiss = { showFabMenu = false },
                    title = "Playlist",
                    items = listOf(
                        ActionSheetItem(Icons.Filled.Add, "Nova playlist") {
                            showFabMenu = false
                            showCreateDialog = true
                        },
                        ActionSheetItem(Icons.Filled.FileDownload, "Importar de M3U") {
                            showFabMenu = false
                            importLauncher.launch("audio/*")
                        }
                    )
                )
            }
        }
    ) { padding ->
        if (playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("Nenhuma playlist ainda. Toque em + pra criar a primeira.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(sortedPlaylists, key = { it.id }) { playlist ->
                    // Estado do menu vive aqui, na própria linha, pra o menu
                    // suspenso abrir colado no botão "⋮" que o aciona, em vez
                    // de flutuar solto no meio da tela.
                    var showMenu by remember { mutableStateOf(false) }

                    ListItem(
                        leadingContent = { Icon(Icons.Filled.QueueMusic, contentDescription = null) },
                        headlineContent = { Text(playlist.name) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.compactVertical(3.dp).clickable { onOpenPlaylist(playlist.id) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = {
                                    scope.launch {
                                        val songs = dao.getPlaylistSongs(playlist.id).first()
                                        if (songs.isNotEmpty()) {
                                            playerController.requestPlayQueue(songs, 0, "playlist:${playlist.id}", playlist.name)
                                            onOpenNowPlaying()
                                        }
                                    }
                                }) {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = "Tocar playlist",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = {
                                    scope.launch { dao.setPlaylistFavorite(playlist.id, !playlist.isFavorite) }
                                }) {
                                    Icon(
                                        if (playlist.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        contentDescription = "Favoritar",
                                        tint = if (playlist.isFavorite) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                                    )
                                }
                                Box {
                                    IconButton(onClick = { showMenu = true }) {
                                        Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções")
                                    }
                                    ActionSheet(
                                        expanded = showMenu,
                                        onDismiss = { showMenu = false },
                                        title = playlist.name,
                                        items = listOf(
                                            ActionSheetItem(Icons.Filled.Edit, "Renomear") {
                                                showMenu = false
                                                playlistForDialog = playlist
                                                showRenameDialog = true
                                            },
                                            ActionSheetItem(
                                                Icons.Filled.Delete, "Excluir",
                                                tint = com.harmonic.player.ui.common.DangerColor
                                            ) {
                                                showMenu = false
                                                playlistForDialog = playlist
                                                showDeleteConfirm = true
                                            }
                                        )
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Nova playlist") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("Nome da playlist") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        scope.launch {
                            dao.insertPlaylist(Playlist(name = newName.trim()))
                        }
                        showCreateDialog = false
                    }
                ) { Text("Criar") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancelar") }
            }
        )
    }

    playlistForDialog?.let { playlist ->
        if (showRenameDialog) {
            var newName by remember { mutableStateOf(playlist.name) }
            AlertDialog(
                onDismissRequest = { showRenameDialog = false; playlistForDialog = null },
                title = { Text("Renomear playlist") },
                text = {
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true)
                },
                confirmButton = {
                    TextButton(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            scope.launch { dao.renamePlaylist(playlist.id, newName.trim()) }
                            showRenameDialog = false
                            playlistForDialog = null
                        }
                    ) { Text("Salvar") }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false; playlistForDialog = null }) { Text("Cancelar") }
                }
            )
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false; playlistForDialog = null },
                title = { Text("Excluir playlist?") },
                text = { Text("\"${playlist.name}\" será excluída. As músicas continuam na sua biblioteca.") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch { dao.deletePlaylist(playlist.id) }
                        showDeleteConfirm = false
                        playlistForDialog = null
                    }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false; playlistForDialog = null }) { Text("Cancelar") }
                }
            )
        }
    }
}
