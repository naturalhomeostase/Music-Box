package com.harmonic.player.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.harmonic.player.data.ReplayGainVolume
import com.harmonic.player.data.SettingsRepository
import com.harmonic.player.data.Song
import com.harmonic.player.data.SongDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class PlaybackUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    /**
     * IDs de músicas que o usuário colocou manualmente na fila ("Tocar a
     * seguir" / "Adicionar à fila") e que ainda não foram tocadas. Usado só
     * pra mostrar o ícone de fila no miniplayer — não afeta a reprodução em
     * si. É recalculado (mantendo só o que ainda está à frente da faixa
     * atual) toda vez que a fila ou o índice atual mudam.
     */
    val manuallyQueuedSongIds: Set<Long> = emptySet(),
    // Sleep timer: null = desativado, -1 = "parar no fim da música atual"
    val sleepTimerEndAt: Long? = null,
    val sleepTimerRemainingMs: Long = 0,
    // A-B Repeat: repete só o trecho entre os dois pontos, em loop
    val pointA: Long? = null,
    val pointB: Long? = null,
    /**
     * Identifica de "onde" a fila atual veio (ex: "playlist:5", "album:12",
     * "artist:Xis", "library") — usado só pra saber se um novo play() está
     * trocando de contexto (pra decidir se avisa antes de resetar
     * shuffle/repeat) ou é só continuar navegando dentro do mesmo lugar.
     */
    val sourceKey: String? = null
)

/** Um play() que está esperando confirmação do usuário porque trocaria de contexto com shuffle/repeat ativos. */
data class PendingPlayRequest(
    val songs: List<Song>,
    val startIndex: Int,
    val sourceKey: String,
    val sourceLabel: String,
    val shuffled: Boolean = false,
    val singleSongContext: List<Song>? = null,
    val singleSongContextIndex: Int = 0
)

/**
 * Envolve o MediaController do Media3 numa API simples de usar a partir do
 * Compose, e cuida de salvar/restaurar a fila de reprodução entre sessões
 * do app (via [SettingsRepository]) e de resolver a música atual a partir
 * do [SongDao], sem depender dos metadados (com perda) do MediaItem.
 */
class PlayerController(
    private val context: Context,
    private val dao: SongDao,
    private val settings: SettingsRepository
) {

    private var controller: MediaController? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var sleepTimerJob: Job? = null
    private var positionSaveJob: Job? = null

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private val _pendingPlayRequest = MutableStateFlow<PendingPlayRequest?>(null)
    val pendingPlayRequest: StateFlow<PendingPlayRequest?> = _pendingPlayRequest.asStateFlow()

    fun connect(onConnected: () -> Unit = {}) {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            controller = controllerFuture.get()
            attachListener()
            // O listener acima só é avisado de MUDANÇAS futuras — se o
            // serviço já estava tocando em segundo plano quando o app foi
            // reaberto (ex: música tocando com o app fechado), nenhum
            // "onIsPlayingChanged" é disparado, porque isPlaying não mudou.
            // Sem isso, o mini player ficava preso mostrando o ícone de
            // play mesmo com a música already tocando. Sincroniza o estado
            // atual do controller manualmente logo após conectar.
            syncInitialStateFromController()
            resolveQueueFromControllerIfNeeded()
            startPeriodicPositionSave()
            startABRepeatMonitor()
            startCrossfadeMonitor()
            startReplayGainSettingMonitor()
            scope.launch {
                controller?.setPlaybackSpeed(settings.playbackSpeed.first())
            }
            onConnected()
        }, MoreExecutors.directExecutor())
    }

    private fun syncInitialStateFromController() {
        val c = controller ?: return
        _uiState.value = _uiState.value.copy(
            isPlaying = c.isPlaying,
            shuffleEnabled = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            durationMs = c.duration.coerceAtLeast(0),
            positionMs = c.currentPosition.coerceAtLeast(0)
        )
    }

    private fun resolveQueueFromControllerIfNeeded() {
        val c = controller ?: return
        if (c.mediaItemCount == 0 || _uiState.value.queue.isNotEmpty()) return
        scope.launch {
            val ids = (0 until c.mediaItemCount).mapNotNull { i ->
                c.getMediaItemAt(i).mediaId.toLongOrNull()
            }
            val songsById = dao.getSongsByIds(ids).associateBy { it.id }
            val orderedSongs = ids.mapNotNull { songsById[it] }
            if (orderedSongs.isNotEmpty()) {
                val index = c.currentMediaItemIndex
                _uiState.value = _uiState.value.copy(
                    queue = orderedSongs,
                    currentIndex = index,
                    currentSong = orderedSongs.getOrNull(index),
                    durationMs = c.duration.coerceAtLeast(0)
                )
            }
        }
    }

    private fun attachListener() {
        controller?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateCurrentSongFromIndex()
                persistQueueSnapshot()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _uiState.value = _uiState.value.copy(
                        durationMs = controller?.duration?.coerceAtLeast(0) ?: 0
                    )
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _uiState.value = _uiState.value.copy(shuffleEnabled = shuffleModeEnabled)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _uiState.value = _uiState.value.copy(repeatMode = repeatMode)
            }
        })
    }

    /**
     * Mantém [PlaybackUiState.manuallyQueuedSongIds] só com o que ainda está
     * à frente da faixa atual — sem isso, o ícone de fila no miniplayer
     * continuaria aceso pra sempre depois que a música "furada" já tivesse
     * tocado e ficado pra trás na lista.
     */
    private fun pruneManuallyQueuedIds() {
        val state = _uiState.value
        if (state.manuallyQueuedSongIds.isEmpty()) return
        val upcomingIds = state.queue.drop((state.currentIndex + 1).coerceAtLeast(0)).map { it.id }.toSet()
        val pruned = state.manuallyQueuedSongIds intersect upcomingIds
        if (pruned != state.manuallyQueuedSongIds) {
            _uiState.value = state.copy(manuallyQueuedSongIds = pruned)
        }
    }

    private fun updateCurrentSongFromIndex() {
        val index = controller?.currentMediaItemIndex ?: -1
        val song = _uiState.value.queue.getOrNull(index)
        _uiState.value = _uiState.value.copy(
            currentSong = song,
            currentIndex = index,
            durationMs = controller?.duration?.coerceAtLeast(0) ?: 0
        )
        pruneManuallyQueuedIds()
        refreshReplayGainForSong(song)
        // "Corte": se a música tem um ponto de início definido (menu
        // Cortar), pula direto pra lá em vez de tocar do começo de verdade.
        // Isso não recodifica o arquivo — só ajusta onde a reprodução
        // começa/termina, então o arquivo original nunca é alterado.
        if (song != null && song.trimStartMs > 0) {
            controller?.seekTo(song.trimStartMs)
        }
    }

    fun playQueue(songs: List<Song>, startIndex: Int, sourceKey: String = "default", shuffled: Boolean? = null) {
        if (songs.size > 1) {
            // Fila de verdade com mais de uma música — não precisa do
            // contexto de navegação avulsa, e mantê-lo aqui poderia fazer
            // Anterior/Próxima pularem pra uma lista errada mais tarde.
            contextSongs = emptyList()
            contextIndex = -1
        }
        val items = songs.map { it.toMediaItem() }
        _uiState.value = _uiState.value.copy(
            queue = songs,
            currentSong = songs.getOrNull(startIndex),
            currentIndex = startIndex,
            isPlaying = true, // otimista: evita o ícone de play "atrasado" até o callback confirmar
            sourceKey = sourceKey,
            manuallyQueuedSongIds = emptySet() // nova fila = nenhuma inserção manual pendente ainda
        )
        val startPositionMs = songs.getOrNull(startIndex)?.trimStartMs?.takeIf { it > 0 } ?: 0L
        controller?.setMediaItems(items, startIndex, startPositionMs)
        controller?.prepare()
        // `shuffled == null` (o caso comum — tocar uma música tocando em
        // qualquer lista normal) deixa o modo aleatório do jeito que já
        // estava, sem mexer nele. Só quando um chamador pede explicitamente
        // (o botão "Aleatório"/"Aleatório: tudo", ou o reset de contexto em
        // confirmPendingPlay) é que a gente liga/desliga de verdade —
        // antes, os botões de "Aleatório" da Biblioteca só chamavam
        // `songs.shuffled()` e tocavam essa lista congelada como fila
        // normal: a primeira música até saía numa ordem aleatória, mas o
        // modo aleatório do player continuava OFF (o ícone em "Tocando
        // agora" não acendia, e pular música seguia a ordem congelada, não
        // uma ordem aleatória de verdade a cada vez).
        if (shuffled != null) {
            controller?.shuffleModeEnabled = shuffled
            _uiState.value = _uiState.value.copy(shuffleEnabled = shuffled)
        }
        controller?.play()
        persistQueueSnapshot()
    }

    /**
     * Mesmo que [playQueue], mas verifica antes se isso trocaria de
     * contexto (ex: estava numa playlist com shuffle ligado, e agora vai
     * tocar um álbum) — nesse caso, guarda o pedido em [pendingPlayRequest]
     * em vez de tocar na hora, pra tela mostrar um aviso e o usuário
     * confirmar (ou cancelar) antes de perder o shuffle/repeat anterior.
     * Quando não há conflito real (mesmo contexto, ou nada tocando ainda,
     * ou shuffle/repeat já desligados), toca direto sem perguntar nada.
     */
    fun requestPlayQueue(songs: List<Song>, startIndex: Int, sourceKey: String, sourceLabel: String, shuffled: Boolean? = null) {
        val state = _uiState.value
        val hasActiveModifiers = state.shuffleEnabled || state.repeatMode != Player.REPEAT_MODE_OFF
        val isDifferentContext = state.sourceKey != null && state.sourceKey != sourceKey && state.queue.isNotEmpty()
        if (hasActiveModifiers && isDifferentContext) {
            _pendingPlayRequest.value = PendingPlayRequest(songs, startIndex, sourceKey, sourceLabel, shuffled ?: false)
        } else {
            playQueue(songs, startIndex, sourceKey, shuffled)
        }
    }

    /**
     * Mesma ideia de [requestPlayQueue], só que pra tocar UMA música avulsa
     * (aba Músicas/Favoritas) guardando a lista de origem — assim Anterior/
     * Próxima continuam funcionando mesmo quando o play precisou de
     * confirmação (troca de contexto com aleatório/repetir ligado).
     */
    fun requestPlaySingleSongWithContext(contextList: List<Song>, index: Int, sourceKey: String, sourceLabel: String) {
        val state = _uiState.value
        val song = contextList.getOrNull(index) ?: return
        val hasActiveModifiers = state.shuffleEnabled || state.repeatMode != Player.REPEAT_MODE_OFF
        val isDifferentContext = state.sourceKey != null && state.sourceKey != sourceKey && state.queue.isNotEmpty()
        if (hasActiveModifiers && isDifferentContext) {
            _pendingPlayRequest.value = PendingPlayRequest(
                songs = listOf(song),
                startIndex = 0,
                sourceKey = sourceKey,
                sourceLabel = sourceLabel,
                singleSongContext = contextList,
                singleSongContextIndex = index
            )
        } else {
            playSingleSongWithContext(contextList, index, sourceKey)
        }
    }

    /**
     * Ponto de entrada único pros botões "Aleatório"/"Aleatório: tudo" da
     * Biblioteca (Músicas, Favoritas, artista, álbum, pasta, playlist) —
     * toca a lista NA ORDEM ORIGINAL (pra fila mostrar a ordem "de
     * verdade" de onde veio) com o aleatório de verdade já ligado, em vez
     * de cada botão embaralhar a lista manualmente do seu próprio jeito.
     */
    fun requestPlayQueueShuffled(songs: List<Song>, sourceKey: String, sourceLabel: String) {
        if (songs.isEmpty()) return
        // Antes começava sempre no índice 0 (a primeira música da lista
        // original) e só ligava o modo aleatório do player DEPOIS —
        // resultado: a primeira faixa tocada nunca era aleatória de
        // verdade, só as próximas. Sorteando o índice inicial aqui, a
        // própria primeira música já sai aleatória.
        val startIndex = songs.indices.random()
        requestPlayQueue(songs, startIndex, sourceKey, sourceLabel, shuffled = true)
    }

    fun confirmPendingPlay() {
        val request = _pendingPlayRequest.value ?: return
        // Reseta shuffle/repeat do contexto anterior — o novo play() já
        // começa "do zero", na ordem padrão de onde o usuário está agora
        // (fica ligado nesse "do zero" só se o próprio pedido pendente
        // pediu aleatório — ex: veio do botão "Aleatório").
        controller?.repeatMode = Player.REPEAT_MODE_OFF
        _uiState.value = _uiState.value.copy(repeatMode = Player.REPEAT_MODE_OFF)
        playQueue(request.songs, request.startIndex, request.sourceKey, request.shuffled)
        if (request.singleSongContext != null) {
            contextSongs = request.singleSongContext
            contextIndex = request.singleSongContextIndex
        }
        _pendingPlayRequest.value = null
    }

    fun cancelPendingPlay() {
        _pendingPlayRequest.value = null
    }

    fun playNext(song: Song) {
        val insertIndex = (controller?.currentMediaItemIndex ?: 0) + 1
        controller?.addMediaItem(insertIndex, song.toMediaItem())
        val newQueue = _uiState.value.queue.toMutableList().apply { add(insertIndex, song) }
        _uiState.value = _uiState.value.copy(
            queue = newQueue,
            manuallyQueuedSongIds = _uiState.value.manuallyQueuedSongIds + song.id
        )
        persistQueueSnapshot()
    }

    fun addToQueueEnd(song: Song) {
        controller?.addMediaItem(song.toMediaItem())
        _uiState.value = _uiState.value.copy(
            queue = _uiState.value.queue + song,
            manuallyQueuedSongIds = _uiState.value.manuallyQueuedSongIds + song.id
        )
        persistQueueSnapshot()
    }

    /** Pula direto pra uma música específica da fila (tela "Fila"). */
    fun skipToQueueItem(index: Int) {
        controller?.seekTo(index, 0L)
    }

    /**
     * A fila e o `currentSong` guardam sua própria CÓPIA de cada [Song], não
     * uma referência viva ao banco — por isso, quando o usuário favorita
     * pela tela "Tocando agora", o banco atualiza mas essa cópia em memória
     * não, e o coração continua mostrando o valor antigo até a fila ser
     * recarregada do zero (ex: voltando pra lista). Chamado junto com
     * `dao.setFavorite`, atualiza a cópia em memória na hora.
     */
    fun updateSongFavoriteInMemory(songId: Long, isFavorite: Boolean) {
        val state = _uiState.value
        val newQueue = state.queue.map { if (it.id == songId) it.copy(isFavorite = isFavorite) else it }
        _uiState.value = state.copy(
            queue = newQueue,
            currentSong = if (state.currentSong?.id == songId) {
                state.currentSong.copy(isFavorite = isFavorite)
            } else {
                state.currentSong
            }
        )
        PlaybackServiceHolder.notifyFavoriteChanged(songId, isFavorite)
    }

    /** Remove uma música da fila (não afeta o arquivo/banco, só a ordem de tocar). */
    fun removeFromQueue(index: Int) {
        controller?.removeMediaItem(index)
        val newQueue = _uiState.value.queue.toMutableList().apply {
            if (index in indices) removeAt(index)
        }
        val newCurrentIndex = controller?.currentMediaItemIndex ?: _uiState.value.currentIndex
        _uiState.value = _uiState.value.copy(queue = newQueue, currentIndex = newCurrentIndex)
        pruneManuallyQueuedIds()
        persistQueueSnapshot()
    }

    /** Reordena a fila arrastando um item de uma posição pra outra (tela "Fila"). */
    fun moveQueueItem(from: Int, to: Int) {
        if (from == to) return
        controller?.moveMediaItem(from, to)
        val newQueue = _uiState.value.queue.toMutableList().apply {
            if (from in indices) add(to.coerceIn(0, size - 1), removeAt(from))
        }
        val newCurrentIndex = controller?.currentMediaItemIndex ?: _uiState.value.currentIndex
        _uiState.value = _uiState.value.copy(queue = newQueue, currentIndex = newCurrentIndex)
        pruneManuallyQueuedIds()
        persistQueueSnapshot()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
            _uiState.value = _uiState.value.copy(isPlaying = false)
        } else {
            c.play()
            _uiState.value = _uiState.value.copy(isPlaying = true)
        }
    }

    /**
     * Diferente de pausar: para de vez, esvazia a fila e derruba a
     * notificação/serviço em primeiro plano — como fechar o player.
     * O miniplayer some sozinho depois (ele só aparece quando
     * `currentSong != null`).
     */
    fun stop() {
        controller?.stop()
        controller?.clearMediaItems()
        _uiState.value = _uiState.value.copy(
            isPlaying = false,
            currentSong = null,
            currentIndex = -1,
            queue = emptyList(),
            manuallyQueuedSongIds = emptySet()
        )
        persistQueueSnapshot()
    }

    /**
     * Anterior/Próxima não tinham pra onde ir quando a fila real do player
     * tem só 1 música — que é EXATAMENTE o caso de tocar uma música avulsa
     * da lista de Músicas/Favoritas (por escolha: só aquela música entra na
     * fila, sem lotar de resto da lista sem pedir). [contextSongs] guarda a
     * lista de onde essa música veio só pra navegação — não aparece na tela
     * de fila, que continua mostrando só a música atual como já era.
     */
    private var contextSongs: List<Song> = emptyList()
    private var contextIndex: Int = -1

    /** Toca uma música avulsa mantendo a fila com só ela, mas guardando a lista de origem pra Anterior/Próxima funcionarem. */
    fun playSingleSongWithContext(contextList: List<Song>, index: Int, sourceKey: String) {
        contextSongs = contextList
        contextIndex = index
        val song = contextList.getOrNull(index) ?: return
        playQueue(listOf(song), 0, sourceKey)
    }

    fun skipNext() {
        val state = _uiState.value
        if (state.queue.size == 1 && contextIndex in contextSongs.indices && contextIndex + 1 < contextSongs.size) {
            contextIndex++
            playQueue(listOf(contextSongs[contextIndex]), 0, state.sourceKey ?: "default")
        } else {
            controller?.seekToNextMediaItem()
        }
    }

    fun skipPrevious() {
        val state = _uiState.value
        if (state.queue.size == 1 && contextIndex in contextSongs.indices && contextIndex - 1 >= 0) {
            contextIndex--
            playQueue(listOf(contextSongs[contextIndex]), 0, state.sourceKey ?: "default")
        } else {
            controller?.seekToPreviousMediaItem()
        }
    }
    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs)

    /** ExoPlayer estica/comprime o tempo (Sonic) mantendo o tom original por padrão — não precisa de nada especial pra "sem alterar o tom". */
    fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
    }

    /** Avança ou volta um intervalo (ex: -10000 = volta 10s), sem passar dos limites da música. */
    fun seekBy(deltaMs: Long) {
        val player = controller ?: return
        val target = (player.currentPosition + deltaMs).coerceIn(0, player.duration.coerceAtLeast(0))
        player.seekTo(target)
    }

    fun setShuffle(enabled: Boolean) {
        // Não deixa ligar o shuffle enquanto "repetir uma música" está
        // ativo — nesse modo só existe uma música na "rotação", então
        // embaralhar não teria efeito nenhum e só confundiria.
        if (enabled && controller?.repeatMode == Player.REPEAT_MODE_ONE) return
        controller?.shuffleModeEnabled = enabled
        _uiState.value = _uiState.value.copy(shuffleEnabled = enabled)
    }

    fun cycleRepeatMode() {
        val next = when (controller?.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller?.repeatMode = next
        _uiState.value = _uiState.value.copy(repeatMode = next)

        // Repetir só UMA música e embaralhar ao mesmo tempo não faz
        // sentido (embaralhar o quê, se é sempre a mesma?) — desliga o
        // shuffle automaticamente nesse caso específico. Repetir a fila
        // TODA continua funcionando junto com o shuffle normalmente.
        if (next == Player.REPEAT_MODE_ONE && controller?.shuffleModeEnabled == true) {
            controller?.shuffleModeEnabled = false
            _uiState.value = _uiState.value.copy(shuffleEnabled = false)
        }
    }

    fun currentPositionMs(): Long = controller?.currentPosition ?: 0

    // ---------- Fila persistente ----------

    private fun startPeriodicPositionSave() {
        scope.launch {
            while (true) {
                delay(5000)
                if (_uiState.value.isPlaying) persistQueueSnapshot()
            }
        }
    }

    /** Força salvar o estado atual da fila imediatamente (chamado em onStop da Activity). */
    fun persistNow() = persistQueueSnapshot()

    private fun persistQueueSnapshot() {
        val state = _uiState.value
        if (state.queue.isEmpty()) return
        scope.launch {
            settings.saveQueueState(
                songIds = state.queue.map { it.id },
                currentIndex = state.currentIndex.coerceAtLeast(0),
                positionMs = currentPositionMs()
            )
        }
    }

    // ---------- A-B Repeat ----------

    /** Marca o ponto A na posição atual da música. */
    fun setPointA() {
        _uiState.value = _uiState.value.copy(pointA = currentPositionMs(), pointB = null)
    }

    /** Marca o ponto B na posição atual — a partir daqui o trecho A-B repete em loop. */
    fun setPointB() {
        val a = _uiState.value.pointA ?: return
        val b = currentPositionMs()
        if (b <= a) return // B precisa vir depois de A, senão ignora
        _uiState.value = _uiState.value.copy(pointB = b)
    }

    fun clearABRepeat() {
        _uiState.value = _uiState.value.copy(pointA = null, pointB = null)
    }

    /** Guarda o id da última música pra qual já registramos um "play" — evita contar de novo a cada 200ms enquanto ela ainda toca. */
    private var lastRegisteredPlaySongId: Long? = null

    /** Roda durante toda a vida do controller, verificando o A-B repeat e o ponto de fim do corte periodicamente. */
    private fun startABRepeatMonitor() {
        scope.launch {
            while (true) {
                delay(200)
                val state = _uiState.value
                val a = state.pointA
                val b = state.pointB
                if (a != null && b != null && currentPositionMs() >= b) {
                    controller?.seekTo(a)
                    continue
                }
                val trimEnd = state.currentSong?.trimEndMs ?: 0
                if (trimEnd > 0 && currentPositionMs() >= trimEnd) {
                    controller?.seekToNextMediaItem()
                    continue
                }
                applyCrossfadeVolume()
                registerPlayIfEligible(state)
            }
        }
    }

    /**
     * Conta como "play" pra estatísticas (Mais tocadas / Tocadas
     * recentemente) quando a pessoa já ouviu metade da música ou 30s dela
     * — o que vier primeiro. Evita contar cliques acidentais que são
     * pulados logo em seguida, mas também não obriga tocar a música
     * inteira (útil pra faixas bem longas).
     */
    private fun registerPlayIfEligible(state: PlaybackUiState) {
        val song = state.currentSong ?: return
        if (lastRegisteredPlaySongId == song.id) return
        val duration = controller?.duration ?: return
        if (duration <= 0 || duration == androidx.media3.common.C.TIME_UNSET) return
        val threshold = minOf(duration / 2, 30_000L)
        if (currentPositionMs() >= threshold) {
            lastRegisteredPlaySongId = song.id
            scope.launch { dao.registerPlay(song.id) }
        }
    }

    // ---------- Crossfade ----------

    private var crossfadeMsCached = 0

    /**
     * Crossfade "sequencial": o player só toca uma faixa de áudio por vez,
     * então não dá pra sobrepor de verdade duas músicas tocando ao mesmo
     * tempo (isso exigiria dois players rodando em paralelo). Em vez
     * disso, o volume desce suavemente nos últimos X ms da música atual e
     * sobe suavemente nos primeiros X ms da próxima — evita o corte seco
     * entre faixas, que é o problema que a maioria das pessoas quer
     * resolver ao pedir "crossfade" num tocador de música.
     *
     * Reaproveita o mesmo ciclo de 200ms do A-B repeat (startABRepeatMonitor)
     * em vez de um timer próprio — importante pro consumo de bateria, já
     * que esse ciclo já roda o tempo todo de qualquer forma.
     */
    private fun startCrossfadeMonitor() {
        scope.launch {
            settings.crossfadeMs.collect { crossfadeMsCached = it }
        }
    }

    // ---------- Normalizar volume (ReplayGain) ----------

    private var replayGainEnabledCached = false
    private var replayGainFactor = 1f
    private var replayGainJob: Job? = null

    private fun startReplayGainSettingMonitor() {
        scope.launch {
            settings.replayGainEnabled.collect { enabled ->
                replayGainEnabledCached = enabled
                if (!enabled) replayGainFactor = 1f
                else refreshReplayGainForSong(_uiState.value.currentSong)
            }
        }
    }

    private fun refreshReplayGainForSong(song: Song?) {
        replayGainJob?.cancel()
        replayGainFactor = 1f
        if (!replayGainEnabledCached || song == null) return
        val songAtRequestTime = song.id
        replayGainJob = scope.launch {
            val gain = ReplayGainVolume.readGainMultiplier(song.path) ?: 1f
            // Confere se a música não mudou de novo enquanto o arquivo era
            // lido (troca rápida de faixas) antes de aplicar o ganho.
            if (_uiState.value.currentSong?.id == songAtRequestTime) {
                replayGainFactor = gain
            }
        }
    }

    /** Aplica o volume final = fade do crossfade (se ligado) × ganho de normalização (se ligado) — os dois escrevem no mesmo Player.volume, então precisam ser combinados aqui, não em lugares separados. */
    private fun applyCrossfadeVolume() {
        val c = controller ?: return
        val crossfadeMs = crossfadeMsCached
        val crossfadeFactor = if (crossfadeMs <= 0) {
            1f
        } else {
            val duration = c.duration
            if (duration <= 0 || duration == androidx.media3.common.C.TIME_UNSET) {
                1f
            } else {
                val position = c.currentPosition
                val fadeInFactor = (position.toFloat() / crossfadeMs).coerceIn(0f, 1f)
                val remaining = duration - position
                val fadeOutFactor = if (c.hasNextMediaItem()) (remaining.toFloat() / crossfadeMs).coerceIn(0f, 1f) else 1f
                minOf(fadeInFactor, fadeOutFactor)
            }
        }
        val target = crossfadeFactor * replayGainFactor
        if (c.volume != target) c.volume = target
    }



    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        val endAt = System.currentTimeMillis() + minutes * 60_000L
        _uiState.value = _uiState.value.copy(sleepTimerEndAt = endAt)
        sleepTimerJob = scope.launch {
            while (true) {
                val remaining = endAt - System.currentTimeMillis()
                if (remaining <= 0) {
                    controller?.pause()
                    _uiState.value = _uiState.value.copy(sleepTimerEndAt = null, sleepTimerRemainingMs = 0)
                    break
                }
                _uiState.value = _uiState.value.copy(sleepTimerRemainingMs = remaining)
                delay(1000)
            }
        }
    }

    fun stopAtEndOfSong() {
        cancelSleepTimer()
        val targetIndex = controller?.currentMediaItemIndex
        if (targetIndex == null) return
        sleepTimerJob = scope.launch {
            while (controller?.currentMediaItemIndex == targetIndex) {
                delay(500)
            }
            controller?.pause()
            _uiState.value = _uiState.value.copy(sleepTimerEndAt = null)
        }
        _uiState.value = _uiState.value.copy(sleepTimerEndAt = -1L)
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _uiState.value = _uiState.value.copy(sleepTimerEndAt = null, sleepTimerRemainingMs = 0)
    }

    fun release() {
        cancelSleepTimer()
        persistQueueSnapshot()
        controller?.release()
        controller = null
    }

    private fun Song.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setUri(path)
            .setMediaId(id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .build()
            )
            .build()
}
