package com.harmonic.player.ui.nowplaying

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.harmonic.player.data.AlbumArtLoader
import com.harmonic.player.data.SongDao
import com.harmonic.player.playback.PlayerController
import com.harmonic.player.ui.theme.withSingleAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playerController: PlayerController,
    dao: SongDao,
    settings: com.harmonic.player.data.SettingsRepository,
    onBack: () -> Unit,
    onOpenEqualizer: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val state by playerController.uiState.collectAsState()
    var sliderPosition by remember { mutableStateOf(0f) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var selectedLyricIndex by remember { mutableStateOf<Int?>(null) }
    val coverDisplayMode by settings.coverDisplayMode.collectAsState(initial = "VINYL")
    var lyricsResult by remember { mutableStateOf<com.harmonic.player.data.LyricsResult>(com.harmonic.player.data.LyricsResult.NotFound) }
    val playbackSpeed by settings.playbackSpeed.collectAsState(initial = 1f)

    // Bitmap da capa da música atual — usado em três lugares: fundo desfocado,
    // arte dentro do vinil giratório, e extração de cor (Palette) pra pintar
    // o resto da tela com uma cor que combine com a música tocando.
    val currentSong = state.currentSong
    val albumBitmap by produceState<Bitmap?>(initialValue = null, key1 = currentSong?.id) {
        value = currentSong?.let { AlbumArtLoader.load(context, it) }
    }

    val extractedColor by produceState<Color?>(initialValue = null, key1 = albumBitmap) {
        value = albumBitmap?.let { bmp ->
            withContext(Dispatchers.Default) {
                try {
                    val palette = androidx.palette.graphics.Palette.from(bmp).generate()
                    val swatch = palette.vibrantSwatch ?: palette.lightVibrantSwatch
                        ?: palette.dominantSwatch ?: palette.mutedSwatch
                    swatch?.let { Color(it.rgb) }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
    // Clareia um pouco cores extraídas escuras demais, senão o texto/ícones
    // que dependem dela ficam ilegíveis sobre o fundo também escuro.
    val pageAccent = (extractedColor ?: MaterialTheme.colorScheme.primary).let { color ->
        if (color.luminance() < 0.35f) androidx.compose.ui.graphics.lerp(color, Color.White, 0.35f) else color
    }
    val onPageAccent = if (pageAccent.luminance() > 0.6f) Color(0xFF1A1A1A) else Color.White

    // Recarrega a letra sempre que a música atual mudar. A leitura do
    // arquivo .lrc/.txt é rápida, mas ainda assim roda fora da thread
    // principal pra nunca travar a UI.
    LaunchedEffect(state.currentSong?.id) {
        val song = state.currentSong
        lyricsResult = if (song != null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.harmonic.player.data.LyricsRepository.load(context, song)
            }
        } else {
            com.harmonic.player.data.LyricsResult.NotFound
        }
    }

    // Garante que a barra já abra na posição correta mesmo se a música
    // estiver pausada (antes, só atualizava dentro do loop de "tocando").
    LaunchedEffect(Unit) {
        sliderPosition = playerController.currentPositionMs().toFloat()
    }

    // Atualiza a posição da barra de progresso a cada 500ms enquanto toca
    LaunchedEffect(state.isPlaying) {
        while (state.isPlaying) {
            if (!isUserSeeking) sliderPosition = playerController.currentPositionMs().toFloat()
            delay(500)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Fundo desfocado com a própria capa da música — só aparece quando
        // a música tem capa de verdade; sem capa, o fundo padrão do app
        // (imagem/gradiente escolhido em Aparência) continua por trás,
        // porque o Scaffold logo abaixo é totalmente transparente.
        albumBitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(60.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            )
        }

        // A cor de destaque desta tela (título, slider, botão de play,
        // ícones ativos...) passa a vir da própria capa em vez da cor de
        // destaque fixa do app — só aqui, o resto do app continua igual.
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.withSingleAccent(pageAccent),
            typography = MaterialTheme.typography
        ) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Voltar", tint = Color.White.copy(alpha = 0.9f))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val next = when (coverDisplayMode) {
                            "VINYL" -> "STATIC"
                            "STATIC" -> "FULLSCREEN"
                            else -> "VINYL"
                        }
                        scope.launch { settings.setCoverDisplayMode(next) }
                    }) {
                        Icon(
                            when (coverDisplayMode) {
                                "STATIC" -> Icons.Filled.CropSquare
                                "FULLSCREEN" -> Icons.Filled.Fullscreen
                                else -> Icons.Filled.Album
                            },
                            contentDescription = "Modo de exibição da capa",
                            tint = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    IconButton(onClick = { showLyrics = !showLyrics }) {
                        Icon(
                            Icons.Filled.Subject,
                            contentDescription = if (showLyrics) "Mostrar capa" else "Mostrar letra",
                            tint = if (showLyrics) pageAccent else Color.White.copy(alpha = 0.85f)
                        )
                    }
                    if (showLyrics && lyricsResult is com.harmonic.player.data.LyricsResult.Synced) {
                        IconButton(onClick = {
                            val song = state.currentSong
                            val lines = (lyricsResult as com.harmonic.player.data.LyricsResult.Synced).lines
                            val currentIdx = lines.indexOfLast { it.timestampMs <= sliderPosition.toLong() }.coerceAtLeast(0)
                            val lineText = selectedLyricIndex?.let { lines.getOrNull(it)?.text }
                                ?: lines.getOrNull(currentIdx)?.text
                            if (song != null && !lineText.isNullOrBlank()) {
                                scope.launch {
                                    val uri = LyricShareImage.generate(
                                        context = context,
                                        albumArt = albumBitmap,
                                        lyricText = lineText,
                                        songTitle = song.title,
                                        artist = song.artist
                                    )
                                    if (uri != null) {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "image/png"
                                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(intent, "Compartilhar letra"))
                                    }
                                }
                            }
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Compartilhar letra", tint = Color.White.copy(alpha = 0.85f))
                        }
                    }
                    IconButton(onClick = { showSleepTimerDialog = true }) {
                        Icon(
                            Icons.Filled.Bedtime,
                            contentDescription = "Sleep timer",
                            tint = if (state.sleepTimerEndAt != null) pageAccent
                                   else Color.White.copy(alpha = 0.85f)
                        )
                    }
                    if (showSleepTimerDialog) {
                        // O diálogo existia como composable, mas nunca era
                        // efetivamente chamado em lugar nenhum — por isso o
                        // ícone parecia "não fazer nada" ao tocar.
                        SleepTimerDialog(
                            currentEndAt = state.sleepTimerEndAt,
                            onDismiss = { showSleepTimerDialog = false },
                            onSelectMinutes = { minutes ->
                                playerController.startSleepTimer(minutes)
                                showSleepTimerDialog = false
                            },
                            onSelectEndOfSong = {
                                playerController.stopAtEndOfSong()
                                showSleepTimerDialog = false
                            },
                            onCancel = {
                                playerController.cancelSleepTimer()
                                showSleepTimerDialog = false
                            }
                        )
                    }
                    IconButton(onClick = onOpenEqualizer) {
                        Icon(Icons.Filled.Equalizer, contentDescription = "Equalizador", tint = Color.White.copy(alpha = 0.85f))
                    }
                    // Botão discreto de velocidade: cada toque avança pro
                    // próximo valor do ciclo (sem precisar abrir Configurações
                    // toda vez que a pessoa quer ouvir mais rápido/devagar).
                    // Mesmo estilo do "A-B" logo abaixo — um texto pequeno no
                    // lugar de um ícone, já que não tem ícone padrão pra isso.
                    IconButton(onClick = {
                        val next = nextPlaybackSpeed(playbackSpeed)
                        playerController.setPlaybackSpeed(next)
                        scope.launch { settings.setPlaybackSpeed(next) }
                    }) {
                        Text(
                            text = formatPlaybackSpeed(playbackSpeed),
                            color = if (playbackSpeed != 1f) pageAccent else Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    // A-B repeat: 1º toque marca o ponto A, 2º marca o B (e
                    // já começa a repetir esse trecho), 3º desliga. Sem
                    // ícone padrão do Material pra isso — um texto pequeno
                    // "A-B" já deixa claro o que é, sem precisar de legenda.
                    IconButton(onClick = {
                        when {
                            state.pointA == null -> playerController.setPointA()
                            state.pointB == null -> playerController.setPointB()
                            else -> playerController.clearABRepeat()
                        }
                    }) {
                        Text(
                            text = if (state.pointB != null) "AB" else if (state.pointA != null) "A…" else "A-B",
                            color = if (state.pointA != null) pageAccent else Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = { showBookmarksDialog = true }) {
                        Icon(Icons.Filled.Bookmark, contentDescription = "Marcadores", tint = Color.White.copy(alpha = 0.85f))
                    }
                    IconButton(onClick = { showQueueSheet = true }) {
                        Icon(Icons.Filled.QueueMusic, contentDescription = "Fila de reprodução", tint = Color.White.copy(alpha = 0.85f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Alterna entre a capa do álbum e a letra sincronizada, conforme
            // o botão na barra superior. A letra usa o espaço restante da
            // tela (weight), a capa mantém proporção quadrada.
            if (showLyrics) {
                LyricsView(
                    lyrics = lyricsResult,
                    positionMs = sliderPosition.toLong(),
                    selectedIndex = selectedLyricIndex,
                    onLineClick = { index, _ -> selectedLyricIndex = if (selectedLyricIndex == index) null else index },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            } else {
                when (coverDisplayMode) {
                    "STATIC" -> com.harmonic.player.ui.common.AlbumArt(
                        song = state.currentSong,
                        modifier = Modifier
                            .fillMaxWidth(0.86f)
                            .aspectRatio(1f)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    )
                    "FULLSCREEN" -> com.harmonic.player.ui.common.AlbumArt(
                        song = state.currentSong,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    )
                    else -> VinylRecord(
                        bitmap = albumBitmap,
                        isPlaying = state.isPlaying,
                        modifier = Modifier
                            .fillMaxWidth(0.86f)
                            .aspectRatio(1f)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    state.currentSong?.title ?: "Nada tocando",
                    style = MaterialTheme.typography.titleLarge.copy(
                        // Mesmo truque da letra da música (LyricsView): em vez
                        // de tentar adivinhar/trocar a cor quando ela fica
                        // parecida com o fundo, uma sombra escura difusa por
                        // trás cria contraste em qualquer combinação de cores,
                        // sem precisar mudar a cor do tema.
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black.copy(alpha = 0.75f),
                            offset = androidx.compose.ui.geometry.Offset(0f, 1f),
                            blurRadius = 10f
                        )
                    ),
                    color = pageAccent,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                state.currentSong?.let { song ->
                    IconButton(onClick = {
                        scope.launch {
                            val newValue = !song.isFavorite
                            dao.setFavorite(song.id, newValue)
                            playerController.updateSongFavoriteInMemory(song.id, newValue)
                        }
                    }) {
                        Icon(
                            imageVector = if (song.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (song.isFavorite) "Remover dos favoritos" else "Favoritar",
                            tint = if (song.isFavorite) pageAccent else Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            Text(
                state.currentSong?.artist ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f)
            )
            state.currentSong?.composer?.takeIf { it.isNotBlank() }?.let { composer ->
                Text(
                    "Compositor: $composer",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            // Info técnica: bitrate, formato, frequência, tamanho do arquivo
            state.currentSong?.let { song ->
                Spacer(Modifier.height(4.dp))
                Text(
                    "${song.format} • ${song.bitrate?.let { "${it / 1000} kbps" } ?: ""} • ${formatFileSize(song.sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }

            Spacer(Modifier.height(16.dp))

            Slider(
                value = sliderPosition,
                onValueChange = { isUserSeeking = true; sliderPosition = it },
                onValueChangeFinished = {
                    playerController.seekTo(sliderPosition.toLong())
                    isUserSeeking = false
                },
                valueRange = 0f..(state.durationMs.coerceAtLeast(1)).toFloat(),
                // A trilha "andada" (mais grossa) já usa a cor do tema via
                // MaterialTheme.colorScheme.primary (== pageAccent) por
                // padrão. Mas a trilha "restante" (mais fina) não tinha cor
                // customizada e caía no cinza padrão do Material3
                // (surfaceVariant) — destoando do resto da tela, que segue
                // a cor extraída da capa. Agora ela usa essa MESMA cor,
                // só bem mais discreta (baixa opacidade).
                colors = SliderDefaults.colors(
                    thumbColor = pageAccent,
                    activeTrackColor = pageAccent,
                    inactiveTrackColor = pageAccent.copy(alpha = 0.28f)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    formatDuration(sliderPosition.toLong()),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f)
                )
                Text(
                    formatDuration(state.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val repeatModeIsOne = state.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE
                IconButton(
                    onClick = { playerController.setShuffle(!state.shuffleEnabled) },
                    enabled = !repeatModeIsOne
                ) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "Aleatório",
                        tint = when {
                            repeatModeIsOne -> Color.White.copy(alpha = 0.25f)
                            state.shuffleEnabled -> pageAccent
                            else -> Color.White.copy(alpha = 0.85f)
                        }
                    )
                }
                IconButton(onClick = { playerController.skipPrevious() }) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "Anterior",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(40.dp)
                    )
                }
                FilledIconButton(
                    onClick = { playerController.togglePlayPause() },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = pageAccent,
                        contentColor = onPageAccent
                    ),
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(onClick = { playerController.skipNext() }) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "Próxima",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(onClick = { playerController.cycleRepeatMode() }) {
                    Icon(
                        if (repeatModeIsOne) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Repetir",
                        tint = if (state.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) pageAccent
                               else Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SeekChip(label = "-60s", onClick = { playerController.seekBy(-60_000) })
                SeekChip(label = "-30s", onClick = { playerController.seekBy(-30_000) })
                SeekChip(label = "-10s", onClick = { playerController.seekBy(-10_000) })
                SeekChip(label = "+10s", onClick = { playerController.seekBy(10_000) })
                SeekChip(label = "+30s", onClick = { playerController.seekBy(30_000) })
                SeekChip(label = "+60s", onClick = { playerController.seekBy(60_000) })
            }
        }
    }
    } // fim do MaterialTheme(pageAccent)
    } // fim do Box de fundo

    if (showQueueSheet) {
        QueueSheet(
            queue = state.queue,
            currentIndex = state.currentIndex,
            accent = pageAccent,
            onDismiss = { showQueueSheet = false },
            onJumpTo = { index -> playerController.skipToQueueItem(index) },
            onRemove = { index -> playerController.removeFromQueue(index) },
            onMove = { from, to -> playerController.moveQueueItem(from, to) }
        )
    }

    if (showBookmarksDialog) {
        val currentSong = state.currentSong
        if (currentSong == null) {
            showBookmarksDialog = false
        } else {
            val bookmarks by dao.getBookmarksForSong(currentSong.id).collectAsState(initial = emptyList())
            var newLabel by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showBookmarksDialog = false },
                title = { Text("Marcadores") },
                text = {
                    Column {
                        if (bookmarks.isEmpty()) {
                            Text(
                                "Nenhum marcador nessa música ainda.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        } else {
                            bookmarks.forEach { bookmark ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            playerController.seekTo(bookmark.positionMs)
                                            showBookmarksDialog = false
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val totalSeconds = bookmark.positionMs / 1000
                                    val timeLabel = "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
                                    Text(
                                        if (bookmark.label.isBlank()) timeLabel else "${bookmark.label} — $timeLabel",
                                        color = Color.White,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { scope.launch { dao.deleteBookmark(bookmark.id) } }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Excluir marcador", tint = Color.White.copy(alpha = 0.6f))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = newLabel,
                            onValueChange = { newLabel = it },
                            label = { Text("Nome do marcador (opcional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            dao.insertBookmark(
                                com.harmonic.player.data.Bookmark(
                                    songId = currentSong.id,
                                    positionMs = playerController.currentPositionMs(),
                                    label = newLabel.trim()
                                )
                            )
                            newLabel = ""
                        }
                    }) { Text("Marcar aqui") }
                },
                dismissButton = {
                    TextButton(onClick = { showBookmarksDialog = false }) { Text("Fechar") }
                }
            )
        }
    }
}

/**
 * Painel deslizante com a fila de reprodução atual — antes disso, não
 * havia NENHUMA forma de ver o que vinha a seguir, só de adicionar coisas
 * "às cegas" (Tocar em seguida / Adicionar à fila). Mostra a música atual
 * destacada, deixa arrastar pra reordenar (segura e arrasta, como na tela
 * de playlist) e tocar num "X" pra tirar uma música da fila sem afetá-la
 * no resto do app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueSheet(
    queue: List<com.harmonic.player.data.Song>,
    currentIndex: Int,
    accent: Color,
    onDismiss: () -> Unit,
    onJumpTo: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var localQueue by remember(queue) { mutableStateOf(queue) }
    var draggingIndex by remember { mutableStateOf(-1) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val rowHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF16141D),
        modifier = Modifier.fillMaxHeight(0.85f)
    ) {
        Text(
            "Fila de reprodução",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        if (localQueue.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Fila vazia", color = Color.White.copy(alpha = 0.6f))
            }
            return@ModalBottomSheet
        }
        val queueListState = rememberLazyListState()
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(state = queueListState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(localQueue, key = { _, song -> song.id }) { index, song ->
                val isCurrent = index == currentIndex
                val isDragging = index == draggingIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .graphicsLayer { translationY = if (isDragging) dragOffsetY else 0f }
                        .zIndex(if (isDragging) 1f else 0f)
                        .background(
                            when {
                                isDragging -> Color.White.copy(alpha = 0.08f)
                                isCurrent -> accent.copy(alpha = 0.14f)
                                else -> Color.Transparent
                            }
                        )
                        .clickable { onJumpTo(index) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.DragHandle,
                        contentDescription = "Arrastar pra reordenar",
                        tint = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .pointerInput(song.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { draggingIndex = index; dragOffsetY = 0f },
                                    onDragEnd = {
                                        draggingIndex = -1
                                        dragOffsetY = 0f
                                    },
                                    onDragCancel = { draggingIndex = -1; dragOffsetY = 0f }
                                ) { change, delta ->
                                    change.consume()
                                    dragOffsetY += delta.y
                                    val current = draggingIndex
                                    if (current == -1) return@detectDragGesturesAfterLongPress
                                    val steps = (dragOffsetY / rowHeightPx).roundToInt()
                                    val targetIndex = (current + steps).coerceIn(0, localQueue.lastIndex)
                                    if (targetIndex != current) {
                                        val mutable = localQueue.toMutableList()
                                        val moved = mutable.removeAt(current)
                                        mutable.add(targetIndex, moved)
                                        localQueue = mutable
                                        onMove(current, targetIndex)
                                        dragOffsetY -= (targetIndex - current) * rowHeightPx
                                        draggingIndex = targetIndex
                                    }
                                }
                            }
                    )
                    if (isCurrent) {
                        Icon(Icons.Filled.VolumeUp, contentDescription = "Tocando agora", tint = accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            song.title,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            color = if (isCurrent) accent else Color.White,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                        )
                        Text(
                            song.artist,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            color = Color.White.copy(alpha = 0.55f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    IconButton(onClick = {
                        localQueue = localQueue.toMutableList().apply { removeAt(index) }
                        onRemove(index)
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remover da fila", tint = Color.White.copy(alpha = 0.5f))
                    }
                }
            }
        }
        com.harmonic.player.ui.common.FastScrollbar(
            listState = queueListState,
            itemCount = localQueue.size,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Disco de vinil com a capa do álbum encaixada no centro, girando enquanto
 * a música toca. Usa um [Animatable] em vez de rememberInfiniteTransition
 * porque precisamos CONGELAR a rotação exatamente onde ela parou ao
 * pausar — como um toca-discos de verdade, que não "volta" pro início, só
 * para de girar ali mesmo.
 */
@Composable
private fun VinylRecord(
    bitmap: Bitmap?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val angle = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                angle.animateTo(
                    targetValue = angle.value + 360f,
                    animationSpec = tween(durationMillis = 9000, easing = LinearEasing)
                )
            }
        }
        // Quando isPlaying vira false, este LaunchedEffect é cancelado pelo
        // próprio Compose (a key mudou) no meio da animação — angle.value
        // fica exatamente onde estava, sem precisar de nenhum código extra.
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = angle.value }
                .clip(CircleShape)
                .background(Color(0xFF141414))
                .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
        ) {
            // Sulcos do vinil — círculos concêntricos bem sutis entre a
            // borda do disco e a capa no centro.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val maxRadius = size.minDimension / 2f
                val grooveCount = 9
                for (i in 1..grooveCount) {
                    val r = maxRadius * (0.68f + (i / grooveCount.toFloat()) * 0.30f)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.05f),
                        radius = r,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }

            // Capa do álbum (ou ícone de nota musical) ocupando o miolo do disco
            Box(
                modifier = Modifier
                    .fillMaxSize(0.62f)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF262626)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxSize(0.4f)
                        )
                    }
                }
            }

            // Furo do eixo, bem no centro
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color(0xFF0A0A0A))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            )
        }
    }
}

@Composable
private fun SleepTimerDialog(
    currentEndAt: Long?,
    onDismiss: () -> Unit,
    onSelectMinutes: (Int) -> Unit,
    onSelectEndOfSong: () -> Unit,
    onCancel: () -> Unit
) {
    val options = listOf(5, 10, 15, 30, 45, 60)
    // O CORPO do diálogo (fundo) usa a cor "de sistema" fixa do Material
    // (colorScheme.surface), mas os botões de texto (TextButton) puxavam a
    // cor de destaque POR MÚSICA (pageAccent, que sobrescreve
    // colorScheme.primary só nesta tela) — as duas nem sempre combinam, e
    // às vezes ficava ilegível. Fixando explicitamente em onSurface (a cor
    // pensada pra sempre contrastar com esse fundo específico) em vez de
    // herdar do botão, garante leitura em qualquer combinação de cores.
    val onSurface = MaterialTheme.colorScheme.onSurface
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer", color = onSurface) },
        text = {
            Column {
                if (currentEndAt != null) {
                    Text(
                        "Timer ativo. Toque em \"Cancelar\" pra desativar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = onSurface.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(12.dp))
                }
                options.forEach { minutes ->
                    TextButton(onClick = { onSelectMinutes(minutes) }, modifier = Modifier.fillMaxWidth()) {
                        Text("$minutes minutos", modifier = Modifier.fillMaxWidth(), color = onSurface)
                    }
                }
                TextButton(onClick = onSelectEndOfSong, modifier = Modifier.fillMaxWidth()) {
                    Text("Fim da música atual", modifier = Modifier.fillMaxWidth(), color = onSurface)
                }
            }
        },
        confirmButton = {
            if (currentEndAt != null) {
                TextButton(onClick = onCancel) { Text("Cancelar timer", color = onSurface) }
            } else {
                TextButton(onClick = onDismiss) { Text("Fechar", color = onSurface) }
            }
        }
    )
}

/** Formata milissegundos como "m:ss", ex: 1234000ms -> "20:34". */
@Composable
private fun SeekChip(label: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/** Ciclo de velocidades do botão discreto na tela "Tocando agora". */
private val playbackSpeedCycle = listOf(1f, 1.25f, 1.5f, 1.75f, 2f, 0.5f, 0.75f)

private fun nextPlaybackSpeed(current: Float): Float {
    val currentIndex = playbackSpeedCycle.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
    val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % playbackSpeedCycle.size
    return playbackSpeedCycle[nextIndex]
}

/** "1x", "1.5x", "0.75x"... — sem casas decimais desnecessárias. */
private fun formatPlaybackSpeed(speed: Float): String {
    val text = if (speed == speed.toInt().toFloat()) {
        speed.toInt().toString()
    } else {
        // Remove zero à direita (1.50 -> 1.5) mantendo vírgula/ponto simples.
        "%.2f".format(speed).trimEnd('0').trimEnd('.')
    }
    return "${text}x"
}

/** Formata bytes como "3,4 MB" (ou KB pros arquivos bem pequenos). */
private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 0.1) "%.1f MB".format(mb) else "%.0f KB".format(bytes / 1024.0)
}
