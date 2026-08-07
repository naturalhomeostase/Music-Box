package com.harmonic.player.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqualizerBandInfo(val index: Int, val centerFreqHz: Int, val minLevel: Int, val maxLevel: Int)

data class EqualizerUiState(
    val ready: Boolean = false,
    val attachFailed: Boolean = false,
    val attachErrorMessage: String? = null,
    val enabled: Boolean = false,
    val bands: List<EqualizerBandInfo> = emptyList(),
    val bandLevels: List<Int> = emptyList(),
    val bassBoostStrength: Int = 0,
    val virtualizerStrength: Int = 0,
    val reverbPreset: Int = 0,
    val bassBoostAvailable: Boolean = true,
    val virtualizerAvailable: Boolean = true,
    val reverbAvailable: Boolean = true
)

/**
 * Os efeitos de áudio do Android (`android.media.audiofx.*`) se conectam a
 * uma "sessão de áudio" (audioSessionId) — um valor específico do ExoPlayer
 * (não da interface genérica `Player` usada pelo MediaController do lado da
 * UI), por isso ele chega até aqui via o singleton `PlaybackAudioSession`,
 * atualizado de dentro do `PlaybackService`.
 *
 * IMPORTANTE: os efeitos precisam ser recriados sempre que o audioSessionId
 * mudar, por isso expomos `attach(sessionId)` para ser chamado sempre que
 * `PlaybackAudioSession.sessionId` mudar.
 */
class EqualizerController {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var presetReverb: PresetReverb? = null
    private var currentSessionId: Int = 0

    private val _uiState = MutableStateFlow(EqualizerUiState())
    val uiState: StateFlow<EqualizerUiState> = _uiState.asStateFlow()

    fun attach(sessionId: Int) {
        if (sessionId == 0 || sessionId == currentSessionId) return
        release()
        currentSessionId = sessionId

        // Antes, os 4 efeitos (Equalizer, BassBoost, Virtualizer,
        // PresetReverb) eram criados dentro de UM ÚNICO try/catch — se
        // qualquer um deles desse erro (ex: "AudioEffect: bad parameter
        // value", muito comum especificamente no PresetReverb em várias
        // ROMs de fabricante, mesmo quando o resto funciona sem problema),
        // NENHUM efeito ficava disponível. Agora cada efeito tem seu
        // próprio try/catch: se um falhar, os outros continuam
        // funcionando normalmente, e só reportamos falha total se o
        // Equalizer em si (o principal) não conseguir conectar.
        var lastError: Exception? = null

        val eq = try {
            val e = Equalizer(0, sessionId).apply { enabled = _uiState.value.enabled }
            val bands = (0 until e.numberOfBands).map { i ->
                val idx = i.toShort()
                EqualizerBandInfo(
                    index = i,
                    centerFreqHz = e.getCenterFreq(idx) / 1000,
                    minLevel = e.bandLevelRange[0].toInt(),
                    maxLevel = e.bandLevelRange[1].toInt()
                )
            }
            e to bands
        } catch (e: Exception) {
            android.util.Log.e("EqualizerController", "Falha ao conectar o Equalizer (sessionId=$sessionId)", e)
            lastError = e
            null
        }

        val bb = try {
            BassBoost(0, sessionId).apply { enabled = _uiState.value.enabled }
        } catch (e: Exception) {
            android.util.Log.e("EqualizerController", "Falha ao conectar o BassBoost (sessionId=$sessionId)", e)
            if (eq == null) lastError = e
            null
        }

        val vr = try {
            Virtualizer(0, sessionId).apply { enabled = _uiState.value.enabled }
        } catch (e: Exception) {
            android.util.Log.e("EqualizerController", "Falha ao conectar o Virtualizer (sessionId=$sessionId)", e)
            if (eq == null) lastError = e
            null
        }

        val reverb = try {
            PresetReverb(0, sessionId).apply { enabled = false }
        } catch (e: Exception) {
            android.util.Log.e("EqualizerController", "Falha ao conectar o PresetReverb (sessionId=$sessionId)", e)
            if (eq == null) lastError = e
            null
        }

        equalizer = eq?.first
        bassBoost = bb
        virtualizer = vr
        presetReverb = reverb

        if (eq != null) {
            _uiState.value = _uiState.value.copy(
                ready = true,
                attachFailed = false,
                attachErrorMessage = null,
                bands = eq.second,
                bassBoostAvailable = bb != null,
                virtualizerAvailable = vr != null,
                reverbAvailable = reverb != null
            )
            // Reaplica os valores salvos assim que os efeitos são criados
            reapplyCurrentState()
        } else {
            // O Equalizer em si (o principal) não conseguiu conectar —
            // mesmo que BassBoost/Virtualizer/Reverb tenham funcionado,
            // sem bandas de frequência a tela não tem o que mostrar.
            currentSessionId = 0
            _uiState.value = _uiState.value.copy(
                ready = false,
                attachFailed = true,
                attachErrorMessage = lastError?.let { "${it.javaClass.simpleName}: ${it.message}" }
            )
        }
    }

    private fun reapplyCurrentState() {
        setEnabled(_uiState.value.enabled)
        _uiState.value.bandLevels.forEachIndexed { index, level -> setBandLevel(index, level) }
        setBassBoostStrength(_uiState.value.bassBoostStrength)
        setVirtualizerStrength(_uiState.value.virtualizerStrength)
        setReverbPreset(_uiState.value.reverbPreset)
    }

    fun setEnabled(enabled: Boolean) {
        equalizer?.enabled = enabled
        bassBoost?.enabled = enabled
        virtualizer?.enabled = enabled
        presetReverb?.enabled = enabled && _uiState.value.reverbPreset != 0
        _uiState.value = _uiState.value.copy(enabled = enabled)
    }

    fun setBandLevel(bandIndex: Int, level: Int) {
        // O crash "AudioEffect: bad parameter value" acontecia aqui: o
        // range de milibels válido (min/max) do Equalizer MUDA de aparelho
        // pra aparelho (cada ROM/fabricante define o próprio DSP), então um
        // nível salvo no DataStore num aparelho com range maior (ex:
        // -1500..1500) é um valor "fora da faixa" num aparelho com range
        // menor (ex: -1200..1200) — e o Equalizer.setBandLevel do Android
        // lança IllegalArgumentException nesse caso em vez de simplesmente
        // ignorar/clampar. Isso disparava sempre que `reapplyCurrentState()`
        // tentava reaplicar um valor salvo assim que o Equalizer conectava
        // (attach -> reapplyCurrentState -> setBandLevel), derrubando o app
        // inteiro porque a exceção não era tratada. Agora limitamos o nível
        // ao range real da banda (reportado pelo próprio aparelho em
        // `attach()`) antes de chamar a API nativa, e ainda blindamos a
        // chamada com try/catch por segurança — assim, mesmo num aparelho
        // com alguma peculiaridade a mais, o equalizador nunca mais derruba
        // o app; na pior das hipóteses aquela banda específica só não muda.
        val bandInfo = _uiState.value.bands.getOrNull(bandIndex)
        val clampedLevel = if (bandInfo != null) {
            level.coerceIn(bandInfo.minLevel, bandInfo.maxLevel)
        } else {
            level
        }
        try {
            equalizer?.setBandLevel(bandIndex.toShort(), clampedLevel.toShort())
        } catch (e: Exception) {
            android.util.Log.e("EqualizerController", "Falha ao aplicar o nível da banda $bandIndex (level=$clampedLevel)", e)
        }
        val updated = _uiState.value.bandLevels.toMutableList()
        while (updated.size <= bandIndex) updated.add(0)
        updated[bandIndex] = clampedLevel
        _uiState.value = _uiState.value.copy(bandLevels = updated)
    }

    fun setBassBoostStrength(strength: Int) {
        bassBoost?.setStrength(strength.toShort())
        _uiState.value = _uiState.value.copy(bassBoostStrength = strength)
    }

    fun setVirtualizerStrength(strength: Int) {
        virtualizer?.setStrength(strength.toShort())
        _uiState.value = _uiState.value.copy(virtualizerStrength = strength)
    }

    fun setReverbPreset(preset: Int) {
        presetReverb?.let {
            it.preset = preset.toShort()
            it.enabled = _uiState.value.enabled && preset != 0
        }
        _uiState.value = _uiState.value.copy(reverbPreset = preset)
    }

    /** Chamado ao carregar os valores salvos no DataStore, antes mesmo dos efeitos existirem. */
    fun restoreState(
        enabled: Boolean,
        bandLevels: List<Int>,
        bassBoost: Int,
        virtualizer: Int,
        reverbPreset: Int
    ) {
        _uiState.value = _uiState.value.copy(
            enabled = enabled,
            bandLevels = bandLevels,
            bassBoostStrength = bassBoost,
            virtualizerStrength = virtualizer,
            reverbPreset = reverbPreset
        )
        if (_uiState.value.ready) reapplyCurrentState()
    }

    /** Zera bandas, bass boost, virtualizador e reverb — mantém o equalizador ligado/desligado como estava. */
    fun resetAll() {
        val zeroLevels = List(_uiState.value.bands.size) { 0 }
        zeroLevels.forEachIndexed { index, level -> setBandLevel(index, level) }
        setBassBoostStrength(0)
        setVirtualizerStrength(0)
        setReverbPreset(0)
    }

    fun release() {
        equalizer?.release(); equalizer = null
        bassBoost?.release(); bassBoost = null
        virtualizer?.release(); virtualizer = null
        presetReverb?.release(); presetReverb = null
        currentSessionId = 0
    }
}

/** Nomes amigáveis para os presets do PresetReverb (fixos no Android, 0 = nenhum). */
val reverbPresetNames = listOf(
    "Nenhum", "Ambiente pequeno", "Sala média", "Sala grande",
    "Câmara média", "Câmara grande", "Plate"
)
