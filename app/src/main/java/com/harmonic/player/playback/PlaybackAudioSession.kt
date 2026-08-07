package com.harmonic.player.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * O audioSessionId (necessário pro Equalizer/BassBoost/Virtualizer) só
 * existe na interface `ExoPlayer`, não na interface genérica `Player` usada
 * pelo `MediaController` do lado da UI — por isso não dá pra simplesmente
 * ler `controller.audioSessionId`.
 *
 * Como o `PlaybackService` roda no mesmo processo do app (não declaramos
 * `android:process` no manifest), o jeito mais simples e confiável de levar
 * esse valor até a UI é este singleton: o serviço escreve, a UI (equalizador)
 * lê. Nada de IPC, nada de comandos customizados de MediaSession.
 */
object PlaybackAudioSession {
    private val _sessionId = MutableStateFlow(0)
    val sessionId: StateFlow<Int> = _sessionId.asStateFlow()

    fun update(id: Int) {
        _sessionId.value = id
    }
}
