package com.harmonic.player.ui.library

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.roundToInt
import com.harmonic.player.ui.theme.withSingleAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.layout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harmonic.player.data.GradientTheme
import com.harmonic.player.data.AlbumSummary
import com.harmonic.player.data.ArtistSummary
import com.harmonic.player.data.MusicDatabase
import com.harmonic.player.data.Playlist
import com.harmonic.player.data.PlaylistSongCrossRef
import com.harmonic.player.data.SettingsRepository
import com.harmonic.player.data.Song
import com.harmonic.player.data.SongDao
import com.harmonic.player.ui.common.ActionSheet
import com.harmonic.player.playback.PlayerController
import com.harmonic.player.playback.PlaybackUiState
import com.harmonic.player.ui.miniplayer.MiniPlayer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class LibraryTab(val label: String) {
    SONGS("Músicas"), ARTISTS("Artistas"), ALBUMS("Álbuns"),
    GENRES("Gêneros"), FOLDERS("Pastas"), FAVORITES("Favoritas"), PLAYLISTS("Playlists")
}

private val songSortOptions = listOf(
    com.harmonic.player.ui.common.SortOption("title", "Título"),
    com.harmonic.player.ui.common.SortOption("artist", "Artista"),
    com.harmonic.player.ui.common.SortOption("duration", "Duração"),
    com.harmonic.player.ui.common.SortOption("dateAdded", "Data adicionada"),
    com.harmonic.player.ui.common.SortOption("year", "Ano"),
    com.harmonic.player.ui.common.SortOption("playCount", "Mais tocadas"),
    com.harmonic.player.ui.common.SortOption("lastPlayedAt", "Tocadas recentemente")
)

private val albumDetailSongSortOptions = listOf(
    com.harmonic.player.ui.common.SortOption("trackNumber", "Faixa"),
    com.harmonic.player.ui.common.SortOption("title", "Título"),
    com.harmonic.player.ui.common.SortOption("duration", "Duração"),
    com.harmonic.player.ui.common.SortOption("playCount", "Mais tocadas"),
    com.harmonic.player.ui.common.SortOption("lastPlayedAt", "Tocadas recentemente")
)

private val albumSortOptions = listOf(
    com.harmonic.player.ui.common.SortOption("album", "Álbum"),
    com.harmonic.player.ui.common.SortOption("artist", "Artista"),
    com.harmonic.player.ui.common.SortOption("trackCount", "Nº de faixas"),
    com.harmonic.player.ui.common.SortOption("year", "Ano"),
    com.harmonic.player.ui.common.SortOption("playCount", "Mais tocadas")
)

private val artistSortOptions = listOf(
    com.harmonic.player.ui.common.SortOption("name", "Nome"),
    com.harmonic.player.ui.common.SortOption("songCount", "Nº de músicas"),
    com.harmonic.player.ui.common.SortOption("albumCount", "Nº de álbuns"),
    com.harmonic.player.ui.common.SortOption("playCount", "Mais tocadas")
)

private val playlistSortOptions = listOf(
    com.harmonic.player.ui.common.SortOption("name", "Nome"),
    com.harmonic.player.ui.common.SortOption("createdAt", "Data adicionada"),
    com.harmonic.player.ui.common.SortOption("modifiedAt", "Modificada")
)

/**
 * Gradiente do título: usa o MATIZ real das cores do tema (a "cor" em si
 * não muda, continua sendo a paleta escolhida pela pessoa), mas ajusta
 * saturação e claridade pra a cor ficar viva e se destacar do fundo —
 * sem aplicar sombra, brilho ou blur nenhum, só a própria cor mais forte.
 *
 * Sem isso, como o fundo do app é feito com as MESMAS cores do tema
 * (ver [com.harmonic.player.ui.common.AppBackground]), um gradiente de
 * texto com a cor "pura" do tema praticamente some em cima do fundo —
 * fica tudo no mesmo tom. Aqui a saturação é elevada a um mínimo vívido
 * e a claridade é empurrada pro lado oposto do fundo (texto claro sobre
 * fundo escuro, texto escuro sobre fundo claro), garantindo contraste
 * mesmo quando a cor de origem é próxima do fundo.
 *
 * @param backgroundIsDark se o fundo por trás do texto (já considerando
 *   o véu/scrim aplicado em Aparência) é escuro ou claro no geral.
 */
// internal (não private): reaproveitada pelo preview ao vivo em
// AppearanceScreen, pra mostrar exatamente a mesma cor que a Biblioteca vai
// usar de verdade — sem isso o preview mentia sobre como o gradiente ficaria.
internal fun readableGradientTextColors(
    sourceColors: List<Color>,
    backgroundIsDark: Boolean = true
): List<Color> = sourceColors.map { vividTitleColor(it, backgroundIsDark) }

internal fun vividTitleColor(color: Color, backgroundIsDark: Boolean): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (color.red * 255f).roundToInt().coerceIn(0, 255),
        (color.green * 255f).roundToInt().coerceIn(0, 255),
        (color.blue * 255f).roundToInt().coerceIn(0, 255),
        hsv
    )
    // Satura bem a cor (mantendo o matiz original) pra não ficar
    // "lavada" — cores já vívidas continuam como estão.
    hsv[1] = hsv[1].coerceAtLeast(0.6f)
    // Empurra o brilho pro extremo oposto ao do fundo, garantindo
    // contraste mesmo quando a cor de origem é parecida com o fundo.
    hsv[2] = if (backgroundIsDark) {
        hsv[2].coerceIn(0.88f, 1f)
    } else {
        hsv[2].coerceIn(0f, 0.32f)
    }
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * Ordena por ano SEM tratar "sem ano" como ano 0. Antes o `?: 0` fazia toda
 * música sem essa metadata (bem comum — nem todo MP3 tem a tag de ano
 * preenchida) parecer "lançada no ano 0", ou seja, mais antiga que
 * QUALQUER música com ano real — inclusive um álbum de 1967, por exemplo,
 * que ficava enterrado atrás de dezenas de músicas sem ano nenhum ao
 * ordenar "crescente". Aqui, músicas sem ano ficam sempre no final da
 * lista, não importa a direção escolhida.
 */
private fun sortSongsByYear(songs: List<Song>, ascending: Boolean): List<Song> {
    val (withYear, withoutYear) = songs.partition { it.year != null }
    val ordered = withYear.sortedWith(compareBy({ it.year }, { it.album.lowercase() }, { it.trackNumber ?: 0 }))
    val directional = if (ascending) ordered else ordered.reversed()
    return directional + withoutYear.sortedBy { it.title.lowercase() }
}

/** Mesma ideia de [sortSongsByYear], mas pra lista de álbuns. */
private fun sortAlbumsByYear(albums: List<com.harmonic.player.data.AlbumSummary>, ascending: Boolean): List<com.harmonic.player.data.AlbumSummary> {
    val (withYear, withoutYear) = albums.partition { it.year != null }
    val ordered = withYear.sortedWith(compareBy({ it.year }, { it.album.lowercase() }))
    val directional = if (ascending) ordered else ordered.reversed()
    return directional + withoutYear.sortedBy { it.album.lowercase() }
}


/**
 * Brush opcional pro título das músicas na lista, quando o usuário ativa
 * "gradiente nos títulos" na tela de Aparência. `null` = título com cor
 * sólida (comportamento padrão). Como [SongRow] é privado deste arquivo e
 * usado só aqui, um CompositionLocal evita ter que passar esse parâmetro
 * por todas as chamadas de SongList/SongRow espalhadas pelas abas.
 */
private val LocalSongTitleBrush = compositionLocalOf<Brush?> { null }

/**
 * Equivalente a um padding vertical negativo — usado pra deixar as linhas
 * das listas mais compactas, compensando o padding interno do ListItem.
 * `Modifier.padding()` do Compose passou a rejeitar valores negativos em
 * tempo de execução (`IllegalArgumentException: Padding must be
 * non-negative`), então em vez de padding isso mede o item normalmente e
 * só reduz a altura que ele reporta pro layout pai, deslocando o desenho
 * pra cima — mesmo resultado visual, sem cair na validação.
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
fun LibraryScreen(
    database: MusicDatabase,
    playerController: PlayerController,
    settings: SettingsRepository,
    onSongClick: (List<Song>, Int, String) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlaylist: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val dao = remember { database.songDao() }
    val playbackState by playerController.uiState.collectAsState()

    // Gradiente do título das músicas, se o usuário ativou essa opção em
    // Aparência — reaproveita as cores do tema de gradiente ativo (ou o
    // padrão "Meia-noite" quando o fundo é uma imagem, já que aí não existe
    // uma paleta de gradiente selecionada).
    val titleGradientEnabled by settings.titleGradientEnabled.collectAsState(initial = false)
    val gradientThemeName by settings.gradientTheme.collectAsState(initial = null)
    val titleGradientColorStart by settings.titleGradientColorStart.collectAsState(initial = null)
    val titleGradientColorEnd by settings.titleGradientColorEnd.collectAsState(initial = null)
    val backgroundImageActive by settings.defaultWallpaper.collectAsState(initial = null)
    val customBackgroundUri by settings.backgroundUri.collectAsState(initial = null)
    val backgroundScrimAlpha by settings.backgroundScrimAlpha.collectAsState(initial = 45)
    val albumGridView by settings.albumGridView.collectAsState(initial = false)
    val artistGridView by settings.artistGridView.collectAsState(initial = false)
    val hiddenTabNames by settings.hiddenTabs.collectAsState(initial = emptySet())
    val visibleTabs = remember(hiddenTabNames) {
        LibraryTab.values().filter { it == LibraryTab.SONGS || it.name !in hiddenTabNames }
    }
    val titleBrush = if (titleGradientEnabled) {
        // Cores de origem: as escolhidas livremente pela pessoa na roda de
        // cores, ou as do tema de gradiente ativo.
        val source = if (titleGradientColorStart != null && titleGradientColorEnd != null) {
            listOf(Color(titleGradientColorStart!!), Color(titleGradientColorEnd!!))
        } else {
            val theme = GradientTheme.values().find { it.name == gradientThemeName } ?: GradientTheme.APP_ICON
            theme.colorsArgb.map { Color(it) }
        }
        // Estima se o fundo atrás do texto é claro ou escuro, já
        // considerando o véu (scrim) preto aplicado em cima — mesma lógica
        // usada em AppBackground. Com imagem de fundo custom não dá pra
        // saber a cor sem decodificar o bitmap aqui, então assume escuro
        // (o caso mais comum e o padrão de fábrica do app).
        val backgroundIsDark = if (customBackgroundUri != null || backgroundImageActive != null) {
            true
        } else {
            val theme = GradientTheme.values().find { it.name == gradientThemeName } ?: GradientTheme.APP_ICON
            val avgLuminance = theme.colorsArgb.map { Color(it).luminance() }.average().toFloat()
            val scrimmedLuminance = avgLuminance * (1f - backgroundScrimAlpha / 100f)
            scrimmedLuminance < 0.5f
        }
        Brush.linearGradient(readableGradientTextColors(source, backgroundIsDark))
    } else null

    var bulkAddSongs by remember { mutableStateOf<List<Song>?>(null) }
    var deleteConfirm by remember { mutableStateOf<Pair<String, suspend () -> Unit>?>(null) }
    var renameArtistTarget by remember { mutableStateOf<String?>(null) }
    var renameAlbumTarget by remember { mutableStateOf<AlbumSummary?>(null) }
    val favoriteArtists by dao.getFavoriteArtistNames().collectAsState(initial = emptyList())
    val favoriteAlbumIds by dao.getFavoriteAlbumIds().collectAsState(initial = emptyList())
    // Clique longo num álbum/artista/pasta (nas abas de listagem, sem
    // entrar na página dele) abre o mesmo menu "⋮" que já existe lá dentro
    // — só guarda aqui QUAL item, o menu em si é montado mais abaixo.
    var quickMenuAlbum by remember { mutableStateOf<AlbumSummary?>(null) }
    var quickMenuArtist by remember { mutableStateOf<String?>(null) }
    var quickMenuFolder by remember { mutableStateOf<String?>(null) }
    // rememberSaveable (não remember): sem isso, o critério de ordenação
    // "esquecia" toda vez que a tela saía de composição — por exemplo, ao
    // tocar uma música e voltar da tela "Tocando agora", ou trocar de aba
    // — voltando sempre pro padrão de fábrica em vez de manter a escolha
    // da pessoa. As telas de detalhe (dentro de um artista/álbum) já
    // usavam rememberSaveable por esse mesmo motivo; aqui só faltava.
    // Fica salvo de verdade (DataStore), não só rememberSaveable — antes
    // sobrevivia a trocar de tela mas voltava pro padrão de fábrica toda
    // vez que o app era fechado por completo e reaberto. Padrão de
    // fábrica agora é "Data adicionada" (mais recentes primeiro) em vez de
    // alfabético.
    val sortKey by settings.songsSortKey.collectAsState(initial = "dateAdded")
    val sortAscending by settings.songsSortAscending.collectAsState(initial = false)
    var minDurationFilterSec by remember { mutableStateOf(0) }
    var maxDurationFilterSec by remember { mutableStateOf(0) } // 0 = sem limite máximo
    var showDurationFilterDialog by remember { mutableStateOf(false) }
    var favoritesSortKey by rememberSaveable { mutableStateOf("title") }
    var favoritesSortAscending by rememberSaveable { mutableStateOf(true) }
    var albumSortKey by rememberSaveable { mutableStateOf("album") }
    var albumSortAscending by rememberSaveable { mutableStateOf(true) }
    var artistSortKey by rememberSaveable { mutableStateOf("name") }
    var artistSortAscending by rememberSaveable { mutableStateOf(true) }
    var playlistSortKey by rememberSaveable { mutableStateOf("createdAt") }
    var playlistSortAscending by rememberSaveable { mutableStateOf(false) }
    // Ordenação das músicas DENTRO da página de um artista/álbum aberto
    // (botão "ordenar por" ao lado do play/"⋮" no cabeçalho dessas páginas).
    var artistDetailSortKey by rememberSaveable { mutableStateOf("title") }
    var artistDetailSortAscending by rememberSaveable { mutableStateOf(true) }
    var albumDetailSortKey by rememberSaveable { mutableStateOf("trackNumber") }
    var albumDetailSortAscending by rememberSaveable { mutableStateOf(true) }
    var showCreatePlaylistFab by remember { mutableStateOf(false) }
    // Qual playlist está com diálogo de renomear/excluir aberto — o menu de
    // opções em si agora vive dentro da própria linha da playlist, perto do
    // botão "⋮" que o aciona.
    var playlistForDialog by remember { mutableStateOf<Playlist?>(null) }
    var showRenamePlaylistDialog by remember { mutableStateOf(false) }
    var showDeletePlaylistConfirm by remember { mutableStateOf(false) }

    // rememberSaveable (não só remember) porque abrir "Tocando agora" navega
    // pra uma rota nova no NavHost — o que descarta a composição desta tela
    // enquanto ela não está visível. Com remember comum, esse estado
    // (aba selecionada e artista/álbum "aberto") era perdido nesse meio
    // tempo, e voltar de "Tocando agora" sempre caía na lista de Músicas em
    // vez de voltar pra página do artista/álbum onde o usuário estava.
    var selectedTab by rememberSaveable { mutableStateOf(LibraryTab.SONGS) }
    // Quando o usuário toca num nome de artista/álbum/gênero/pasta, guardamos
    // aqui qual grupo foi escolhido, pra mostrar as músicas daquele grupo.
    // Voltar (seta ou botão físico) limpa isso e volta pra lista de grupos.
    var drilledGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var drilledAlbumId by rememberSaveable { mutableStateOf<Long?>(null) }
    var drilledAlbumArtist by rememberSaveable { mutableStateOf("") }

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    // Esconde a barrinha de "total de músicas/artistas/..." (com o
    // shuffle e o ordenar por) ao rolar a lista, deixando só o menu
    // horizontal das abas visível acima — igual um cabeçalho que recolhe.
    // Usa nested scroll pra "ouvir" o gesto de rolagem das listas internas
    // (LazyColumn/LazyVerticalGrid) ANTES delas consumirem o movimento.
    var countBarVisible by remember { mutableStateOf(true) }
    val countBarNestedScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (available.y < -4f) countBarVisible = false
                else if (available.y > 4f) countBarVisible = true
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    // Volta pra lista de grupos ao trocar de aba
    LaunchedEffect(selectedTab) {
        drilledGroup = null
        drilledAlbumId = null
        countBarVisible = true
    }

    // Pede foco assim que o campo de busca aparece, para o usuário poder
    // digitar direto sem precisar tocar duas vezes.
    LaunchedEffect(isSearching) {
        if (isSearching) searchFocusRequester.requestFocus()
    }

    // Antes só tratava o gesto de voltar quando dentro de artista/álbum —
    // com a busca aberta (e sem estar dentro de artista/álbum), o gesto de
    // voltar não tinha NENHUM BackHandler ativo pra interceptar, então caía
    // no comportamento padrão do sistema (minimizar o app) em vez de só
    // fechar a busca. Também passou a voltar pra aba "Músicas" antes de
    // deixar o gesto minimizar o app, caso a pessoa esteja em outra aba
    // (Artistas, Álbuns, Gêneros, Pastas, Playlists) — assim "voltar" some
    // primeiro pela navegação interna do app, só saindo de verdade depois.
    androidx.activity.compose.BackHandler(
        enabled = isSearching || drilledGroup != null || drilledAlbumId != null || selectedTab != LibraryTab.SONGS
    ) {
        if (isSearching) {
            isSearching = false
            searchQuery = ""
        } else if (drilledGroup != null || drilledAlbumId != null) {
            drilledGroup = null
            drilledAlbumId = null
        } else {
            selectedTab = LibraryTab.SONGS
        }
    }

    val searchResults by (if (searchQuery.isNotBlank()) dao.search(searchQuery) else dao.getAllSongs())
        .collectAsState(initial = emptyList())

    CompositionLocalProvider(LocalSongTitleBrush provides titleBrush) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    if (isSearching) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar músicas, artistas, álbuns...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester)
                        )
                    } else {
                        val accent = MaterialTheme.colorScheme.primary
                        Text(
                            "Music Box",
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            style = LocalTextStyle.current.copy(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = accent.copy(alpha = 0.75f),
                                    offset = androidx.compose.ui.geometry.Offset.Zero,
                                    blurRadius = 18f
                                )
                            )
                        )
                    }
                },
                navigationIcon = {
                    if (isSearching) {
                        IconButton(onClick = { isSearching = false; searchQuery = "" }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Fechar busca", tint = Color.White)
                        }
                    }
                },
                actions = {
                    if (!isSearching) {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Buscar", tint = Color.White)
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "Configurações", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            MiniPlayer(
                state = playbackState,
                onTogglePlayPause = { playerController.togglePlayPause() },
                onSkipPrevious = { playerController.skipPrevious() },
                onSkipNext = { playerController.skipNext() },
                onStop = { playerController.stop() },
                onOpenNowPlaying = onOpenNowPlaying
            )
        },
        floatingActionButton = {
            if (selectedTab == LibraryTab.PLAYLISTS && searchQuery.isBlank()) {
                FloatingActionButton(onClick = { showCreatePlaylistFab = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Nova playlist")
                }
            }
        }
    ) { padding ->
        // Deslizar pros lados troca de aba — só quando estamos na lista de
        // topo de cada aba (não dentro de um artista/álbum/pasta aberto,
        // pra não atrapalhar quem só quer rolar aquela lista) e fora da busca.
        var dragAccumulator by remember { mutableStateOf(0f) }
        val canSwipeTabs = drilledGroup == null && drilledAlbumId == null && searchQuery.isBlank()

        // Deslocamento horizontal do conteúdo, animável: acompanha o dedo
        // durante o arrasto (com uma leve resistência) pra dar feedback
        // visual imediato de que o swipe está sendo reconhecido — antes,
        // nada se mexia até soltar o dedo, e por isso parecia que o gesto
        // não estava funcionando. Ao soltar, anima suavemente de volta pra
        // 0 (seja porque a aba mudou — nesse caso "desliza" o novo conteúdo
        // pra dentro — seja porque não passou do limite, e aí só volta pro
        // lugar), sem nenhum exagero de velocidade ou distância.
        val dragOffsetX = remember { androidx.compose.animation.core.Animatable(0f) }
        val density = LocalDensity.current
        val maxDragOffsetPx = with(density) { 48.dp.toPx() }
        val maxDragFadePx = with(density) { 220.dp.toPx() }

        LaunchedEffect(selectedTab) {
            dragOffsetX.animateTo(
                0f,
                animationSpec = tween(durationMillis = 260, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(countBarNestedScrollConnection)
                .graphicsLayer {
                    translationX = dragOffsetX.value
                    alpha = 1f - (kotlin.math.abs(dragOffsetX.value) / maxDragFadePx).coerceIn(0f, 0.25f)
                }
                .then(
                    if (canSwipeTabs) {
                        Modifier.pointerInput(visibleTabs, selectedTab) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragAccumulator = 0f },
                                onDragEnd = {
                                    val currentIndex = visibleTabs.indexOf(selectedTab)
                                    val changedTab = when {
                                        dragAccumulator < -120f && currentIndex < visibleTabs.lastIndex -> {
                                            selectedTab = visibleTabs[currentIndex + 1]; true
                                        }
                                        dragAccumulator > 120f && currentIndex > 0 -> {
                                            selectedTab = visibleTabs[currentIndex - 1]; true
                                        }
                                        else -> false
                                    }
                                    dragAccumulator = 0f
                                    // Se a aba não mudou, o LaunchedEffect(selectedTab)
                                    // acima não dispara (a key não muda) — então essa
                                    // volta suave pro lugar precisa ser feita aqui.
                                    if (!changedTab) {
                                        scope.launch {
                                            dragOffsetX.animateTo(0f, tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                                        }
                                    }
                                }
                            ) { change, dragAmount ->
                                dragAccumulator += dragAmount
                                scope.launch {
                                    dragOffsetX.snapTo((dragOffsetX.value + dragAmount * 0.55f).coerceIn(-maxDragOffsetPx, maxDragOffsetPx))
                                }
                                change.consume()
                            }
                        }
                    } else Modifier
                )
        ) {

            // Sem a permissão de notificação (Android 13+) ou com elas
            // desativadas nas configurações do sistema, o Android nem
            // mostra a notificação de reprodução — os controles de
            // play/pause somem da barra de notificação mesmo com a música
            // tocando. Esse aviso deixa isso claro em vez do usuário
            // descobrir escondido, sem saber o motivo.
            //
            // IMPORTANTE: `areNotificationsEnabled()` só verifica a permissão
            // GERAL do app — não pega o caso (bem comum) de só o canal
            // "Reprodução" estar desativado individualmente nas
            // configurações (ex: o usuário desativou só ele antes, ou o
            // canal foi criado com uma config ruim numa versão antiga do
            // app — depois de criado, o sistema NUNCA deixa o app mudar a
            // importância do canal de novo, só o usuário manualmente).
            // Nesse caso a permissão geral aparece concedida, mas a
            // notificação nunca aparece — só os controles da tela de
            // bloqueio continuam, porque esses vêm direto da MediaSession,
            // não do canal de notificação. Por isso checamos o canal
            // específico também, e linkamos direto pra tela dele (não só a
            // geral do app) quando ele é a causa.
            var notificationBannerDismissed by remember { mutableStateOf(false) }
            val notificationManagerCompat = remember { androidx.core.app.NotificationManagerCompat.from(context) }
            val playbackChannelBlocked = remember {
                val channel = notificationManagerCompat.getNotificationChannel(
                    com.harmonic.player.playback.PlaybackService.NOTIFICATION_CHANNEL_ID
                )
                channel != null && channel.importance == android.app.NotificationManager.IMPORTANCE_NONE
            }
            val notificationsEnabled = remember { notificationManagerCompat.areNotificationsEnabled() }
            if ((!notificationsEnabled || playbackChannelBlocked) && !notificationBannerDismissed) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(Color.White.copy(alpha = 0.08f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Icon(Icons.Filled.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Notificações desativadas", color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Sem elas, os controles de play/pause não aparecem fora do app",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(
                            onClick = {
                                // Quando é só o canal específico que está
                                // bloqueado (permissão geral ok), abre direto
                                // a tela desse canal — a tela geral de
                                // notificações do app não deixa reativar um
                                // canal individual escondido lá dentro.
                                val intent = if (playbackChannelBlocked && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    android.content.Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, com.harmonic.player.playback.PlaybackService.NOTIFICATION_CHANNEL_ID)
                                } else {
                                    android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                context.startActivity(intent)
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) { Text("Ativar") }
                    }
                    IconButton(onClick = { notificationBannerDismissed = true }) {
                        Icon(Icons.Filled.Close, contentDescription = "Dispensar", tint = Color.White.copy(alpha = 0.6f))
                    }
                }
            }

            if (searchQuery.isBlank()) {
                val accentColor = MaterialTheme.colorScheme.primary
                val selectedVisibleIndex = visibleTabs.indexOf(selectedTab).coerceAtLeast(0)
                ScrollableTabRow(
                    selectedTabIndex = selectedVisibleIndex,
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    edgePadding = 16.dp,
                    // Sem o divisor padrão (linha cinza full-width) — some
                    // com a sensação de "barra escura" atrás do menu.
                    divider = {},
                    indicator = { tabPositions ->
                        if (selectedVisibleIndex < tabPositions.size) {
                            TabRowDefaults.Indicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedVisibleIndex]),
                                height = 3.dp,
                                color = accentColor
                            )
                        }
                    }
                ) {
                    visibleTabs.forEach { tab ->
                        val isSelected = selectedTab == tab
                        val tabTitleBrush = LocalSongTitleBrush.current
                        Tab(
                            selected = isSelected,
                            onClick = { selectedTab = tab },
                            text = {
                                if (isSelected && tabTitleBrush != null) {
                                    // Gradiente de letras ativado: a aba
                                    // selecionada usa o mesmo gradiente de
                                    // destaque em vez de cor sólida.
                                    Text(
                                        tab.label,
                                        style = androidx.compose.ui.text.TextStyle(
                                            brush = tabTitleBrush,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                } else {
                                    Text(
                                        tab.label,
                                        // Sem gradiente de letras: a aba
                                        // selecionada usa a cor de destaque
                                        // do tema, e as não selecionadas
                                        // agora ficam branco sólido (antes
                                        // era um branco meio transparente).
                                        color = if (isSelected) accentColor else Color.White,
                                        fontSize = if (isSelected) 15.sp else 13.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            },
                            selectedContentColor = accentColor,
                            unselectedContentColor = Color.White
                        )
                    }
                }
            }

            // Se a aba selecionada foi escondida enquanto estava ativa, volta pra Músicas.
            LaunchedEffect(visibleTabs) {
                if (selectedTab !in visibleTabs) selectedTab = LibraryTab.SONGS
            }

            if (searchQuery.isBlank() && drilledGroup == null && drilledAlbumId == null) {
                val countText = when (selectedTab) {
                    LibraryTab.SONGS -> {
                        val allSongs by dao.getAllSongs().collectAsState(initial = emptyList())
                        "${allSongs.size} música(s)"
                    }
                    LibraryTab.ARTISTS -> {
                        val allArtists by dao.getArtists().collectAsState(initial = emptyList())
                        "${allArtists.size} artista(s)"
                    }
                    LibraryTab.ALBUMS -> {
                        val allAlbums by dao.getAlbums().collectAsState(initial = emptyList())
                        "${allAlbums.size} álbum(ns)"
                    }
                    LibraryTab.FAVORITES -> {
                        val allFavorites by dao.getFavorites().collectAsState(initial = emptyList())
                        "${allFavorites.size} favorita(s)"
                    }
                    LibraryTab.PLAYLISTS -> {
                        val allPlaylists by dao.getPlaylists().collectAsState(initial = emptyList())
                        "${allPlaylists.size} playlist(s)"
                    }
                    else -> null
                }
                // Some ao rolar a lista pra baixo (só o menu horizontal das
                // abas, acima, continua fixo) e reaparece ao rolar pra cima.
                androidx.compose.animation.AnimatedVisibility(
                    visible = countBarVisible,
                    enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (countText != null) {
                            Spacer(Modifier.width(6.dp))
                            Text(countText, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (selectedTab) {
                            LibraryTab.SONGS -> {
                                val allSongs by dao.getAllSongs().collectAsState(initial = emptyList())
                                // "Ativo" = a fila tocando agora veio dessa
                                // aba (tanto faz se foi o botão de play ou o
                                // de aleatório que a colocou pra tocar — os
                                // dois usam sourceKey "songs"). O brilho e a
                                // cor de destaque só aparecem nesse caso;
                                // parado/pausado ou tocando outra coisa, os
                                // dois botões ficam neutros.
                                val isThisSourceActive = isQueueFullyActive(playbackState, allSongs, "songs")
                                val isPlayingThis = isThisSourceActive && playbackState.isPlaying
                                val isShufflingThis = isThisSourceActive && playbackState.shuffleEnabled
                                val accent = MaterialTheme.colorScheme.primary
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
                                    if (isPlayingThis) {
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .background(
                                                    Brush.radialGradient(listOf(accent.copy(alpha = 0.4f), Color.Transparent))
                                                )
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (isThisSourceActive) {
                                                playerController.togglePlayPause()
                                            } else {
                                                playerController.requestPlayQueue(allSongs, 0, "songs", "Músicas")
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            if (isPlayingThis) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = if (isPlayingThis) "Pausar" else "Tocar tudo",
                                            tint = if (isPlayingThis) accent else Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
                                    if (isShufflingThis) {
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .background(
                                                    Brush.radialGradient(listOf(accent.copy(alpha = 0.4f), Color.Transparent))
                                                )
                                        )
                                    }
                                    IconButton(onClick = { playerController.requestPlayQueueShuffled(allSongs, "songs", "Músicas") }, modifier = Modifier.size(32.dp)) {
                                        Icon(
                                            Icons.Filled.Shuffle,
                                            contentDescription = "Aleatório",
                                            tint = if (isShufflingThis) accent else Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                com.harmonic.player.ui.common.SortMenuButton(
                                    options = songSortOptions, selectedKey = sortKey, ascending = sortAscending,
                                    onSelect = { newKey -> scope.launch { settings.setSongsSort(newKey, sortAscending) } },
                                    onToggleDirection = { scope.launch { settings.setSongsSort(sortKey, !sortAscending) } }
                                )
                                val durationFilterActive = minDurationFilterSec > 0 || maxDurationFilterSec > 0
                                IconButton(onClick = { showDurationFilterDialog = true }, modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        Icons.Filled.FilterAlt,
                                        contentDescription = "Filtrar por duração",
                                        tint = if (durationFilterActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            LibraryTab.FAVORITES -> {
                                val allFavorites by dao.getFavorites().collectAsState(initial = emptyList())
                                // Mesma ideia da aba Músicas, ver comentário acima.
                                val isThisSourceActive = isQueueFullyActive(playbackState, allFavorites, "favorites")
                                val isPlayingThis = isThisSourceActive && playbackState.isPlaying
                                val isShufflingThis = isThisSourceActive && playbackState.shuffleEnabled
                                val accent = MaterialTheme.colorScheme.primary
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
                                    if (isPlayingThis) {
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .background(
                                                    Brush.radialGradient(listOf(accent.copy(alpha = 0.4f), Color.Transparent))
                                                )
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (isThisSourceActive) {
                                                playerController.togglePlayPause()
                                            } else {
                                                playerController.requestPlayQueue(allFavorites, 0, "favorites", "Favoritas")
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            if (isPlayingThis) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = if (isPlayingThis) "Pausar" else "Tocar tudo",
                                            tint = if (isPlayingThis) accent else Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
                                    if (isShufflingThis) {
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .background(
                                                    Brush.radialGradient(listOf(accent.copy(alpha = 0.4f), Color.Transparent))
                                                )
                                        )
                                    }
                                    IconButton(onClick = { playerController.requestPlayQueueShuffled(allFavorites, "favorites", "Favoritas") }, modifier = Modifier.size(32.dp)) {
                                        Icon(
                                            Icons.Filled.Shuffle,
                                            contentDescription = "Aleatório",
                                            tint = if (isShufflingThis) accent else Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                com.harmonic.player.ui.common.SortMenuButton(
                                    options = songSortOptions, selectedKey = favoritesSortKey, ascending = favoritesSortAscending,
                                    onSelect = { favoritesSortKey = it }, onToggleDirection = { favoritesSortAscending = !favoritesSortAscending }
                                )
                            }
                            LibraryTab.ALBUMS -> {
                                com.harmonic.player.ui.common.SortMenuButton(
                                    options = albumSortOptions, selectedKey = albumSortKey, ascending = albumSortAscending,
                                    onSelect = { albumSortKey = it }, onToggleDirection = { albumSortAscending = !albumSortAscending }
                                )
                            }
                            LibraryTab.ARTISTS -> {
                                com.harmonic.player.ui.common.SortMenuButton(
                                    options = artistSortOptions, selectedKey = artistSortKey, ascending = artistSortAscending,
                                    onSelect = { artistSortKey = it }, onToggleDirection = { artistSortAscending = !artistSortAscending }
                                )
                            }
                            LibraryTab.PLAYLISTS -> {
                                com.harmonic.player.ui.common.SortMenuButton(
                                    options = playlistSortOptions, selectedKey = playlistSortKey, ascending = playlistSortAscending,
                                    onSelect = { playlistSortKey = it }, onToggleDirection = { playlistSortAscending = !playlistSortAscending }
                                )
                            }
                            else -> {}
                        }
                    }
                }
                }
            }

            when {
                // Busca tem prioridade sobre tudo — mostra resultado direto
                searchQuery.isNotBlank() -> SongList(
                    songs = searchResults,
                    onSongClick = { onSongClick(searchResults, searchResults.indexOf(it), "search"); onOpenNowPlaying() },
                    onFavoriteToggle = { song -> scope.launch { val newValue = !song.isFavorite; dao.setFavorite(song.id, newValue); playerController.updateSongFavoriteInMemory(song.id, newValue) } },
                    dao = dao,
                    playerController = playerController,
                    onPlayNext = { playerController.playNext(it) },
                    onAddToQueueEnd = { playerController.addToQueueEnd(it) },
                    currentPlayingSongId = playbackState.currentSong?.id,
                    isPlaying = playbackState.isPlaying
                )

                selectedTab == LibraryTab.SONGS -> {
                    val songsRaw by dao.getAllSongs().collectAsState(initial = emptyList())
                    val songs = remember(songsRaw, minDurationFilterSec, maxDurationFilterSec) {
                        if (minDurationFilterSec <= 0 && maxDurationFilterSec <= 0) {
                            songsRaw
                        } else {
                            songsRaw.filter { song ->
                                val sec = song.durationMs / 1000
                                (minDurationFilterSec <= 0 || sec >= minDurationFilterSec) &&
                                    (maxDurationFilterSec <= 0 || sec <= maxDurationFilterSec)
                            }
                        }
                    }
                    val sortedSongs = remember(songs, sortKey, sortAscending) {
                        if (sortKey == "year") {
                            sortSongsByYear(songs, sortAscending)
                        } else {
                            val base = when (sortKey) {
                                "artist" -> songs.sortedBy { it.artist.lowercase() }
                                "duration" -> songs.sortedBy { it.durationMs }
                                "dateAdded" -> songs.sortedBy { it.dateAdded }
                                "playCount" -> songs.sortedBy { it.playCount }
                                "lastPlayedAt" -> songs.sortedBy { it.lastPlayedAt ?: 0L }
                                else -> songs.sortedBy { it.title.lowercase() }
                            }
                            if (sortAscending) base else base.reversed()
                        }
                    }
                    SongList(
                        songs = sortedSongs,
                        // Antes tocava a lista INTEIRA a partir da música
                        // clicada (com centenas de músicas às vezes) — a
                        // fila em "Tocando agora" ficava enorme e difícil
                        // de administrar. Agora clicar toca só aquela
                        // música; se a pessoa quiser ouvir tudo, é só usar
                        // o botão de play ou o de aleatório aqui do lado.
                        // A lista completa ainda é passada como CONTEXTO
                        // (não como fila) só pra Anterior/Próxima saberem
                        // pra onde ir — a fila em si continua com 1 música.
                        onSongClick = {
                            playerController.requestPlaySingleSongWithContext(sortedSongs, sortedSongs.indexOf(it), "songs", "Músicas")
                            onOpenNowPlaying()
                        },
                        onFavoriteToggle = { song -> scope.launch { val newValue = !song.isFavorite; dao.setFavorite(song.id, newValue); playerController.updateSongFavoriteInMemory(song.id, newValue) } },
                        dao = dao,
                        playerController = playerController,
                    onPlayNext = { playerController.playNext(it) },
                    onAddToQueueEnd = { playerController.addToQueueEnd(it) },
                        currentPlayingSongId = playbackState.currentSong?.id,
                        isPlaying = playbackState.isPlaying,
                        sortSignature = "$sortKey:$sortAscending"
                    )
                }

                selectedTab == LibraryTab.FAVORITES -> {
                    val favoritesRaw by dao.getFavorites().collectAsState(initial = emptyList())
                    val songs = remember(favoritesRaw, favoritesSortKey, favoritesSortAscending) {
                        if (favoritesSortKey == "year") {
                            sortSongsByYear(favoritesRaw, favoritesSortAscending)
                        } else {
                            val base = when (favoritesSortKey) {
                                "artist" -> favoritesRaw.sortedBy { it.artist.lowercase() }
                                "duration" -> favoritesRaw.sortedBy { it.durationMs }
                                "dateAdded" -> favoritesRaw.sortedBy { it.dateAdded }
                                "playCount" -> favoritesRaw.sortedBy { it.playCount }
                                "lastPlayedAt" -> favoritesRaw.sortedBy { it.lastPlayedAt ?: 0L }
                                else -> favoritesRaw.sortedBy { it.title.lowercase() }
                            }
                            if (favoritesSortAscending) base else base.reversed()
                        }
                    }
                    SongList(
                        songs = songs,
                        // Mesma mudança da aba Músicas — ver comentário acima.
                        onSongClick = {
                            playerController.requestPlaySingleSongWithContext(songs, songs.indexOf(it), "favorites", "Favoritas")
                            onOpenNowPlaying()
                        },
                        onFavoriteToggle = { song -> scope.launch { val newValue = !song.isFavorite; dao.setFavorite(song.id, newValue); playerController.updateSongFavoriteInMemory(song.id, newValue) } },
                        dao = dao,
                        playerController = playerController,
                    onPlayNext = { playerController.playNext(it) },
                    onAddToQueueEnd = { playerController.addToQueueEnd(it) },
                        currentPlayingSongId = playbackState.currentSong?.id,
                        isPlaying = playbackState.isPlaying,
                        sortSignature = "$favoritesSortKey:$favoritesSortAscending"
                    )
                }

                selectedTab == LibraryTab.ARTISTS && drilledGroup == null -> {
                    val artistSummariesRaw by dao.getArtistSummaries().collectAsState(initial = emptyList())
                    val artistSummaries = remember(artistSummariesRaw, artistSortKey, artistSortAscending) {
                        val base = when (artistSortKey) {
                            "songCount" -> artistSummariesRaw.sortedBy { it.songCount }
                            "albumCount" -> artistSummariesRaw.sortedBy { it.albumCount }
                            "playCount" -> artistSummariesRaw.sortedBy { it.playCount }
                            else -> artistSummariesRaw.sortedBy { it.name.lowercase() }
                        }
                        if (artistSortAscending) base else base.reversed()
                    }
                    // Mesmo motivo do SongList: sem voltar pro topo, trocar
                    // a ordenação fazia a tela "pular" pro fim sozinha.
                    val artistListState = rememberLazyListState()
                    val artistGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                    LaunchedEffect(artistSortKey, artistSortAscending) {
                        artistListState.scrollToItem(0)
                        artistGridState.scrollToItem(0)
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (artistGridView) {
                            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                                state = artistGridState,
                                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(artistSummaries, key = { it.name }) { artist ->
                                    ArtistGridCell(artist = artist, dao = dao, onLongClick = { quickMenuArtist = artist.name }) { drilledGroup = artist.name }
                                }
                            }
                            com.harmonic.player.ui.common.FastScrollbarGrid(
                                gridState = artistGridState,
                                itemCount = artistSummaries.size,
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        } else {
                            LazyColumn(state = artistListState, modifier = Modifier.fillMaxSize()) {
                                items(artistSummaries, key = { it.name }) { artist ->
                                    ArtistRow(artist = artist, dao = dao, onLongClick = { quickMenuArtist = artist.name }) { drilledGroup = artist.name }
                                }
                            }
                            com.harmonic.player.ui.common.FastScrollbar(
                                listState = artistListState,
                                itemCount = artistSummaries.size,
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }
                    }
                }
                selectedTab == LibraryTab.ARTISTS -> {
                    val artistName = drilledGroup!!
                    val songsRaw by dao.getSongsByArtist(artistName).collectAsState(initial = emptyList())
                    val songs = remember(songsRaw, artistDetailSortKey, artistDetailSortAscending) {
                        if (artistDetailSortKey == "year") {
                            sortSongsByYear(songsRaw, artistDetailSortAscending)
                        } else {
                            val base = when (artistDetailSortKey) {
                                "duration" -> songsRaw.sortedBy { it.durationMs }
                                "dateAdded" -> songsRaw.sortedBy { it.dateAdded }
                                "playCount" -> songsRaw.sortedBy { it.playCount }
                                "lastPlayedAt" -> songsRaw.sortedBy { it.lastPlayedAt ?: 0L }
                                else -> songsRaw.sortedBy { it.title.lowercase() }
                            }
                            if (artistDetailSortAscending) base else base.reversed()
                        }
                    }
                    val isFavArtist = favoriteArtists.contains(artistName)
                    val isArtistQueueActive = isQueueFullyActive(playbackState, songs, "artist:$artistName")
                    val isPlayingArtist = isArtistQueueActive && playbackState.isPlaying
                    Column {
                        GroupHeader(
                            title = artistName,
                            onBack = { drilledGroup = null },
                            onPlay = {
                                if (isArtistQueueActive) {
                                    playerController.togglePlayPause()
                                } else {
                                    playerController.requestPlayQueue(songs, 0, "artist:$artistName", artistName)
                                }
                            },
                            isPlayingThis = isPlayingArtist,
                            sortMenu = {
                                com.harmonic.player.ui.common.SortMenuButton(
                                    options = songSortOptions, selectedKey = artistDetailSortKey, ascending = artistDetailSortAscending,
                                    onSelect = { artistDetailSortKey = it }, onToggleDirection = { artistDetailSortAscending = !artistDetailSortAscending }
                                )
                            },
                            menuItems = listOf(
                                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.PlayArrow, "Tocar tudo") {
                                    playerController.requestPlayQueue(songs, 0, "artist:$artistName", artistName)
                                },
                                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.Shuffle, "Aleatório") {
                                    playerController.requestPlayQueueShuffled(songs, "artist:$artistName", artistName)
                                },
                                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.QueueMusic, "Adicionar à fila") {
                                    songs.forEach { playerController.addToQueueEnd(it) }
                                },
                                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.PlaylistAdd, "Adicionar à playlist") {
                                    bulkAddSongs = songs
                                },
                                com.harmonic.player.ui.common.ActionSheetItem(
                                    if (isFavArtist) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    if (isFavArtist) "Remover dos favoritos" else "Favoritar"
                                ) {
                                    scope.launch { dao.setArtistFavorite(artistName, !isFavArtist) }
                                },
                                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.Edit, "Renomear") {
                                    renameArtistTarget = artistName
                                },
                                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.Share, "Compartilhar") {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, "Ouvindo $artistName")
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, null))
                                },
                                com.harmonic.player.ui.common.ActionSheetItem(
                                    Icons.Filled.Delete, "Excluir",
                                    tint = com.harmonic.player.ui.common.DangerColor
                                ) {
                                    deleteConfirm = "Excluir todas as músicas de \"$artistName\"?" to {
                                        dao.deleteSongsByArtist(artistName)
                                        drilledGroup = null
                                    }
                                }
                            )
                        )
                        SongList(
                            songs = songs,
                            onSongClick = { onSongClick(songs, songs.indexOf(it), "artist:$artistName"); onOpenNowPlaying() },
                            onFavoriteToggle = { song -> scope.launch { val newValue = !song.isFavorite; dao.setFavorite(song.id, newValue); playerController.updateSongFavoriteInMemory(song.id, newValue) } },
                            dao = dao,
                            playerController = playerController,
                    onPlayNext = { playerController.playNext(it) },
                    onAddToQueueEnd = { playerController.addToQueueEnd(it) },
                            currentPlayingSongId = playbackState.currentSong?.id,
                            isPlaying = playbackState.isPlaying
                        )
                    }
                }

                selectedTab == LibraryTab.ALBUMS && drilledAlbumId == null -> {
                    val albumsRaw by dao.getAlbums().collectAsState(initial = emptyList())
                    val albums = remember(albumsRaw, albumSortKey, albumSortAscending) {
                        if (albumSortKey == "year") {
                            sortAlbumsByYear(albumsRaw, albumSortAscending)
                        } else {
                            val base = when (albumSortKey) {
                                "artist" -> albumsRaw.sortedBy { it.artist.lowercase() }
                                "trackCount" -> albumsRaw.sortedBy { it.trackCount }
                                "playCount" -> albumsRaw.sortedBy { it.playCount }
                                else -> albumsRaw.sortedBy { it.album.lowercase() }
                            }
                            if (albumSortAscending) base else base.reversed()
                        }
                    }
                    val albumListState = rememberLazyListState()
                    val albumGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                    LaunchedEffect(albumSortKey, albumSortAscending) {
                        albumListState.scrollToItem(0)
                        albumGridState.scrollToItem(0)
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (albumGridView) {
                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                            state = albumGridState,
                            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(albums, key = { it.albumId }) { album ->
                                AlbumGridCell(
                                    album = album,
                                    dao = dao,
                                    onLongClick = { quickMenuAlbum = album },
                                    onClick = {
                                        drilledGroup = album.album
                                        drilledAlbumId = album.albumId
                                        drilledAlbumArtist = album.artist
                                    }
                                )
                            }
                        }
                        com.harmonic.player.ui.common.FastScrollbarGrid(
                            gridState = albumGridState,
                            itemCount = albums.size,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    } else {
                        LazyColumn(state = albumListState, modifier = Modifier.fillMaxSize()) {
                            items(albums, key = { it.albumId }) { album ->
                                val representativeSong by produceState<Song?>(initialValue = null, key1 = album.representativeSongId) {
                                    value = dao.getSongById(album.representativeSongId)
                                }
                                ListItem(
                                    leadingContent = {
                                        com.harmonic.player.ui.common.AlbumArt(
                                            song = representativeSong,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                        )
                                    },
                                    headlineContent = {
                                        val albumRowBrush = LocalSongTitleBrush.current
                                        if (albumRowBrush != null) {
                                            Text(
                                                album.album, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                style = LocalTextStyle.current.copy(brush = albumRowBrush)
                                            )
                                        } else {
                                            Text(album.album, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
                                        }
                                    },
                                    supportingContent = {
                                        Text(
                                            "${album.artist} • ${album.trackCount} música${if (album.trackCount == 1) "" else "s"}",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = Color.White.copy(alpha = 0.55f)
                                        )
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier.compactVertical(6.dp).combinedClickable(
                                        onClick = {
                                            drilledGroup = album.album
                                            drilledAlbumId = album.albumId
                                            drilledAlbumArtist = album.artist
                                        },
                                        onLongClick = { quickMenuAlbum = album }
                                    )
                                )
                            }
                        }
                        com.harmonic.player.ui.common.FastScrollbar(
                            listState = albumListState,
                            itemCount = albums.size,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }
                    }
                }
                selectedTab == LibraryTab.ALBUMS -> {
                    val albumId = drilledAlbumId!!
                    val albumName = drilledGroup ?: ""
                    val songsRaw by dao.getSongsByAlbum(albumId).collectAsState(initial = emptyList())
                    val songs = remember(songsRaw, albumDetailSortKey, albumDetailSortAscending) {
                        val base = when (albumDetailSortKey) {
                            "title" -> songsRaw.sortedBy { it.title.lowercase() }
                            "duration" -> songsRaw.sortedBy { it.durationMs }
                            "playCount" -> songsRaw.sortedBy { it.playCount }
                            "lastPlayedAt" -> songsRaw.sortedBy { it.lastPlayedAt ?: 0L }
                            else -> songsRaw.sortedBy { it.trackNumber ?: Int.MAX_VALUE }
                        }
                        if (albumDetailSortAscending) base else base.reversed()
                    }
                    val isFavAlbum = favoriteAlbumIds.contains(albumId)
                    val isAlbumQueueActive = isQueueFullyActive(playbackState, songs, "album:$albumId")
                    val isPlayingAlbum = isAlbumQueueActive && playbackState.isPlaying
                    Column {
                        GroupHeader(
                            title = albumName,
                            subtitle = drilledAlbumArtist,
                            onBack = { drilledGroup = null; drilledAlbumId = null },
                            onPlay = {
                                if (isAlbumQueueActive) {
                                    playerController.togglePlayPause()
                                } else {
                                    playerController.requestPlayQueue(songs, 0, "album:$albumId", albumName)
                                }
                            },
                            isPlayingThis = isPlayingAlbum,
                            sortMenu = {
                                com.harmonic.player.ui.common.SortMenuButton(
                                    options = albumDetailSongSortOptions, selectedKey = albumDetailSortKey, ascending = albumDetailSortAscending,
                                    onSelect = { albumDetailSortKey = it }, onToggleDirection = { albumDetailSortAscending = !albumDetailSortAscending }
                                )
                            },
                            menuItems = listOf(
                                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.PlayArrow, "Tocar tudo") {
                                    playerController.requestPlayQueue(songs, 0, "album:$albumId", albumName)
                                },
                                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.Shuffle, "Aleatório") {
                                    playerController.requestPlayQueueShuffled(songs, "album:$albumId", albumName)
                                },
                                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.QueueMusic, "Adicionar à fila") {
                                    songs.forEach { playerController.addToQueueEnd(it) }
                                },
                                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.PlaylistAdd, "Adicionar à playlist") {
                                    bulkAddSongs = songs
                                },
                                com.harmonic.player.ui.common.ActionSheetItem(
                                    if (isFavAlbum) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    if (isFavAlbum) "Remover dos favoritos" else "Favoritar"
                                ) {
                                    scope.launch { dao.setAlbumFavorite(albumId, !isFavAlbum) }
                                },
                                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.Edit, "Renomear") {
                                    renameAlbumTarget = AlbumSummary(
                                        albumName, albumId, drilledAlbumArtist, songs.size,
                                        songs.sumOf { it.playCount }, songs.firstOrNull()?.id ?: 0L
                                    )
                                },
                                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.Share, "Compartilhar") {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, "Ouvindo o álbum $albumName")
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, null))
                                },
                                com.harmonic.player.ui.common.ActionSheetItem(
                                    Icons.Filled.Delete, "Excluir",
                                    tint = com.harmonic.player.ui.common.DangerColor
                                ) {
                                    deleteConfirm = "Excluir todas as músicas do álbum \"$albumName\"?" to {
                                        dao.deleteSongsByAlbum(albumId)
                                        drilledGroup = null
                                        drilledAlbumId = null
                                    }
                                }
                            )
                        )
                        SongList(
                            songs = songs,
                            onSongClick = { onSongClick(songs, songs.indexOf(it), "album:$albumId"); onOpenNowPlaying() },
                            onFavoriteToggle = { song -> scope.launch { val newValue = !song.isFavorite; dao.setFavorite(song.id, newValue); playerController.updateSongFavoriteInMemory(song.id, newValue) } },
                            dao = dao,
                            playerController = playerController,
                    onPlayNext = { playerController.playNext(it) },
                    onAddToQueueEnd = { playerController.addToQueueEnd(it) },
                            currentPlayingSongId = playbackState.currentSong?.id,
                            isPlaying = playbackState.isPlaying
                        )
                    }
                }

                selectedTab == LibraryTab.GENRES && drilledGroup == null -> {
                    val genres by dao.getGenres().collectAsState(initial = emptyList())
                    GroupList(items = genres) { drilledGroup = it }
                }
                selectedTab == LibraryTab.GENRES -> {
                    val songs by dao.getSongsByGenre(drilledGroup!!).collectAsState(initial = emptyList())
                    val genreName = drilledGroup!!
                    val isGenreQueueActive = isQueueFullyActive(playbackState, songs, "genre:$genreName")
                    val isPlayingGenre = isGenreQueueActive && playbackState.isPlaying
                    Column {
                        GroupHeader(
                            title = genreName,
                            onBack = { drilledGroup = null },
                            onPlay = {
                                if (isGenreQueueActive) {
                                    playerController.togglePlayPause()
                                } else {
                                    playerController.requestPlayQueue(songs, 0, "genre:$genreName", genreName)
                                }
                            },
                            isPlayingThis = isPlayingGenre,
                            // Mesmas opções que já existem em Álbuns/Artistas —
                            // antes o menu de gênero nem existia, só dava pra
                            // tocar música por música dentro dele.
                            menuItems = listOf(
                                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.PlayArrow, "Tocar tudo") {
                                    playerController.requestPlayQueue(songs, 0, "genre:$genreName", genreName)
                                },
                                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.Shuffle, "Aleatório") {
                                    playerController.requestPlayQueueShuffled(songs, "genre:$genreName", genreName)
                                },
                                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.QueueMusic, "Adicionar à fila") {
                                    songs.forEach { playerController.addToQueueEnd(it) }
                                },
                                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.PlaylistAdd, "Adicionar à playlist") {
                                    bulkAddSongs = songs
                                },
                                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.Share, "Compartilhar") {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, "Ouvindo o gênero $genreName")
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, null))
                                }
                            )
                        )
                        SongList(
                            songs = songs,
                            onSongClick = { onSongClick(songs, songs.indexOf(it), "genre:${drilledGroup ?: ""}"); onOpenNowPlaying() },
                            onFavoriteToggle = { song -> scope.launch { val newValue = !song.isFavorite; dao.setFavorite(song.id, newValue); playerController.updateSongFavoriteInMemory(song.id, newValue) } },
                            dao = dao,
                            playerController = playerController,
                    onPlayNext = { playerController.playNext(it) },
                    onAddToQueueEnd = { playerController.addToQueueEnd(it) },
                            currentPlayingSongId = playbackState.currentSong?.id,
                            isPlaying = playbackState.isPlaying
                        )
                    }
                }

                selectedTab == LibraryTab.FOLDERS && drilledGroup == null -> {
                    val folders by dao.getFolders().collectAsState(initial = emptyList())
                    FolderList(folders = folders, onLongClick = { quickMenuFolder = it }) { drilledGroup = it }
                }
                selectedTab == LibraryTab.FOLDERS -> {
                    val folder = drilledGroup!!
                    val songs by dao.getSongsByFolder(folder).collectAsState(initial = emptyList())
                    val folderTitle = folder.substringAfterLast('/')
                    val isFolderQueueActive = isQueueFullyActive(playbackState, songs, "folder:$folder")
                    val isPlayingFolder = isFolderQueueActive && playbackState.isPlaying
                    Column {
                        GroupHeader(
                            title = folderTitle,
                            onBack = { drilledGroup = null },
                            onPlay = {
                                if (isFolderQueueActive) {
                                    playerController.togglePlayPause()
                                } else {
                                    playerController.requestPlayQueue(songs, 0, "folder:$folder", folderTitle)
                                }
                            },
                            isPlayingThis = isPlayingFolder,
                            menuItems = listOf(
                                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.PlayArrow, "Tocar tudo") {
                                    playerController.requestPlayQueue(songs, 0, "folder:$folder", folder.substringAfterLast('/'))
                                },
                                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.Shuffle, "Aleatório: tudo") {
                                    playerController.requestPlayQueueShuffled(songs, "folder:$folder", folder.substringAfterLast('/'))
                                },
                                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.PlaylistAdd, "Adicionar à playlist") {
                                    bulkAddSongs = songs
                                },
                                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.VisibilityOff, "Ocultar pasta") {
                                    scope.launch { dao.hideFolder(com.harmonic.player.data.HiddenFolder(folder)) }
                                    drilledGroup = null
                                },
                                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.Share, "Compartilhar") {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, folder)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, null))
                                },
                                com.harmonic.player.ui.common.ActionSheetItem(
                                    Icons.Filled.Delete, "Excluir",
                                    tint = com.harmonic.player.ui.common.DangerColor
                                ) {
                                    deleteConfirm = "Excluir todas as músicas da pasta \"${folder.substringAfterLast('/')}\"?" to {
                                        dao.deleteSongsByFolder(folder)
                                        drilledGroup = null
                                    }
                                }
                            )
                        )
                        SongList(
                            songs = songs,
                            onSongClick = { onSongClick(songs, songs.indexOf(it), "folder:$folder"); onOpenNowPlaying() },
                            onFavoriteToggle = { song -> scope.launch { val newValue = !song.isFavorite; dao.setFavorite(song.id, newValue); playerController.updateSongFavoriteInMemory(song.id, newValue) } },
                            dao = dao,
                            playerController = playerController,
                    onPlayNext = { playerController.playNext(it) },
                    onAddToQueueEnd = { playerController.addToQueueEnd(it) },
                            currentPlayingSongId = playbackState.currentSong?.id,
                            isPlaying = playbackState.isPlaying
                        )
                    }
                }

                selectedTab == LibraryTab.PLAYLISTS -> {
                    val playlistsRaw by dao.getPlaylists().collectAsState(initial = emptyList())
                    val playlists = remember(playlistsRaw, playlistSortKey, playlistSortAscending) {
                        val base = when (playlistSortKey) {
                            "modifiedAt" -> playlistsRaw.sortedBy { it.modifiedAt }
                            "name" -> playlistsRaw.sortedBy { it.name.lowercase() }
                            else -> playlistsRaw.sortedBy { it.createdAt }
                        }
                        val ordered = if (playlistSortAscending) base else base.reversed()
                        ordered.sortedByDescending { it.isFavorite }
                    }
                    if (playlists.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Nenhuma playlist ainda. Toque no + pra criar a primeira.",
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        val playlistListState = rememberLazyListState()
                        LaunchedEffect(playlistSortKey, playlistSortAscending) {
                            playlistListState.scrollToItem(0)
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        LazyColumn(state = playlistListState, modifier = Modifier.fillMaxSize()) {
                            items(playlists, key = { it.id }) { playlist ->
                                var showPlaylistMenu by remember { mutableStateOf(false) }
                                ListItem(
                                    leadingContent = { Icon(Icons.Filled.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    headlineContent = { Text(playlist.name, color = Color.White) },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier.compactVertical(3.dp).clickable { onOpenPlaylist(playlist.id) },
                                    trailingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                                                IconButton(onClick = { showPlaylistMenu = true }) {
                                                    Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções", tint = Color.White.copy(alpha = 0.85f))
                                                }
                                                com.harmonic.player.ui.common.ActionSheet(
                                                    expanded = showPlaylistMenu,
                                                    onDismiss = { showPlaylistMenu = false },
                                                    title = playlist.name,
                                                    items = listOf(
                                                        com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.Edit, "Renomear") {
                                                            showPlaylistMenu = false
                                                            playlistForDialog = playlist
                                                            showRenamePlaylistDialog = true
                                                        },
                                                        com.harmonic.player.ui.common.ActionSheetItem(
                                                            Icons.Filled.Delete, "Excluir",
                                                            tint = com.harmonic.player.ui.common.DangerColor
                                                        ) {
                                                            showPlaylistMenu = false
                                                            playlistForDialog = playlist
                                                            showDeletePlaylistConfirm = true
                                                        }
                                                    )
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        com.harmonic.player.ui.common.FastScrollbar(
                            listState = playlistListState,
                            itemCount = playlists.size,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                        }
                    }
                }
            }
        }
    }
    } // fim do CompositionLocalProvider(LocalSongTitleBrush)

    if (showCreatePlaylistFab) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistFab = false },
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
                        scope.launch { dao.insertPlaylist(Playlist(name = newName.trim())) }
                        showCreatePlaylistFab = false
                    }
                ) { Text("Criar") }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistFab = false }) { Text("Cancelar") }
            }
        )
    }

    playlistForDialog?.let { playlist ->
        if (showRenamePlaylistDialog) {
            var newName by remember { mutableStateOf(playlist.name) }
            AlertDialog(
                onDismissRequest = { showRenamePlaylistDialog = false; playlistForDialog = null },
                title = { Text("Renomear playlist") },
                text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true) },
                confirmButton = {
                    TextButton(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            scope.launch { dao.renamePlaylist(playlist.id, newName.trim()) }
                            showRenamePlaylistDialog = false
                            playlistForDialog = null
                        }
                    ) { Text("Salvar") }
                },
                dismissButton = {
                    TextButton(onClick = { showRenamePlaylistDialog = false; playlistForDialog = null }) { Text("Cancelar") }
                }
            )
        }

        if (showDeletePlaylistConfirm) {
            AlertDialog(
                onDismissRequest = { showDeletePlaylistConfirm = false; playlistForDialog = null },
                title = { Text("Excluir playlist?") },
                text = { Text("\"${playlist.name}\" será excluída. As músicas continuam na sua biblioteca.") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch { dao.deletePlaylist(playlist.id) }
                        showDeletePlaylistConfirm = false
                        playlistForDialog = null
                    }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeletePlaylistConfirm = false; playlistForDialog = null }) { Text("Cancelar") }
                }
            )
        }
    }

    // Excluir todas as músicas de um artista/álbum/pasta de uma vez —
    // reaproveitado pelos 3 menus, já que a confirmação é sempre igual:
    // uma mensagem e uma ação suspend pra rodar se o usuário confirmar.
    deleteConfirm?.let { (message, action) ->
        AlertDialog(
            onDismissRequest = { deleteConfirm = null },
            title = { Text("Excluir?") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { action() }
                    deleteConfirm = null
                }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = null }) { Text("Cancelar") }
            }
        )
    }

    // Adicionar todas as músicas de um artista/álbum/pasta a uma playlist —
    // mesma ideia do seletor de playlist por música, só que em lote.
    bulkAddSongs?.let { songs ->
        val playlists by dao.getPlaylists().collectAsState(initial = emptyList())
        var showCreate by remember { mutableStateOf(false) }
        if (!showCreate) {
            AlertDialog(
                onDismissRequest = { bulkAddSongs = null },
                title = { Text("Adicionar ${songs.size} música(s) a qual playlist?") },
                text = {
                    Column {
                        if (playlists.isEmpty()) Text("Nenhuma playlist ainda.")
                        playlists.forEach { playlist ->
                            ListItem(
                                headlineContent = { Text(playlist.name) },
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        val startPos = dao.getPlaylistSongs(playlist.id).first().size
                                        songs.forEachIndexed { index, s ->
                                            dao.addToPlaylist(PlaylistSongCrossRef(playlist.id, s.id, startPos + index))
                                        }
                                        dao.touchPlaylist(playlist.id)
                                    }
                                    bulkAddSongs = null
                                }
                            )
                        }
                        ListItem(
                            leadingContent = { Icon(Icons.Filled.PlaylistAdd, contentDescription = null) },
                            headlineContent = { Text("Nova playlist...") },
                            modifier = Modifier.clickable { showCreate = true }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { bulkAddSongs = null }) { Text("Fechar") }
                }
            )
        } else {
            var newName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { bulkAddSongs = null },
                title = { Text("Nova playlist") },
                text = {
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true)
                },
                confirmButton = {
                    TextButton(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            scope.launch {
                                val newId = dao.insertPlaylist(Playlist(name = newName.trim()))
                                songs.forEachIndexed { index, s ->
                                    dao.addToPlaylist(PlaylistSongCrossRef(newId, s.id, index))
                                }
                            }
                            bulkAddSongs = null
                        }
                    ) { Text("Criar e adicionar") }
                },
                dismissButton = {
                    TextButton(onClick = { bulkAddSongs = null }) { Text("Cancelar") }
                }
            )
        }
    }

    renameArtistTarget?.let { artistName ->
        var newName by remember { mutableStateOf(artistName) }
        AlertDialog(
            onDismissRequest = { renameArtistTarget = null },
            title = { Text("Renomear artista") },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true) },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        scope.launch { dao.renameArtist(artistName, newName.trim()) }
                        drilledGroup = newName.trim()
                        renameArtistTarget = null
                    }
                ) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(onClick = { renameArtistTarget = null }) { Text("Cancelar") }
            }
        )
    }

    renameAlbumTarget?.let { album ->
        var newName by remember { mutableStateOf(album.album) }
        AlertDialog(
            onDismissRequest = { renameAlbumTarget = null },
            title = { Text("Renomear álbum") },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true) },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        scope.launch { dao.renameAlbum(album.albumId, newName.trim()) }
                        drilledGroup = newName.trim()
                        renameAlbumTarget = null
                    }
                ) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(onClick = { renameAlbumTarget = null }) { Text("Cancelar") }
            }
        )
    }
    // Menus rápidos: abrem com um clique longo num álbum/artista/pasta,
    // sem precisar entrar na página dele só pra ver as opções — os mesmos
    // itens que já existem lá dentro (Tocar tudo, Shuffle, Renomear...).
    quickMenuAlbum?.let { album ->
        val songs by dao.getSongsByAlbum(album.albumId).collectAsState(initial = emptyList())
        val isFavAlbum = favoriteAlbumIds.contains(album.albumId)
        com.harmonic.player.ui.common.ActionSheet(
            expanded = true,
            onDismiss = { quickMenuAlbum = null },
            title = album.album,
            subtitle = album.artist,
            items = listOf(
                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.PlayArrow, "Tocar tudo") {
                    playerController.requestPlayQueue(songs, 0, "album:${album.albumId}", album.album)
                    quickMenuAlbum = null
                },
                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.Shuffle, "Aleatório") {
                    playerController.requestPlayQueueShuffled(songs, "album:${album.albumId}", album.album)
                    quickMenuAlbum = null
                },
                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.QueueMusic, "Adicionar à fila") {
                    songs.forEach { playerController.addToQueueEnd(it) }
                    quickMenuAlbum = null
                },
                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.PlaylistAdd, "Adicionar à playlist") {
                    bulkAddSongs = songs
                    quickMenuAlbum = null
                },
                com.harmonic.player.ui.common.ActionSheetItem(
                    if (isFavAlbum) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    if (isFavAlbum) "Remover dos favoritos" else "Favoritar"
                ) {
                    scope.launch { dao.setAlbumFavorite(album.albumId, !isFavAlbum) }
                    quickMenuAlbum = null
                },
                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.Edit, "Renomear") {
                    renameAlbumTarget = album
                    quickMenuAlbum = null
                },
                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.Share, "Compartilhar") {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, "Ouvindo o álbum ${album.album}")
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, null))
                    quickMenuAlbum = null
                },
                com.harmonic.player.ui.common.ActionSheetItem(
                    Icons.Filled.Delete, "Excluir",
                    tint = com.harmonic.player.ui.common.DangerColor
                ) {
                    deleteConfirm = "Excluir todas as músicas do álbum \"${album.album}\"?" to {
                        dao.deleteSongsByAlbum(album.albumId)
                    }
                    quickMenuAlbum = null
                }
            )
        )
    }

    quickMenuArtist?.let { artistName ->
        val songs by dao.getSongsByArtist(artistName).collectAsState(initial = emptyList())
        val isFavArtist = favoriteArtists.contains(artistName)
        com.harmonic.player.ui.common.ActionSheet(
            expanded = true,
            onDismiss = { quickMenuArtist = null },
            title = artistName,
            items = listOf(
                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.PlayArrow, "Tocar tudo") {
                    playerController.requestPlayQueue(songs, 0, "artist:$artistName", artistName)
                    quickMenuArtist = null
                },
                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.Shuffle, "Aleatório") {
                    playerController.requestPlayQueueShuffled(songs, "artist:$artistName", artistName)
                    quickMenuArtist = null
                },
                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.QueueMusic, "Adicionar à fila") {
                    songs.forEach { playerController.addToQueueEnd(it) }
                    quickMenuArtist = null
                },
                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.PlaylistAdd, "Adicionar à playlist") {
                    bulkAddSongs = songs
                    quickMenuArtist = null
                },
                com.harmonic.player.ui.common.ActionSheetItem(
                    if (isFavArtist) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    if (isFavArtist) "Remover dos favoritos" else "Favoritar"
                ) {
                    scope.launch { dao.setArtistFavorite(artistName, !isFavArtist) }
                    quickMenuArtist = null
                },
                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.Edit, "Renomear") {
                    renameArtistTarget = artistName
                    quickMenuArtist = null
                },
                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.Share, "Compartilhar") {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, "Ouvindo $artistName")
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, null))
                    quickMenuArtist = null
                },
                com.harmonic.player.ui.common.ActionSheetItem(
                    Icons.Filled.Delete, "Excluir",
                    tint = com.harmonic.player.ui.common.DangerColor
                ) {
                    deleteConfirm = "Excluir todas as músicas de \"$artistName\"?" to {
                        dao.deleteSongsByArtist(artistName)
                    }
                    quickMenuArtist = null
                }
            )
        )
    }

    quickMenuFolder?.let { folder ->
        val songs by dao.getSongsByFolder(folder).collectAsState(initial = emptyList())
        val folderName = folder.substringAfterLast('/')
        com.harmonic.player.ui.common.ActionSheet(
            expanded = true,
            onDismiss = { quickMenuFolder = null },
            title = folderName,
            items = listOf(
                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.PlayArrow, "Tocar tudo") {
                    playerController.requestPlayQueue(songs, 0, "folder:$folder", folderName)
                    quickMenuFolder = null
                },
                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.Shuffle, "Aleatório: tudo") {
                    playerController.requestPlayQueueShuffled(songs, "folder:$folder", folderName)
                    quickMenuFolder = null
                },
                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.PlaylistAdd, "Adicionar à playlist") {
                    bulkAddSongs = songs
                    quickMenuFolder = null
                },
                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.VisibilityOff, "Ocultar pasta") {
                    scope.launch { dao.hideFolder(com.harmonic.player.data.HiddenFolder(folder)) }
                    quickMenuFolder = null
                },
                com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.Share, "Compartilhar") {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, folder)
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, null))
                    quickMenuFolder = null
                },
                com.harmonic.player.ui.common.ActionSheetItem(
                    Icons.Filled.Delete, "Excluir",
                    tint = com.harmonic.player.ui.common.DangerColor
                ) {
                    deleteConfirm = "Excluir todas as músicas da pasta \"$folderName\"?" to {
                        dao.deleteSongsByFolder(folder)
                    }
                    quickMenuFolder = null
                }
            )
        )
    }

    if (showDurationFilterDialog) {
        var minInput by remember { mutableStateOf(if (minDurationFilterSec > 0) (minDurationFilterSec / 60).toString() else "") }
        var maxInput by remember { mutableStateOf(if (maxDurationFilterSec > 0) (maxDurationFilterSec / 60).toString() else "") }
        AlertDialog(
            onDismissRequest = { showDurationFilterDialog = false },
            title = { Text("Filtrar por duração") },
            text = {
                Column {
                    Text(
                        "Só mostra músicas dentro dessa faixa de duração (em minutos). Deixe em branco pra não limitar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = minInput,
                            onValueChange = { minInput = it.filter { c -> c.isDigit() } },
                            label = { Text("Mín. (min)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        OutlinedTextField(
                            value = maxInput,
                            onValueChange = { maxInput = it.filter { c -> c.isDigit() } },
                            label = { Text("Máx. (min)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    minDurationFilterSec = (minInput.toIntOrNull() ?: 0) * 60
                    maxDurationFilterSec = (maxInput.toIntOrNull() ?: 0) * 60
                    showDurationFilterDialog = false
                }) { Text("Aplicar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    minDurationFilterSec = 0
                    maxDurationFilterSec = 0
                    showDurationFilterDialog = false
                }) { Text("Limpar filtro") }
            }
        )
    }
}

/**
 * Verifica se ESSA lista específica de músicas é literalmente a fila que
 * está tocando agora — não só se o `sourceKey` bate. Antes só o sourceKey
 * era checado, e como tocar uma música avulsa da lista de Músicas usa o
 * mesmo sourceKey "songs" da lista inteira (idem "artist:X", "album:Y"),
 * os botões "Tocar tudo"/play do cabeçalho achavam que a lista inteira já
 * estava tocando (e só davam pause/resume) quando na verdade só uma
 * música avulsa clicada na lista estava na fila — resultado: pausar essa
 * música avulsa e tocar em "Tocar tudo" só retomava ela, em vez de tocar
 * a lista inteira de verdade como devia.
 */
private fun isQueueFullyActive(state: PlaybackUiState, songs: List<Song>, sourceKey: String): Boolean =
    state.sourceKey == sourceKey &&
        state.queue.size == songs.size &&
        state.queue.map { it.id }.toSet() == songs.map { it.id }.toSet()

@Composable
private fun GroupHeader(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    menuItems: List<com.harmonic.player.ui.common.ActionSheetItem>? = null,
    onPlay: (() -> Unit)? = null,
    isPlayingThis: Boolean = false,
    sortMenu: (@Composable () -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // Botão de play dedicado + "ordenar por", do lado dos "⋮" — antes
        // essas ações só existiam escondidas dentro do menu de três
        // pontinhos ("Tocar tudo"), exigindo um toque a mais só pra tocar
        // o artista/álbum inteiro.
        if (onPlay != null) {
            IconButton(onClick = onPlay) {
                Icon(
                    if (isPlayingThis) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlayingThis) "Pausar" else "Tocar",
                    tint = if (isPlayingThis) MaterialTheme.colorScheme.primary else Color.White
                )
            }
        }
        sortMenu?.invoke()
        if (menuItems != null) {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções", tint = Color.White)
                }
                com.harmonic.player.ui.common.ActionSheet(
                    expanded = showMenu,
                    onDismiss = { showMenu = false },
                    title = title,
                    items = menuItems.map { item -> item.copy(onClick = { showMenu = false; item.onClick() }) }
                )
            }
        }
    }
}

@Composable
private fun GroupList(items: List<String>, onClick: (String) -> Unit) {
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(items, key = { it }) { name ->
            ListItem(
                headlineContent = {
                    val groupTitleBrush = LocalSongTitleBrush.current
                    if (groupTitleBrush != null) {
                        Text(
                            name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = LocalTextStyle.current.copy(brush = groupTitleBrush)
                        )
                    } else {
                        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.compactVertical(6.dp).clickable { onClick(name) }
            )
        }
    }
    com.harmonic.player.ui.common.FastScrollbar(
        listState = listState,
        itemCount = items.size,
        modifier = Modifier.align(Alignment.CenterEnd)
    )
    }
}

/**
 * Lista de pastas com ícone na cor de destaque e o NOME da pasta em
 * destaque acima do caminho completo (antes só aparecia o caminho).
 */
@Composable
private fun FolderList(folders: List<String>, onLongClick: (String) -> Unit = {}, onClick: (String) -> Unit) {
    val accentColor = MaterialTheme.colorScheme.primary
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(folders, key = { it }) { folder ->
            val folderName = folder.trimEnd('/').substringAfterLast('/').ifBlank { folder }
            ListItem(
                leadingContent = {
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = accentColor)
                },
                headlineContent = {
                    val folderTitleBrush = LocalSongTitleBrush.current
                    if (folderTitleBrush != null) {
                        Text(
                            folderName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = LocalTextStyle.current.copy(brush = folderTitleBrush)
                        )
                    } else {
                        Text(folderName, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
                    }
                },
                supportingContent = {
                    Text(folder, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White.copy(alpha = 0.55f))
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.combinedClickable(onClick = { onClick(folder) }, onLongClick = { onLongClick(folder) })
            )
        }
    }
    com.harmonic.player.ui.common.FastScrollbar(
        listState = listState,
        itemCount = folders.size,
        modifier = Modifier.align(Alignment.CenterEnd)
    )
    }
}

/** Uma linha de artista com foto (capa da primeira música dele) e contagem de músicas/álbuns. */
@Composable
private fun ArtistRow(artist: ArtistSummary, dao: SongDao, onLongClick: () -> Unit = {}, onClick: () -> Unit) {
    var sampleSong by remember(artist.name) { mutableStateOf<Song?>(null) }
    LaunchedEffect(artist.name) {
        sampleSong = dao.getFirstSongForArtist(artist.name)
    }
    ListItem(
        modifier = Modifier.compactVertical(6.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            com.harmonic.player.ui.common.AlbumArt(
                song = sampleSong,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                placeholderShape = CircleShape
            )
        },
        headlineContent = {
            val titleBrush = LocalSongTitleBrush.current
            if (titleBrush != null) {
                Text(
                    artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = LocalTextStyle.current.copy(brush = titleBrush)
                )
            } else {
                Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
            }
        },
        supportingContent = {
            Text(
                "${artist.songCount} música(s) • ${artist.albumCount} álbum(ns)",
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    )
}

/** Célula da grade de artistas: foto circular grande + nome + contagens embaixo. */
@Composable
private fun ArtistGridCell(artist: ArtistSummary, dao: SongDao, onLongClick: () -> Unit = {}, onClick: () -> Unit) {
    var sampleSong by remember(artist.name) { mutableStateOf<Song?>(null) }
    LaunchedEffect(artist.name) {
        sampleSong = dao.getFirstSongForArtist(artist.name)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        com.harmonic.player.ui.common.AlbumArt(
            song = sampleSong,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape),
            placeholderShape = CircleShape
        )
        Spacer(Modifier.height(6.dp))
        val artistTitleBrush = LocalSongTitleBrush.current
        if (artistTitleBrush != null) {
            Text(
                artist.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(brush = artistTitleBrush)
            )
        } else {
            Text(
                artist.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            "${artist.songCount} música(s) • ${artist.albumCount} álbum(ns)",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color.White.copy(alpha = 0.55f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/** Uma célula da grade de álbuns: busca a capa da primeira música do álbum sob demanda. */
@Composable
private fun AlbumGridCell(album: AlbumSummary, dao: SongDao, onLongClick: () -> Unit = {}, onClick: () -> Unit) {
    var sampleSong by remember(album.albumId) { mutableStateOf<Song?>(null) }
    LaunchedEffect(album.albumId) {
        sampleSong = dao.getFirstSongForAlbum(album.albumId)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        com.harmonic.player.ui.common.AlbumArt(
            song = sampleSong,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.height(6.dp))
        val albumTitleBrush = LocalSongTitleBrush.current
        if (albumTitleBrush != null) {
            Text(
                album.album,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(brush = albumTitleBrush)
            )
        } else {
            Text(
                album.album,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            album.artist,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color.White.copy(alpha = 0.55f),
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            "${album.trackCount} música${if (album.trackCount == 1) "" else "s"}",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color.White.copy(alpha = 0.4f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun SongList(
    songs: List<Song>,
    dao: com.harmonic.player.data.SongDao,
    playerController: PlayerController,
    onSongClick: (Song) -> Unit,
    onFavoriteToggle: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueueEnd: (Song) -> Unit,
    currentPlayingSongId: Long? = null,
    isPlaying: Boolean = false,
    sortSignature: String = ""
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    // Toca na capa de uma música pra marcar/desmarcar ela pra uma ação em
    // lote (fila, favoritos, playlist) — reseta se a lista em si mudar
    // (ex: trocou de aba), pra não ficar seleção "fantasma" de outra lista.
    var selectedIds by remember(songs) { mutableStateOf(emptySet<Long>()) }
    var showPlaylistPickerForSelection by remember { mutableStateOf(false) }
    var showDeleteSelectedConfirm by remember { mutableStateOf(false) }
    val selectedSongs = remember(songs, selectedIds) { songs.filter { it.id in selectedIds } }

    // Exclusão em lote — Android 11+ (API 30) mostra UM ÚNICO diálogo de
    // confirmação do sistema pra todas as músicas de uma vez
    // (createDeleteRequest aceita uma lista de Uris), em vez de precisar
    // aprovar uma por uma. Aprovar aqui já apaga o arquivo de verdade — a
    // limpeza do banco local roda depois (tanto otimista, logo abaixo,
    // quanto de qualquer forma pelo próprio scan reativo do MediaStore).
    val batchDeleteLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val idsToRemove = selectedSongs.map { it.id }
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                idsToRemove.forEach { dao.deleteSongById(it) }
            }
            selectedIds = emptySet()
        }
    }

    fun deleteSelectedSongs() {
        val toDelete = selectedSongs
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val uris = toDelete.map { song ->
                android.content.ContentUris.withAppendedId(
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.mediaStoreId
                )
            }
            try {
                uris.forEach { context.contentResolver.delete(it, null, null) }
                toDelete.forEach { dao.deleteSongById(it.id) }
                selectedIds = emptySet()
            } catch (e: SecurityException) {
                // Android 10 e anteriores: RecoverableSecurityException é por
                // item, não dá pra agrupar — cai pro fluxo em lote do 11+.
                val intentSender = if (android.os.Build.VERSION.SDK_INT >= 30) {
                    android.provider.MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
                } else {
                    (e as? android.app.RecoverableSecurityException)?.userAction?.actionIntent?.intentSender
                }
                intentSender?.let {
                    batchDeleteLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(it).build())
                }
            } catch (e: Exception) { /* nada a fazer, evita derrubar o app */ }
        }
    }

    val listState = rememberLazyListState()
    // Sem isso, trocar o critério ou a direção da ordenação (ex: pra
    // "Decrescente") fazia a lista "pular" pro fim da tela sozinha: o
    // Compose tenta manter o mesmo item visível pela chave dele (o id da
    // música) — como esse item, que antes estava no topo, passa a ficar
    // perto do final depois de inverter a ordem, ele rolava a tela até lá
    // achando que estava "preservando a posição". Voltando pro topo
    // manualmente sempre que o critério de ordenação muda, isso não acontece.
    LaunchedEffect(sortSignature) {
        if (sortSignature.isNotEmpty()) listState.scrollToItem(0)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectedIds.isNotEmpty()) {
            SelectionActionBar(
                count = selectedIds.size,
                onClose = { selectedIds = emptySet() },
                onAddToQueue = {
                    selectedSongs.forEach { onAddToQueueEnd(it) }
                    selectedIds = emptySet()
                },
                onFavorite = {
                    scope.launch { selectedSongs.forEach { dao.setFavorite(it.id, true); playerController.updateSongFavoriteInMemory(it.id, true) } }
                    selectedIds = emptySet()
                },
                onAddToPlaylist = { showPlaylistPickerForSelection = true },
                onDelete = { showDeleteSelectedConfirm = true }
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(songs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        dao = dao,
                        onClick = { onSongClick(song) },
                        onFavoriteToggle = { onFavoriteToggle(song) },
                        onPlayNext = { onPlayNext(song) },
                        onAddToQueueEnd = { onAddToQueueEnd(song) },
                        isCurrentlyPlaying = song.id == currentPlayingSongId,
                        isPlaying = isPlaying,
                        isSelected = song.id in selectedIds,
                        selectionMode = selectedIds.isNotEmpty(),
                        onToggleSelect = {
                            selectedIds = if (song.id in selectedIds) selectedIds - song.id else selectedIds + song.id
                        },
                        onLongClick = {
                            if (song.id !in selectedIds) selectedIds = selectedIds + song.id
                        }
                    )
                }
            }
            com.harmonic.player.ui.common.FastScrollbar(
                listState = listState,
                itemCount = songs.size,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }

    if (showPlaylistPickerForSelection) {
        val playlists by dao.getPlaylists().collectAsState(initial = emptyList())
        var showCreateFromSelection by remember { mutableStateOf(false) }
        if (!showCreateFromSelection) {
            AlertDialog(
                onDismissRequest = { showPlaylistPickerForSelection = false },
                title = { Text("Adicionar ${selectedIds.size} música(s) a qual playlist?") },
                text = {
                    Column {
                        if (playlists.isEmpty()) {
                            Text("Nenhuma playlist ainda.")
                        }
                        playlists.forEach { playlist ->
                            ListItem(
                                headlineContent = { Text(playlist.name) },
                                modifier = Modifier.clickable {
                                    val songsToAdd = selectedSongs
                                    scope.launch {
                                        var pos = dao.getPlaylistSongs(playlist.id).first().size
                                        songsToAdd.forEach { song ->
                                            dao.addToPlaylist(com.harmonic.player.data.PlaylistSongCrossRef(playlist.id, song.id, pos))
                                            pos++
                                        }
                                        dao.touchPlaylist(playlist.id)
                                    }
                                    showPlaylistPickerForSelection = false
                                    selectedIds = emptySet()
                                }
                            )
                        }
                        ListItem(
                            leadingContent = { Icon(Icons.Filled.PlaylistAdd, contentDescription = null) },
                            headlineContent = { Text("Nova playlist...") },
                            modifier = Modifier.clickable { showCreateFromSelection = true }
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showPlaylistPickerForSelection = false }) { Text("Cancelar") }
                }
            )
        } else {
            var newPlaylistName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showPlaylistPickerForSelection = false },
                title = { Text("Nova playlist") },
                text = {
                    OutlinedTextField(value = newPlaylistName, onValueChange = { newPlaylistName = it }, singleLine = true)
                },
                confirmButton = {
                    TextButton(
                        enabled = newPlaylistName.isNotBlank(),
                        onClick = {
                            val songsToAdd = selectedSongs
                            scope.launch {
                                val newId = dao.insertPlaylist(Playlist(name = newPlaylistName.trim()))
                                songsToAdd.forEachIndexed { index, s ->
                                    dao.addToPlaylist(PlaylistSongCrossRef(newId, s.id, index))
                                }
                            }
                            showPlaylistPickerForSelection = false
                            selectedIds = emptySet()
                        }
                    ) { Text("Criar e adicionar") }
                },
                dismissButton = {
                    TextButton(onClick = { showPlaylistPickerForSelection = false }) { Text("Cancelar") }
                }
            )
        }
    }

    if (showDeleteSelectedConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedConfirm = false },
            title = { Text("Excluir ${selectedIds.size} música(s)?") },
            text = { Text("Isso apaga os arquivos de verdade do aparelho, não só da lista. Não dá pra desfazer.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteSelectedConfirm = false
                    deleteSelectedSongs()
                }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}

/**
 * Barra que aparece no topo da lista assim que 1+ músicas são selecionadas
 * (tocando na capa) — deixa fazer em lote as mesmas 3 ações do menu "..."
 * de uma música só: fila, favoritos, playlist.
 */
@Composable
private fun SelectionActionBar(
    count: Int,
    onClose: () -> Unit,
    onAddToQueue: () -> Unit,
    onFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Cancelar seleção", tint = Color.White)
        }
        Text(
            "$count selecionada(s)",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onAddToQueue) {
            Icon(Icons.Filled.QueueMusic, contentDescription = "Adicionar à fila", tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onFavorite) {
            Icon(Icons.Filled.Favorite, contentDescription = "Favoritar", tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onAddToPlaylist) {
            Icon(Icons.Filled.PlaylistAdd, contentDescription = "Adicionar à playlist", tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Excluir", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * Três barrinhas verticais que sobem e descem em loop, tipo um equalizer de
 * verdade — cada barra com sua própria duração/fase, pra não ficarem
 * "batendo" juntas de forma óbvia e mecânica. Só é chamado quando a música
 * está de fato tocando (ver SongRow); quando pausada, cai no ícone estático.
 */
@Composable
internal fun AnimatedEqualizerBars(color: Color, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "eq")
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "eqBar1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(560, easing = LinearEasing), RepeatMode.Reverse),
        label = "eqBar2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(340, easing = LinearEasing), RepeatMode.Reverse),
        label = "eqBar3"
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(bar1, bar2, bar3).forEach { fraction ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(fraction.coerceIn(0.15f, 1f))
                    .background(color, RoundedCornerShape(1.dp))
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SongRow(
    song: Song,
    dao: com.harmonic.player.data.SongDao,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueueEnd: () -> Unit,
    isCurrentlyPlaying: Boolean = false,
    isPlaying: Boolean = false,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val accentColor = MaterialTheme.colorScheme.primary
    // Estado do menu de opções vive aqui, na própria linha — assim o menu
    // abre coladinho no ícone que o aciona, alinhado ao lado dele, em vez
    // de subir do rodapé da tela como antes.
    var showOptions by remember { mutableStateOf(false) }

    ListItem(
        leadingContent = {
            // Clique longo em qualquer parte da linha entra no "modo
            // seleção" (barra de ações no topo pra fila/favoritos/
            // playlist) — um "check" verde some por cima da capa quando
            // selecionada, e enquanto esse modo estiver ativo, tocar na
            // linha marca/desmarca em vez de tocar a música.
            Box(contentAlignment = Alignment.Center) {
                com.harmonic.player.ui.common.AlbumArt(
                    song = song,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .clickable(onClick = onToggleSelect)
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                    )
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Selecionada",
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        headlineContent = {
            val titleBrush = LocalSongTitleBrush.current
            if (titleBrush != null && !isCurrentlyPlaying) {
                // Gradiente só no título; a música tocando no momento
                // continua com a cor de destaque sólida, pra não perder o
                // "qual música está tocando agora" que o gradiente ia diluir.
                Text(
                    song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = LocalTextStyle.current.copy(brush = titleBrush)
                )
            } else {
                Text(
                    song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrentlyPlaying) accentColor else Color.White
                )
            }
        },
        supportingContent = {
            Text(
                "${formatDuration(song.durationMs)} • ${song.artist}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrentlyPlaying) accentColor.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.7f)
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Indica visualmente qual música da lista está tocando
                // agora — sem isso, era impossível saber só olhando a lista.
                if (isCurrentlyPlaying) {
                    if (isPlaying) {
                        AnimatedEqualizerBars(
                            color = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Pause,
                            contentDescription = "Pausado",
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                }
                IconButton(onClick = onFavoriteToggle, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = if (song.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favoritar",
                        tint = if (song.isFavorite) accentColor else Color.White.copy(alpha = 0.7f)
                    )
                }
                // Ícone de menu à direita da linha — abre as opções dessa
                // música (tocar em seguida, playlist, cortar, etc).
                Box {
                    IconButton(onClick = { showOptions = true }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Mais opções",
                            tint = Color.White.copy(alpha = 0.75f)
                        )
                    }
                    if (showOptions) {
                        SongOptionsSheet(
                            song = song,
                            dao = dao,
                            onDismiss = { showOptions = false },
                            onPlayNext = { showOptions = false; onPlayNext() },
                            onAddToQueueEnd = { showOptions = false; onAddToQueueEnd() }
                        )
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .compactVertical(6.dp)
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() else onClick() },
                onLongClick = onLongClick
            )
    )
}

/**
 * Bottom sheet de opções aberto com toque longo numa música — reúne todas
 * as ações possíveis: fila, playlist, corte, renomear, capa, toque de
 * chamada, compartilhar, excluir, nome do arquivo, propriedades e ocultar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongOptionsSheet(
    song: Song,
    dao: com.harmonic.player.data.SongDao,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueueEnd: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showActionSheet by remember { mutableStateOf(true) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showEditFileNameDialog by remember { mutableStateOf(false) }
    var showTrimDialog by remember { mutableStateOf(false) }
    var showEditTagsDialog by remember { mutableStateOf(false) }
    var showPropertiesDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val songUri = remember(song.mediaStoreId) {
        android.content.ContentUris.withAppendedId(
            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.mediaStoreId
        )
    }

    // Escolher uma imagem da galeria pra usar como capa — OpenDocument (em
    // vez de GetContent) porque suporta permissão persistente: sem isso, a
    // capa sumiria depois que o app fosse reiniciado.
    val coverPickerLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) { /* alguns provedores não suportam; a capa ainda funciona nesta sessão */ }
            scope.launch { dao.setCustomCover(song.id, uri.toString()) }
        }
        onDismiss()
    }

    // Excluir/renomear o arquivo de verdade (não só o registro no app) pode
    // pedir confirmação do sistema em Android 10+ — esse launcher recebe
    // essa resposta. Guardamos o valor pendente porque, sem isso, aprovar
    // a permissão na tela do sistema não tentava a mudança de novo sozinho
    // — o usuário aprovava e nada acontecia.
    //
    // Esse launcher atende tanto renomear quanto excluir, então guardamos
    // qual dos dois está pendente pra saber o que fazer quando o resultado
    // chega — antes só existia pendingRename, então aprovar a EXCLUSÃO no
    // diálogo do sistema não fazia nada (o registro ficava órfão no banco).
    var pendingRename by remember { mutableStateOf<Pair<android.content.ContentValues, String>?>(null) }
    var pendingDelete by remember { mutableStateOf(false) }
    val securityLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val rename = pendingRename
        val wasDelete = pendingDelete
        pendingRename = null
        pendingDelete = false
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            if (rename != null) {
                val (values, newPath) = rename
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        context.contentResolver.update(songUri, values, null, null)
                        dao.updateSongPath(song.id, newPath)
                        android.media.MediaScannerConnection.scanFile(context, arrayOf(song.path, newPath), null, null)
                    } catch (e: Exception) { /* usuário pode ter negado; ignora */ }
                }
            } else if (wasDelete) {
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    // No Android 11+ o próprio sistema já apaga o arquivo ao
                    // aprovar o createDeleteRequest; em versões anteriores
                    // (RecoverableSecurityException) precisa tentar de novo
                    // agora que a permissão foi concedida. Nos dois casos, o
                    // registro local também precisa sair do banco.
                    try {
                        if (android.os.Build.VERSION.SDK_INT < 30) {
                            context.contentResolver.delete(songUri, null, null)
                        }
                    } catch (e: Exception) { /* já pode ter sido apagado pelo sistema */ }
                    dao.deleteSongById(song.id)
                }
            }
        }
        onDismiss()
    }

    // Salvar tags grava direto no arquivo via jaudiotagger — não passa pelo
    // MediaStore, então o pedido de permissão via createWriteRequest não
    // adianta aqui (isso só libera acesso por Uri/FD do MediaStore, e
    // jaudiotagger usa java.io.File puro). Em Android 10+, escrever direto
    // no arquivo de outro app exige a permissão especial "Acesso a todos os
    // arquivos" (MANAGE_EXTERNAL_STORAGE) — sem ela, a escrita falhava
    // (IOException, não SecurityException) e o "Salvar" parecia não fazer
    // nada. Em Android 9 e anteriores, basta a permissão comum de escrita.
    var pendingTagValues by remember { mutableStateOf<com.harmonic.player.data.TagEditor.TagValues?>(null) }
    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        val values = pendingTagValues
        pendingTagValues = null
        if (granted && values != null) {
            scope.launch { saveTagsToFileAndDb(context, dao, song, values) }
        } else if (values != null) {
            android.widget.Toast.makeText(context, "Permissão negada — as tags não foram salvas", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    /** Confere/pede a permissão de arquivo certa pra essa versão do Android antes de gravar. */
    suspend fun saveTags(values: com.harmonic.player.data.TagEditor.TagValues) {
        val sdk = android.os.Build.VERSION.SDK_INT
        val hasFileAccess = when {
            sdk >= 30 -> android.os.Environment.isExternalStorageManager()
            sdk >= 29 -> true // requestLegacyExternalStorage no manifesto já cobre o Android 10
            else -> androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (!hasFileAccess) {
            pendingTagValues = values
            if (sdk >= 30) {
                val intent = try {
                    android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        android.net.Uri.parse("package:${context.packageName}")
                    )
                } catch (e: Exception) {
                    android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                }
                context.startActivity(intent)
                android.widget.Toast.makeText(
                    context,
                    "Ative \"Permitir acesso a todos os arquivos\" pro Music Box e depois toque em Salvar de novo",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } else {
                legacyStoragePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            return
        }
        saveTagsToFileAndDb(context, dao, song, values)
    }

    // onDismiss() só roda DEPOIS do bloco terminar — mesmo motivo da
    // correção em "Editar nome do arquivo": se o sheet fosse fechado logo
    // após o scope.launch(), a composable (e o securityLauncher registrado
    // nela) já tinha saído de cena antes do IntentSender do sistema ser
    // lançado, e o launch() batia num launcher desregistrado
    // (IllegalStateException: "Attempting to launch an unregistered
    // ActivityResultLauncher").
    fun deleteSong() {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                context.contentResolver.delete(songUri, null, null)
                dao.deleteSongById(song.id)
            } catch (e: SecurityException) {
                val intentSender = if (android.os.Build.VERSION.SDK_INT >= 30) {
                    android.provider.MediaStore.createDeleteRequest(context.contentResolver, listOf(songUri)).intentSender
                } else {
                    (e as? android.app.RecoverableSecurityException)?.userAction?.actionIntent?.intentSender
                }
                if (intentSender != null) {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        pendingDelete = true
                        securityLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(intentSender).build())
                    }
                    // Não fecha o sheet aqui: o securityLauncher precisa
                    // continuar registrado até o usuário responder ao pedido
                    // de permissão do sistema — quem fecha o sheet depois é
                    // o próprio callback do securityLauncher.
                    return@launch
                }
            } catch (e: Exception) { /* nada a fazer, evita derrubar o app */ }
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                showDeleteConfirm = false
                onDismiss()
            }
        }
    }

    fun shareSong() {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(android.content.Intent.EXTRA_STREAM, songUri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Compartilhar música"))
    }

    fun setAsRingtone() {
        if (!android.provider.Settings.System.canWrite(context)) {
            // Precisa de uma permissão especial do sistema — abre a tela
            // certa em vez de simplesmente falhar em silêncio.
            context.startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    android.net.Uri.parse("package:${context.packageName}")
                )
            )
            return
        }
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Audio.Media.IS_RINGTONE, true)
                }
                context.contentResolver.update(songUri, values, null, null)
                android.media.RingtoneManager.setActualDefaultRingtoneUri(
                    context, android.media.RingtoneManager.TYPE_RINGTONE, songUri
                )
            } catch (e: Exception) { /* alguns fabricantes bloqueiam isso fora das configurações do sistema */ }
        }
    }

    ActionSheet(
        expanded = showActionSheet,
        onDismiss = { showActionSheet = false; onDismiss() },
        title = song.title,
        subtitle = song.artist,
        items = listOf(
            com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.SkipNext, "Tocar em seguida") {
                showActionSheet = false; onPlayNext()
            },
            com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.QueueMusic, "Adicionar à fila") {
                showActionSheet = false; onAddToQueueEnd()
            },
            com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.PlaylistAdd, "Adicionar à playlist") {
                showActionSheet = false; showPlaylistPicker = true
            },
            com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.ContentCut, "Cortar") {
                showActionSheet = false; showTrimDialog = true
            },
            com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.Info, "Editar tags (título, artista, álbum, gênero...)") {
                showActionSheet = false; showEditTagsDialog = true
            },
            com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.Image, "Mudar capa") {
                showActionSheet = false; coverPickerLauncher.launch(arrayOf("image/*"))
            },
            com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.MusicNote, "Definir como toque de chamada") {
                showActionSheet = false; setAsRingtone(); onDismiss()
            },
            com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.Share, "Compartilhar") {
                showActionSheet = false; shareSong(); onDismiss()
            },
            com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.DriveFileRenameOutline, "Renomear arquivo (nome no armazenamento)") {
                showActionSheet = false; showEditFileNameDialog = true
            },
            com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.Info, "Propriedades") {
                showActionSheet = false; showPropertiesDialog = true
            },
            com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.VisibilityOff, "Ocultar música") {
                showActionSheet = false
                // onDismiss() DEPOIS do trabalho assíncrono terminar, não
                // antes — onDismiss() tira este composable da árvore, o
                // que cancela o `scope` (rememberCoroutineScope é preso ao
                // ciclo de vida de quem o criou). Chamando antes, o
                // scope.launch quase sempre é cancelado ANTES de rodar
                // sequer a primeira linha (o launch só executa no próximo
                // "tick", e o onDismiss() síncrono logo em seguida já
                // derruba o composable antes disso). Isso valia pra
                // "Ocultar música", "Ocultar álbum", salvar tags, renomear
                // arquivo e cortar música — todos tinham essa mesma corrida.
                scope.launch {
                    dao.setSongHidden(song.id, true)
                    onDismiss()
                }
            },
            com.harmonic.player.ui.common.ActionSheetItem(Icons.Filled.VisibilityOff, "Ocultar álbum inteiro") {
                showActionSheet = false
                scope.launch {
                    dao.hideSongsByAlbum(song.albumId)
                    onDismiss()
                }
            },
            com.harmonic.player.ui.common.ActionSheetItem(
                Icons.Filled.Delete, "Excluir",
                tint = com.harmonic.player.ui.common.DangerColor
            ) {
                showActionSheet = false; showDeleteConfirm = true
            }
        )
    )

    if (showPlaylistPicker) {
        val playlists by dao.getPlaylists().collectAsState(initial = emptyList())
        AlertDialog(
            onDismissRequest = { showPlaylistPicker = false; onDismiss() },
            title = { Text("Adicionar a qual playlist?") },
            text = {
                Column {
                    if (playlists.isEmpty()) {
                        Text("Nenhuma playlist ainda.")
                    }
                    playlists.forEach { playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.name) },
                            modifier = Modifier.clickable {
                                scope.launch {
                                    val currentCount = dao.getPlaylistSongs(playlist.id).first().size
                                    dao.addToPlaylist(PlaylistSongCrossRef(playlist.id, song.id, currentCount))
                                    dao.touchPlaylist(playlist.id)
                                }
                                showPlaylistPicker = false
                                onDismiss()
                            }
                        )
                    }
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.PlaylistAdd, contentDescription = null) },
                        headlineContent = { Text("Nova playlist...") },
                        modifier = Modifier.clickable {
                            showPlaylistPicker = false
                            showCreatePlaylistDialog = true
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistPicker = false; onDismiss() }) { Text("Fechar") }
            }
        )
    }

    if (showCreatePlaylistDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false; onDismiss() },
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
                            val newId = dao.insertPlaylist(Playlist(name = newName.trim()))
                            dao.addToPlaylist(PlaylistSongCrossRef(newId, song.id, 0))
                        }
                        showCreatePlaylistDialog = false
                        onDismiss()
                    }
                ) { Text("Criar e adicionar") }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false; onDismiss() }) { Text("Cancelar") }
            }
        )
    }

    if (showEditTagsDialog) {
        // Carrega as tags direto do arquivo (não do banco do app) — assim o
        // formulário sempre reflete o que está gravado de verdade, mesmo se
        // alguém editou o arquivo por fora do app.
        var loading by remember { mutableStateOf(true) }
        var title by remember { mutableStateOf(song.title) }
        var artist by remember { mutableStateOf(song.artist) }
        var album by remember { mutableStateOf(song.album) }
        var genre by remember { mutableStateOf(song.genre ?: "") }
        var year by remember { mutableStateOf(song.year?.toString() ?: "") }
        var track by remember { mutableStateOf(song.trackNumber?.toString() ?: "") }
        var composer by remember { mutableStateOf(song.composer ?: "") }
        var lookingUpOnline by remember { mutableStateOf(false) }
        var onlineLookupMessage by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(song.id) {
            val fileTags = com.harmonic.player.data.TagEditor.read(song.path)
            if (fileTags != null) {
                if (fileTags.title.isNotBlank()) title = fileTags.title
                if (fileTags.artist.isNotBlank()) artist = fileTags.artist
                if (fileTags.album.isNotBlank()) album = fileTags.album
                if (fileTags.genre.isNotBlank()) genre = fileTags.genre
                if (fileTags.year.isNotBlank()) year = fileTags.year
                if (fileTags.trackNumber.isNotBlank()) track = fileTags.trackNumber
                if (fileTags.composer.isNotBlank()) composer = fileTags.composer
            }
            loading = false
        }

        AlertDialog(
            onDismissRequest = { showEditTagsDialog = false; onDismiss() },
            title = { Text("Editar tags do arquivo") },
            text = {
                Column {
                    Text(
                        "Grava direto no arquivo — sobrevive a limpar o cache, reinstalar o app, ou abrir em outro player.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    if (loading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = artist, onValueChange = { artist = it }, label = { Text("Artista") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = album, onValueChange = { album = it }, label = { Text("Álbum") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = genre, onValueChange = { genre = it }, label = { Text("Gênero") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Row {
                            OutlinedTextField(
                                value = year, onValueChange = { year = it }, label = { Text("Ano") }, singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = track, onValueChange = { track = it }, label = { Text("Faixa nº") }, singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = composer, onValueChange = { composer = it }, label = { Text("Compositor") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                        // Busca álbum/ano/faixa/gênero automaticamente pelo
                        // título+artista já preenchidos — só uma SUGESTÃO,
                        // continua tudo editável antes de salvar.
                        TextButton(
                            enabled = !lookingUpOnline && title.isNotBlank(),
                            onClick = {
                                lookingUpOnline = true
                                onlineLookupMessage = null
                                scope.launch {
                                    val info = com.harmonic.player.data.AlbumMetadataLookup.lookup(title, artist)
                                    if (info == null) {
                                        onlineLookupMessage = "Não achei essa música online."
                                    } else {
                                        if (!info.album.isNullOrBlank()) album = info.album
                                        if (!info.year.isNullOrBlank()) year = info.year
                                        if (!info.trackNumber.isNullOrBlank()) track = info.trackNumber
                                        if (!info.genre.isNullOrBlank() && genre.isBlank()) genre = info.genre
                                        onlineLookupMessage = "Preenchido com o que encontrei online — dá pra corrigir antes de salvar."
                                    }
                                    lookingUpOnline = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (lookingUpOnline) {
                                androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Buscando...")
                            } else {
                                Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Buscar álbum/ano/faixa online")
                            }
                        }
                        onlineLookupMessage?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !loading && title.isNotBlank(),
                    onClick = {
                        val values = com.harmonic.player.data.TagEditor.TagValues(title, artist, album, genre, year, track, composer)
                        // showEditTagsDialog/onDismiss só DEPOIS que saveTags
                        // termina — essa era a causa real de "salva, mas não
                        // aparece no app" (às vezes nem o Toast aparecia):
                        // onDismiss() fecha este composable, o que cancela o
                        // `scope` (rememberCoroutineScope morre com quem
                        // criou). Chamando onDismiss() logo depois do
                        // scope.launch, o scope quase sempre já estava
                        // cancelado antes mesmo do saveTags rodar sua
                        // primeira linha — daí a tag às vezes gravava no
                        // arquivo (quando a escrita corria por fora, tipo no
                        // fluxo de permissão) mas o dao.updateSongMetadata()
                        // e o Toast nunca rodavam.
                        scope.launch {
                            saveTags(values)
                            showEditTagsDialog = false
                            onDismiss()
                        }
                    }
                ) { Text("Salvar no arquivo") }
            },
            dismissButton = {
                TextButton(onClick = { showEditTagsDialog = false; onDismiss() }) { Text("Cancelar") }
            }
        )
    }

    if (showEditFileNameDialog) {
        var newFileName by remember {
            mutableStateOf(java.io.File(song.path).nameWithoutExtension)
        }
        AlertDialog(
            onDismissRequest = { showEditFileNameDialog = false; onDismiss() },
            title = { Text("Editar nome do arquivo") },
            text = {
                Column {
                    Text(
                        "Isso renomeia o arquivo de verdade no armazenamento, não só o título mostrado no app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newFileName.isNotBlank(),
                    onClick = {
                        val newPath = java.io.File(song.path).parent?.let {
                            java.io.File(it, "${newFileName.trim()}.${song.format.lowercase()}").absolutePath
                        } ?: song.path
                        val values = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, "${newFileName.trim()}.${song.format.lowercase()}")
                        }
                        // onDismiss() só DEPOIS do bloco terminar (ver
                        // explicação na mesma correção em "Salvar no
                        // arquivo") — isso é o motivo mais provável de "não
                        // consigo efetivamente renomear o arquivo": o
                        // onDismiss() de antes cancelava o scope quase
                        // sempre ANTES do contentResolver.update/
                        // dao.updateSongPath rodarem.
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                context.contentResolver.update(songUri, values, null, null)
                                // Sem isso, o app só saberia do nome novo no
                                // próximo re-scan (que podia demorar) — o
                                // arquivo já tinha sido renomeado de verdade,
                                // mas continuava aparecendo com o nome antigo
                                // até limpar o cache do app inteiro.
                                dao.updateSongPath(song.id, newPath)
                                android.media.MediaScannerConnection.scanFile(context, arrayOf(song.path, newPath), null, null)
                            } catch (e: SecurityException) {
                                val intentSender = if (android.os.Build.VERSION.SDK_INT >= 29) {
                                    (e as? android.app.RecoverableSecurityException)?.userAction?.actionIntent?.intentSender
                                } else null
                                if (intentSender != null) {
                                    pendingRename = values to newPath
                                    securityLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(intentSender).build())
                                }
                            } catch (e: Exception) { /* ignora, evita derrubar o app */ }
                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                showEditFileNameDialog = false
                                onDismiss()
                            }
                        }
                    }
                ) { Text("Renomear arquivo") }
            },
            dismissButton = {
                TextButton(onClick = { showEditFileNameDialog = false; onDismiss() }) { Text("Cancelar") }
            }
        )
    }

    if (showTrimDialog) {
        var start by remember { mutableStateOf(song.trimStartMs.toFloat()) }
        var end by remember { mutableStateOf((song.trimEndMs.takeIf { it > 0 } ?: song.durationMs).toFloat()) }
        AlertDialog(
            onDismissRequest = { showTrimDialog = false; onDismiss() },
            title = { Text("Cortar música") },
            text = {
                Column {
                    Text(
                        "Ajusta só onde a reprodução começa/termina — não recodifica nem altera o arquivo original.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Início: ${formatDuration(start.toLong())}")
                    Slider(
                        value = start,
                        onValueChange = { if (it < end) start = it },
                        valueRange = 0f..song.durationMs.toFloat()
                    )
                    Text("Fim: ${formatDuration(end.toLong())}")
                    Slider(
                        value = end,
                        onValueChange = { if (it > start) end = it },
                        valueRange = 0f..song.durationMs.toFloat()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { dao.setTrimPoints(song.id, start.toLong(), end.toLong()) }
                    showTrimDialog = false
                    onDismiss()
                }) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch { dao.setTrimPoints(song.id, 0, 0) }
                    showTrimDialog = false
                    onDismiss()
                }) { Text("Remover corte") }
            }
        )
    }

    if (showPropertiesDialog) {
        AlertDialog(
            onDismissRequest = { showPropertiesDialog = false; onDismiss() },
            title = { Text("Propriedades") },
            text = {
                Column {
                    PropertyRow("Título", song.title)
                    PropertyRow("Artista", song.artist)
                    PropertyRow("Álbum", song.album)
                    PropertyRow("Duração", formatDuration(song.durationMs))
                    PropertyRow("Formato", song.format)
                    if (song.bitrate != null) PropertyRow("Bitrate", "${song.bitrate / 1000} kbps")
                    PropertyRow("Tamanho", "${song.sizeBytes / 1024 / 1024} MB")
                    PropertyRow("Caminho", song.path)
                }
            },
            confirmButton = {
                TextButton(onClick = { showPropertiesDialog = false; onDismiss() }) { Text("Fechar") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false; onDismiss() },
            title = { Text("Excluir música?") },
            text = { Text("\"${song.title}\" será apagada do dispositivo. Essa ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(onClick = {
                    // deleteSong() fecha o sheet sozinho (showDeleteConfirm
                    // e onDismiss) quando a exclusão termina — não fazemos
                    // isso aqui pra não desregistrar o securityLauncher
                    // antes dele ser usado, caso o sistema peça permissão.
                    showDeleteConfirm = false
                    deleteSong()
                }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDismiss() }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(90.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * Grava as tags no arquivo de verdade e, se der certo, atualiza o registro
 * no banco do app também — usada pelo diálogo "Editar tags". Deixa
 * [SecurityException] escapar (não a engole) pra quem chama poder tratar o
 * caso de faltar permissão de escrita (armazenamento com escopo).
 */
private suspend fun saveTagsToFileAndDb(
    context: android.content.Context,
    dao: com.harmonic.player.data.SongDao,
    song: Song,
    values: com.harmonic.player.data.TagEditor.TagValues
) {
    val ok = com.harmonic.player.data.TagEditor.write(song.path, values)
    if (ok) {
        dao.updateSongMetadata(
            song.id, values.title.trim(), values.artist.trim(), values.album.trim(),
            values.genre.trim().ifBlank { null }, values.trackNumber.trim().toIntOrNull(),
            values.year.trim().toIntOrNull(), values.composer.trim().ifBlank { null }
        )
        // Avisa o MediaStore que esse arquivo mudou (a gente escreveu nele
        // direto pelo sistema de arquivos, então o MediaStore ainda não
        // sabe) — sem isso, outros apps (gerenciador de arquivos, etc)
        // continuariam vendo as tags antigas.
        android.media.MediaScannerConnection.scanFile(context, arrayOf(song.path), null, null)
        // Se o artista ou álbum mudou, a música pode "sumir" da página em
        // que você estava (ex: dentro da página de um artista específico,
        // ou de um álbum) — não é um bug, ela só não bate mais com aquele
        // filtro. Sem esse aviso, isso parecia perda de dado.
        val artistChanged = values.artist.trim() != song.artist
        val albumChanged = values.album.trim() != song.album
        val message = when {
            artistChanged || albumChanged ->
                "Tags salvas — como o artista/álbum mudou, ela pode ter saído dessa lista (procure em \"${values.artist.trim()}\")"
            else -> "Tags salvas no arquivo"
        }
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
    } else {
        android.widget.Toast.makeText(context, "Não foi possível salvar as tags nesse arquivo", android.widget.Toast.LENGTH_LONG).show()
    }
}
