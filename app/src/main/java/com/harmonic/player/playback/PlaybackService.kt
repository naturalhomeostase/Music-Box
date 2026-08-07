package com.harmonic.player.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.harmonic.player.HarmonicApp
import com.harmonic.player.MainActivity
import com.harmonic.player.R
import com.harmonic.player.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Serviço de reprodução em segundo plano.
 *
 * Usar MediaSessionService (Media3) nos dá de graça, sem código extra:
 *  - Notificação com controles (play/pause/próxima/anterior/capa)
 *  - Controles na tela bloqueada
 *  - Resposta automática a botões do fone Bluetooth / fone com fio
 *  - Pausa automática ao receber ligação ou desconectar o áudio
 *  - Compatibilidade com Android Auto
 *
 * O ExoPlayer já lida nativamente com MP3, FLAC, WAV, AAC, OGG, OPUS, M4A.
 *
 * Também restaura a última fila de reprodução salva (ver
 * [com.harmonic.player.data.SettingsRepository.saveQueueState]) assim que o
 * serviço é criado, deixando o player pronto (mas pausado) — assim, se o
 * usuário reabrir o app depois de o sistema matar o processo, a música que
 * estava tocando continua exatamente de onde parou.
 */
class PlaybackService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null
    val mediaSessionPublic: MediaSession? get() = mediaSession
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var restoreJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.repeatMode = Player.REPEAT_MODE_OFF

        val sessionCallback = PlaybackSessionCallback(this, serviceScope)
        // Quando o coração é tocado dentro do app (não na notificação), o
        // ícone da notificação precisa saber disso também — sem essa ponte,
        // ele só atualizava ao trocar de música.
        PlaybackServiceHolder.setFavoriteChangeListener { songId, isFavorite ->
            sessionCallback.onExternalFavoriteChange(songId, isFavorite)
        }

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Reforça o valor atual da sessão de áudio a cada troca de
                // play/pause — o listener de análise abaixo (que só reage
                // quando o ID realmente MUDA) sozinho não estava sendo o
                // bastante pra manter o equalizador conectado em todos os
                // casos reais de uso; isso é uma rede de segurança extra,
                // barata de chamar.
                PlaybackAudioSession.update(player.audioSessionId)
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                sessionCallback.onSongChanged(mediaItem?.mediaId?.toLongOrNull())
                PlaybackAudioSession.update(player.audioSessionId)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                PlaybackAudioSession.update(player.audioSessionId)
            }
        })

        // O audioSessionId só é acessível aqui, na instância real do
        // ExoPlayer — expomos ele pro resto do app (equalizador) via
        // PlaybackAudioSession, já que MediaController não tem esse dado.
        player.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                audioSessionId: Int
            ) {
                PlaybackAudioSession.update(audioSessionId)
            }
        })

        val sessionActivityIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaLibrarySession.Builder(this, player, sessionCallback)
            .setSessionActivity(sessionActivityIntent)
            .build()

        // Sem isso, a notificação de reprodução usa um canal e um nome
        // genéricos escolhidos pela própria biblioteca — dando um nome
        // claro aqui, fica mais fácil pro usuário achar e conferir se as
        // notificações do player estão ativadas em Ajustes > Apps > Music
        // Box > Notificações, caso não estejam aparecendo.
        val notificationProvider = androidx.media3.session.DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(NOTIFICATION_CHANNEL_ID)
            .setChannelName(R.string.notification_channel_playback)
            .build()
        setMediaNotificationProvider(notificationProvider)

        restoreSavedQueueIfAny(player)
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "harmonic_playback"
    }

    private fun restoreSavedQueueIfAny(player: ExoPlayer) {
        restoreJob = serviceScope.launch {
            val app = applicationContext as HarmonicApp
            val saved = app.settings.readSavedQueueState() ?: return@launch
            val dao = app.database.songDao()
            val songsById = dao.getSongsByIds(saved.songIds).associateBy { it.id }
            val orderedSongs = saved.songIds.mapNotNull { songsById[it] }
            if (orderedSongs.isEmpty()) return@launch

            val items = orderedSongs.map { song ->
                MediaItem.Builder()
                    .setUri(song.path)
                    .setMediaId(song.id.toString())
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .build()
                    )
                    .build()
            }
            val safeIndex = saved.currentIndex.coerceIn(0, items.size - 1)
            player.setMediaItems(items, safeIndex, saved.positionMs)
            player.prepare()
            // Sem player.play() — fica pronto, pausado, esperando o usuário.
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        // Mantém o serviço (e a notificação com os controles) vivo sempre
        // que existir uma fila carregada — mesmo pausado. Antes, fechar o
        // app com a música pausada matava a notificação junto, obrigando a
        // reabrir o app só pra retomar. Só encerra de vez se não há
        // absolutamente nada carregado pra tocar.
        if (player == null || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        restoreJob?.cancel()
        PlaybackServiceHolder.setFavoriteChangeListener(null)
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

/**
 * Callback da sessão. Além de favoritar/parar (ver comentário mais abaixo),
 * agora também implementa a NAVEGAÇÃO da biblioteca — é o que faz o
 * Android Auto (e qualquer outro "media browser", como o Google Assistant)
 * conseguir mostrar Músicas/Artistas/Álbuns/Playlists/Favoritas na tela do
 * carro, em vez de só tocar/pausar o que já estava tocando no celular.
 *
 * Os itens "pasta" (Músicas, um artista específico, etc) só têm id+título;
 * os itens "música" já vêm com a URI real do arquivo — o Android Auto pode
 * tocar direto o que a gente devolve aqui, sem precisar de mais nenhuma
 * consulta depois de escolhido.
 */
private const val ACTION_STOP = "com.harmonic.player.STOP"
private const val ACTION_FAVORITE_TOGGLE = "com.harmonic.player.FAVORITE_TOGGLE"
private const val LIBRARY_ROOT_ID = "root"

private class PlaybackSessionCallback(
    private val context: Context,
    private val scope: CoroutineScope
) : MediaLibrarySession.Callback {

    private val stopSessionCommand = SessionCommand(ACTION_STOP, Bundle.EMPTY)
    private val favoriteSessionCommand = SessionCommand(ACTION_FAVORITE_TOGGLE, Bundle.EMPTY)

    private val stopButton = CommandButton.Builder()
        .setDisplayName("Parar")
        .setSessionCommand(stopSessionCommand)
        .setIconResId(R.drawable.ic_stop)
        .build()

    private var currentSongId: Long? = null
    private var currentIsFavorite: Boolean = false

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        val availableCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
            .buildUpon()
            .add(stopSessionCommand)
            .add(favoriteSessionCommand)
            .build()
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(availableCommands)
            .build()
    }

    override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
        refreshCustomLayout(session)
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        when (customCommand.customAction) {
            ACTION_STOP -> {
                session.player.stop()
                session.player.clearMediaItems()
            }
            ACTION_FAVORITE_TOGGLE -> {
                val songId = currentSongId
                if (songId != null) {
                    val newValue = !currentIsFavorite
                    // Otimista: já reflete o novo estado no ícone na hora,
                    // sem esperar a escrita no banco terminar — a notificação
                    // fica travada só o tempo de um toggle de boolean.
                    currentIsFavorite = newValue
                    refreshCustomLayout(session)
                    scope.launch(Dispatchers.IO) {
                        val dao = (context.applicationContext as HarmonicApp).database.songDao()
                        dao.setFavorite(songId, newValue)
                    }
                    PlaybackServiceHolder.notifyFavoriteChanged(songId, newValue)
                }
            }
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    /** Chamado pelo listener do player quando a faixa muda — busca o status de favorito da música nova. */
    fun onSongChanged(songId: Long?) {
        currentSongId = songId
        if (songId == null) {
            currentIsFavorite = false
            return
        }
        scope.launch(Dispatchers.IO) {
            val dao = (context.applicationContext as HarmonicApp).database.songDao()
            val isFavorite = dao.getSongsByIds(listOf(songId)).firstOrNull()?.isFavorite ?: false
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                // Confere se a música não mudou de novo enquanto a consulta
                // rodava (troca rápida de faixas) antes de aplicar.
                if (currentSongId == songId) {
                    currentIsFavorite = isFavorite
                    context.mediaSessionOrNull()?.let { refreshCustomLayout(it) }
                }
            }
        }
    }

    /** Chamado quando o favorito muda por FORA da notificação (ex: coração na tela do app) — mantém o ícone sincronizado. */
    fun onExternalFavoriteChange(songId: Long, isFavorite: Boolean) {
        if (songId == currentSongId && isFavorite != currentIsFavorite) {
            currentIsFavorite = isFavorite
            context.mediaSessionOrNull()?.let { refreshCustomLayout(it) }
        }
    }

    private fun refreshCustomLayout(session: MediaSession) {
        val favoriteButton = CommandButton.Builder()
            .setDisplayName(if (currentIsFavorite) "Remover dos favoritos" else "Favoritar")
            .setSessionCommand(favoriteSessionCommand)
            .setIconResId(if (currentIsFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border)
            .build()
        session.setCustomLayout(listOf(favoriteButton, stopButton))
    }

    // ---------- Navegação da biblioteca (Android Auto etc.) ----------

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> =
        Futures.immediateFuture(LibraryResult.ofItem(folderItem(LIBRARY_ROOT_ID, "Music Box"), params))

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> = futureResult {
        val songId = mediaId.toLongOrNull()
        val dao = (context.applicationContext as HarmonicApp).database.songDao()
        val song = songId?.let { dao.getSongsByIds(listOf(it)).firstOrNull() }
        if (song != null) LibraryResult.ofItem(songItem(song), null)
        else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = futureResult {
        val dao = (context.applicationContext as HarmonicApp).database.songDao()
        val items: List<MediaItem> = when {
            parentId == LIBRARY_ROOT_ID -> listOf(
                folderItem("songs", "Músicas"),
                folderItem("favorites", "Favoritas"),
                folderItem("artists", "Artistas"),
                folderItem("albums", "Álbuns"),
                folderItem("playlists", "Playlists")
            )
            parentId == "songs" -> dao.getAllSongsOnce().map { songItem(it) }
            parentId == "favorites" -> dao.getAllSongsOnce().filter { it.isFavorite }.map { songItem(it) }
            parentId == "artists" -> dao.getArtists().first().map { name -> folderItem("artist:$name", name) }
            parentId.startsWith("artist:") -> dao.getSongsByArtist(parentId.removePrefix("artist:")).first().map { songItem(it) }
            parentId == "albums" -> dao.getAlbums().first().map { album -> folderItem("album:${album.albumId}", "${album.album} — ${album.artist}") }
            parentId.startsWith("album:") -> {
                val albumId = parentId.removePrefix("album:").toLongOrNull()
                if (albumId != null) dao.getSongsByAlbum(albumId).first().map { songItem(it) } else emptyList()
            }
            parentId == "playlists" -> dao.getPlaylistsOnce().map { playlist -> folderItem("playlist:${playlist.id}", playlist.name) }
            parentId.startsWith("playlist:") -> {
                val playlistId = parentId.removePrefix("playlist:").toLongOrNull()
                if (playlistId != null) dao.getPlaylistSongsOnce(playlistId).map { songItem(it) } else emptyList()
            }
            else -> emptyList()
        }
        LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
    }

    /** Roda [block] numa corrotina e devolve o resultado como ListenableFuture — sem depender de nenhuma lib extra pra ponte corrotina/Future. */
    private fun <T : Any> futureResult(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        scope.launch(Dispatchers.IO) {
            future.set(block())
        }
        return future
    }

    private fun folderItem(id: String, title: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()

    private fun songItem(song: Song): MediaItem =
        MediaItem.Builder()
            .setUri(song.path)
            .setMediaId(song.id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()
}

/** [Context] aqui é sempre o próprio [PlaybackService] — pega a sessão dele de volta pra atualizar o layout fora do fluxo normal de callbacks. */
private fun Context.mediaSessionOrNull(): MediaSession? = (this as? PlaybackService)?.mediaSessionPublic
