package com.harmonic.player.playback

import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WidgetPlaybackState(
    val title: String? = null,
    val artist: String? = null,
    val isPlaying: Boolean = false,
    val hasQueue: Boolean = false,
    val currentMediaId: Long? = null,
    val coverBitmap: android.graphics.Bitmap? = null
)

/**
 * Ponte entre o widget de tela inicial (Glance) e o player real, que só
 * existe dentro do `PlaybackService`. Como o serviço roda no mesmo processo
 * do app, guardar a referência do `Player` aqui (populada pelo próprio
 * serviço) é o caminho mais direto pro widget ler/controlar a reprodução,
 * sem precisar montar um MediaController próprio só pra isso.
 */
object PlaybackServiceHolder {
    private var player: Player? = null

    private val _state = MutableStateFlow(WidgetPlaybackState())
    val state: StateFlow<WidgetPlaybackState> = _state.asStateFlow()

    // Ponte pro botão "Favoritar" da notificação: quando o coração é tocado
    // NO APP (tela Tocando Agora, listas), o app avisa aqui — e o serviço
    // (que "ouve" isso) atualiza o ícone da notificação na hora, sem
    // precisar trocar de música pra sincronizar.
    private var onFavoriteChangedExternally: ((songId: Long, isFavorite: Boolean) -> Unit)? = null

    fun setFavoriteChangeListener(listener: ((Long, Boolean) -> Unit)?) {
        onFavoriteChangedExternally = listener
    }

    fun notifyFavoriteChanged(songId: Long, isFavorite: Boolean) {
        onFavoriteChangedExternally?.invoke(songId, isFavorite)
    }

    fun attach(player: Player) {
        this.player = player
    }

    fun detach() {
        player = null
        _state.value = WidgetPlaybackState()
    }

    fun refreshState() {
        val p = player ?: run { _state.value = WidgetPlaybackState(); return }
        val metadata = p.mediaMetadata
        val mediaId = p.currentMediaItem?.mediaId?.toLongOrNull()
        // Só zera a capa quando a música REALMENTE mudou — sem isso, cada
        // play/pause (que também chama refreshState) fazia a capa sumir e
        // recarregar do zero, piscando à toa no widget.
        val songChanged = mediaId != _state.value.currentMediaId
        _state.value = _state.value.copy(
            title = metadata.title?.toString(),
            artist = metadata.artist?.toString(),
            isPlaying = p.isPlaying,
            hasQueue = p.mediaItemCount > 0,
            currentMediaId = mediaId,
            coverBitmap = if (songChanged) null else _state.value.coverBitmap
        )
    }

    /** Chamado pelo PlaybackService depois de carregar a capa em segundo plano — só aplica se a música ainda for a mesma. */
    fun updateCover(mediaId: Long?, bitmap: android.graphics.Bitmap?) {
        if (mediaId == _state.value.currentMediaId) {
            _state.value = _state.value.copy(coverBitmap = bitmap)
        }
    }

    fun togglePlayPause() {
        player?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun skipNext() {
        player?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        player?.seekToPreviousMediaItem()
    }
}
