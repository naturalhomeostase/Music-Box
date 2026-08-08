package com.harmonic.player

import android.Manifest
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.harmonic.player.data.Song
import com.harmonic.player.playback.EqualizerController
import com.harmonic.player.playback.PlayerController
import com.harmonic.player.ui.common.AppBackground
import com.harmonic.player.ui.equalizer.EqualizerScreen
import com.harmonic.player.ui.library.LibraryScreen
import com.harmonic.player.ui.nowplaying.NowPlayingScreen
import com.harmonic.player.ui.playlists.PlaylistDetailScreen
import com.harmonic.player.ui.playlists.PlaylistsScreen
import com.harmonic.player.ui.settings.AppearanceScreen
import com.harmonic.player.ui.theme.HarmonicTheme
import com.harmonic.player.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    private lateinit var playerController: PlayerController

    /** Uri de um áudio aberto vindo de outro app ("Abrir com" / compartilhar). */
    private var pendingExternalAudioUri by mutableStateOf<Uri?>(null)

    private fun extractAudioUri(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> {
            if (intent.type?.startsWith("audio/") == true) {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            } else null
        }
        else -> null
    }

    /** Monta uma música "avulsa" (não indexada na biblioteca) a partir de um Uri externo, lendo os metadados direto do arquivo. */
    private fun buildAdHocSongFromUri(uri: Uri): Song {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            fun meta(key: Int) = retriever.extractMetadata(key)
            Song(
                id = -1,
                mediaStoreId = -1,
                title = meta(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Faixa externa",
                artist = meta(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Desconhecido",
                album = meta(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "",
                albumId = -1,
                genre = meta(MediaMetadataRetriever.METADATA_KEY_GENRE),
                year = meta(MediaMetadataRetriever.METADATA_KEY_YEAR)?.toIntOrNull(),
                composer = meta(MediaMetadataRetriever.METADATA_KEY_COMPOSER),
                trackNumber = null,
                durationMs = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                sizeBytes = 0,
                path = uri.toString(),
                folder = "",
                bitrate = meta(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull(),
                sampleRate = null,
                format = (contentResolver.getType(uri) ?: "audio").substringAfterLast('/').uppercase(),
                dateAdded = System.currentTimeMillis(),
                dateModified = System.currentTimeMillis()
            )
        } finally {
            retriever.release()
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as HarmonicApp

        playerController = PlayerController(applicationContext, app.database.songDao(), app.settings)
        playerController.connect()

        pendingExternalAudioUri = extractAudioUri(intent)

        // Tela cheia de verdade: o conteúdo do app passa a se estender por
        // baixo da barra de status/navegação (que já são transparentes no
        // tema), em vez de deixar aquela faixa escura padrão do sistema no
        // topo. A cor dos ÍCONES da barra (claros/escuros) é ajustada logo
        // abaixo, dinamicamente, de acordo com o tema atual do app.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val accentColorArgb by app.settings.accentColor.collectAsState(initial = null)
            val themeModeStr by app.settings.themeMode.collectAsState(initial = "dark")

            val themeMode = when (themeModeStr) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                "amoled" -> ThemeMode.AMOLED
                else -> ThemeMode.SYSTEM
            }
            val customAccent = accentColorArgb?.let { Color(it) }
            val scope = rememberCoroutineScope()

            // Ícones da barra de status/navegação: escuros sobre fundo
            // claro (tema Light) ou claros sobre fundo escuro (Dark/AMOLED
            // /Sistema-escuro) — assim ela sempre acompanha o tema em vez
            // de ficar fixa numa cor só.
            val systemInDarkTheme = isSystemInDarkTheme()
            val useLightStatusBarIcons = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK, ThemeMode.AMOLED -> true
                ThemeMode.SYSTEM -> systemInDarkTheme
            }
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !useLightStatusBarIcons
                controller.isAppearanceLightNavigationBars = !useLightStatusBarIcons
            }

            HarmonicTheme(themeMode = themeMode, customAccentColor = customAccent) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                    AppBackground(settings = app.settings) {
                        // Pedimos as duas permissões juntas: acesso ao áudio
                        // (essencial pra biblioteca funcionar) e notificações
                        // (essencial no Android 13+ pra aparecer o player na
                        // barra de notificação — sem essa permissão, o
                        // MediaSessionService cria a notificação mas o
                        // sistema simplesmente não mostra ela).
                        val permissions = buildList {
                            add(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                    Manifest.permission.READ_MEDIA_AUDIO
                                else Manifest.permission.READ_EXTERNAL_STORAGE
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        val permissionsState = rememberMultiplePermissionsState(permissions)

                        val audioPermissionGranted = permissionsState.permissions.any {
                            (it.permission == Manifest.permission.READ_MEDIA_AUDIO ||
                             it.permission == Manifest.permission.READ_EXTERNAL_STORAGE) && it.status.isGranted
                        }

                        LaunchedEffect(Unit) {
                            // Antes isso só disparava quando a permissão de
                            // áudio ainda não tinha sido concedida — então,
                            // se o áudio já estava liberado (reinstalação,
                            // ou o usuário negou notificações da primeira
                            // vez), a permissão de notificação nunca era
                            // pedida de novo, e a barra de notificação com
                            // os controles de play/pause/stop simplesmente
                            // não aparecia com o app em segundo plano.
                            if (!permissionsState.allPermissionsGranted) {
                                permissionsState.launchMultiplePermissionRequest()
                            }
                        }

                        // Assim que a permissão de áudio for concedida (seja
                        // porque já estava concedida, seja porque o usuário
                        // acabou de aceitar), força um novo escaneamento.
                        // Sem isso, a primeira leitura (que roda no
                        // Application.onCreate, antes da permissão existir)
                        // não encontra nada, e só um reinício completo do
                        // app rodava o scan de novo já com permissão.
                        LaunchedEffect(audioPermissionGranted) {
                            if (audioPermissionGranted) {
                                app.musicRepository.rescanNow(scope)
                            }
                        }

                        // Se o app foi aberto a partir de "Abrir com" num
                        // arquivo de áudio (ou um compartilhamento), toca
                        // direto — mesmo que a música não esteja indexada
                        // na biblioteca do app.
                        LaunchedEffect(pendingExternalAudioUri, audioPermissionGranted) {
                            val uri = pendingExternalAudioUri
                            if (uri != null && audioPermissionGranted) {
                                val song = try { buildAdHocSongFromUri(uri) } catch (e: Exception) { null }
                                if (song != null) {
                                    playerController.playQueue(listOf(song), 0)
                                }
                                pendingExternalAudioUri = null
                            }
                        }

                        // Vai direto pra Músicas em vez de mostrar uma tela
                        // própria pedindo permissão antes — o diálogo do
                        // SISTEMA já é disparado automaticamente pelo
                        // LaunchedEffect acima, e aparece por cima da tela de
                        // Músicas normalmente. Se o usuário negar (ou já
                        // tiver negado antes), o HarmonicNavHost mostra um
                        // aviso simples com um botão pra tentar de novo, em
                        // vez da biblioteca ficar vazia sem explicação.
                        HarmonicNavHost(
                            playerController = playerController,
                            app = app,
                            audioPermissionGranted = audioPermissionGranted,
                            onRequestPermission = {
                                if (permissionsState.shouldShowRationale) {
                                    permissionsState.launchMultiplePermissionRequest()
                                } else {
                                    // "Não perguntar de novo" já foi marcado (ou o
                                    // sistema decidiu não mostrar mais o diálogo) —
                                    // só abrindo a tela de permissões do app nas
                                    // Configurações do sistema resolve a partir daqui.
                                    startActivity(
                                        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", packageName, null)
                                        }
                                    )
                                }
                            }
                        )
                        com.harmonic.player.ui.common.PlaybackContextConfirmDialog(playerController)
                    }
                }
            }
        }
    }

    override fun onStop() {
        playerController.persistNow()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingExternalAudioUri = extractAudioUri(intent)
    }

    override fun onDestroy() {
        playerController.release()
        super.onDestroy()
    }
}

@Composable
private fun HarmonicNavHost(
    playerController: PlayerController,
    app: HarmonicApp,
    audioPermissionGranted: Boolean,
    onRequestPermission: () -> Unit
) {
    val navController = rememberNavController()

    // Uma única instância do equalizador vive durante toda a navegação —
    // não só enquanto a tela dele está aberta. Senão, o efeito sonoro
    // desapareceria assim que o usuário voltasse pra Biblioteca.
    val equalizerController = remember { EqualizerController() }
    val audioSessionId by com.harmonic.player.playback.PlaybackAudioSession.sessionId.collectAsState()

    // Carrega os valores salvos do equalizador uma única vez, assim que o
    // app abre (antes mesmo de o usuário visitar a tela do equalizador).
    LaunchedEffect(Unit) {
        equalizerController.restoreState(
            enabled = app.settings.eqEnabled.first(),
            bandLevels = app.settings.eqBandLevels.first(),
            bassBoost = app.settings.bassBoostStrength.first(),
            virtualizer = app.settings.virtualizerStrength.first(),
            reverbPreset = app.settings.reverbPreset.first()
        )
    }

    // Reconecta os efeitos sempre que o audioSessionId do ExoPlayer mudar
    // (acontece ao iniciar a reprodução pela primeira vez, por exemplo).
    // Se a primeira tentativa falhar (alguns aparelhos ainda não têm a
    // trilha de áudio 100% pronta no instante exato em que o ID da sessão
    // muda), tenta de novo uma vez depois de um instante, em vez de
    // desistir na hora.
    LaunchedEffect(audioSessionId) {
        if (audioSessionId != 0) {
            equalizerController.attach(audioSessionId)
            if (equalizerController.uiState.value.attachFailed) {
                kotlinx.coroutines.delay(600)
                equalizerController.attach(audioSessionId)
            }
        }
    }

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Box(modifier = Modifier.fillMaxSize()) {
    NavHost(navController = navController, startDestination = "library") {
        composable("library") {
            LibraryScreen(
                database = app.database,
                playerController = playerController,
                settings = app.settings,
                onSongClick = { queue, index, sourceKey ->
                    val label = sourceKey.substringAfter(':').ifBlank { "essa lista" }
                    playerController.requestPlayQueue(queue, index, sourceKey, label)
                },
                onOpenNowPlaying = { navController.navigate("now_playing") },
                onOpenSettings = { navController.navigate("settings") },
                onOpenPlaylist = { id -> navController.navigate("playlist/$id") }
            )
        }
        composable("now_playing") {
            NowPlayingScreen(
                playerController = playerController,
                dao = app.database.songDao(),
                settings = app.settings,
                onBack = { navController.popBackStack() },
                onOpenEqualizer = { navController.navigate("equalizer") }
            )
        }
        composable("settings") {
            com.harmonic.player.ui.settings.SettingsScreen(
                settings = app.settings,
                musicRepository = app.musicRepository,
                playerController = playerController,
                dao = app.database.songDao(),
                onBack = { navController.popBackStack() },
                onOpenTheme = { navController.navigate("appearance") },
                onOpenEqualizer = { navController.navigate("equalizer") },
                onOpenHiddenFolders = { navController.navigate("hidden_folders") },
                onOpenHiddenSongs = { navController.navigate("hidden_songs") },
                onOpenAbout = { navController.navigate("about") },
                onOpenMaintenance = { navController.navigate("library_maintenance") },
                onOpenHistory = { navController.navigate("history_stats") }
            )
        }
        composable("history_stats") {
            com.harmonic.player.ui.settings.HistoryStatsScreen(
                database = app.database,
                playerController = playerController,
                onBack = { navController.popBackStack() }
            )
        }
        composable("library_maintenance") {
            com.harmonic.player.ui.settings.LibraryMaintenanceScreen(
                database = app.database,
                onBack = { navController.popBackStack() }
            )
        }
        composable("about") {
            com.harmonic.player.ui.settings.AboutScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable("hidden_folders") {
            com.harmonic.player.ui.settings.HiddenFoldersScreen(
                database = app.database,
                onBack = { navController.popBackStack() }
            )
        }
        composable("hidden_songs") {
            com.harmonic.player.ui.settings.HiddenSongsScreen(
                database = app.database,
                onBack = { navController.popBackStack() }
            )
        }
        composable("appearance") {
            AppearanceScreen(
                settings = app.settings,
                onBack = { navController.popBackStack() }
            )
        }
        composable("equalizer") {
            EqualizerScreen(
                equalizerController = equalizerController,
                settings = app.settings,
                onBack = { navController.popBackStack() }
            )
        }
        composable("playlists") {
            PlaylistsScreen(
                dao = app.database.songDao(),
                playerController = playerController,
                onBack = { navController.popBackStack() },
                onOpenPlaylist = { id -> navController.navigate("playlist/$id") },
                onOpenNowPlaying = { navController.navigate("now_playing") }
            )
        }
        composable(
            route = "playlist/{playlistId}",
            arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: return@composable
            PlaylistDetailScreen(
                playlistId = playlistId,
                dao = app.database.songDao(),
                context = app.applicationContext,
                playerController = playerController,
                onBack = { navController.popBackStack() },
                onOpenNowPlaying = { navController.navigate("now_playing") }
            )
        }
    }

    // Aviso pra recuperar de uma permissão negada — sem isso, quem nega o
    // diálogo do sistema (de propósito ou sem querer) ficava com a
    // biblioteca vazia pra sempre, sem nenhuma pista do porquê nem um jeito
    // de resolver a partir do próprio app.
    if (currentRoute == "library" && !audioPermissionGranted) {
        androidx.compose.material3.Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            color = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Text(
                    "Sem acesso às músicas do aparelho.",
                    modifier = Modifier.weight(1f),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.width(12.dp))
                androidx.compose.material3.TextButton(onClick = onRequestPermission) {
                    androidx.compose.material3.Text("Permitir")
                }
            }
        }
    }
    }
}
