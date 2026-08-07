package com.harmonic.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import java.io.File
import kotlin.math.pow

/**
 * Lê a tag REPLAYGAIN_TRACK_GAIN, um padrão (não um FieldKey do jaudiotagger
 * — é um campo "customizado" de texto livre, tipo TXXX no ID3 ou comentário
 * Vorbis no FLAC/OGG) que ferramentas como foobar2000, mp3gain e o próprio
 * iTunes já gravam em muitos arquivos, geralmente algo como "-6.2 dB".
 *
 * Não fazemos NENHUMA análise de áudio aqui — só lemos o valor se a própria
 * música já vier com ele calculado. Músicas sem a tag simplesmente tocam no
 * volume normal (nunca ficam mudas nem erram o volume por falta de dado).
 */
object ReplayGainVolume {

    /**
     * Devolve um multiplicador de volume linear (0..1) a aplicar na música,
     * ou null se ela não tiver a tag. Só reduz o volume de faixas gravadas
     * mais altas — nunca aumenta acima de 1.0 (aumentar via volume simples
     * distorceria o áudio; isso exigiria um efeito de "loudness enhancer"
     * separado, fora do escopo por agora), então o efeito prático é deixar
     * as faixas mais altas do volume parecidas com as mais baixas, e não
     * o contrário.
     */
    suspend fun readGainMultiplier(path: String): Float? = withContext(Dispatchers.IO) {
        try {
            val tag = AudioFileIO.read(File(path)).tag ?: return@withContext null
            val raw = tag.getFirst("REPLAYGAIN_TRACK_GAIN").ifBlank { tag.getFirst("REPLAYGAIN_ALBUM_GAIN") }
            if (raw.isBlank()) return@withContext null
            val db = raw.replace("dB", "", ignoreCase = true).trim().toFloatOrNull() ?: return@withContext null
            val linear = 10f.pow(db / 20f)
            linear.coerceIn(0.1f, 1f)
        } catch (e: Exception) {
            null
        }
    }
}
