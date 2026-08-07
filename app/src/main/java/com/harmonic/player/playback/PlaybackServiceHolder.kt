package com.harmonic.player.playback

/**
 * Ponte pro botão "Favoritar" da notificação: quando o coração é tocado NO
 * APP (tela Tocando Agora, listas), o app avisa aqui — e o serviço (que
 * "ouve" isso) atualiza o ícone da notificação na hora, sem precisar trocar
 * de música pra sincronizar.
 *
 * (Isso já foi usado também como ponte pro widget de tela inicial — como o
 * widget foi removido, sobrou só essa parte.)
 */
object PlaybackServiceHolder {
    private var onFavoriteChangedExternally: ((songId: Long, isFavorite: Boolean) -> Unit)? = null

    fun setFavoriteChangeListener(listener: ((Long, Boolean) -> Unit)?) {
        onFavoriteChangedExternally = listener
    }

    fun notifyFavoriteChanged(songId: Long, isFavorite: Boolean) {
        onFavoriteChangedExternally?.invoke(songId, isFavorite)
    }
}
