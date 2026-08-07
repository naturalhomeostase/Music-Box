package com.harmonic.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

/**
 * Edita as tags de verdade dentro do arquivo de áudio (ID3 no MP3, Vorbis
 * Comment no OGG/FLAC, MP4 atoms no M4A...) — diferente do "Renomear" do
 * menu de música, que só grava no banco do app. Escrevendo direto no
 * arquivo, a mudança sobrevive a reinstalar o app, limpar o cache, ou abrir
 * a música em qualquer outro player.
 */
object TagEditor {

    data class TagValues(
        val title: String,
        val artist: String,
        val album: String,
        val genre: String,
        val year: String,
        val trackNumber: String,
        val composer: String = ""
    )

    /** Lê as tags atuais direto do arquivo (não do banco do app), pra pré-preencher o formulário de edição. */
    suspend fun read(path: String): TagValues? = withContext(Dispatchers.IO) {
        try {
            val audioFile = AudioFileIO.read(File(path))
            val tag = audioFile.tag
            TagValues(
                title = tag?.getFirst(FieldKey.TITLE).orEmpty(),
                artist = tag?.getFirst(FieldKey.ARTIST).orEmpty(),
                album = tag?.getFirst(FieldKey.ALBUM).orEmpty(),
                genre = tag?.getFirst(FieldKey.GENRE).orEmpty(),
                year = tag?.getFirst(FieldKey.YEAR).orEmpty(),
                trackNumber = tag?.getFirst(FieldKey.TRACK).orEmpty(),
                composer = tag?.getFirst(FieldKey.COMPOSER).orEmpty()
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Grava as tags no arquivo. Cada campo é tentado separadamente — se um
     * formato não suportar um campo específico (raro, mas acontece com
     * alguns campos em alguns formatos), os outros ainda são salvos em vez
     * de tudo falhar junto.
     */
    suspend fun write(path: String, values: TagValues): Boolean = withContext(Dispatchers.IO) {
        try {
            val audioFile = AudioFileIO.read(File(path))
            val tag = audioFile.tagOrCreateAndSetDefault

            fun setSafe(key: FieldKey, value: String) {
                try {
                    if (value.isBlank()) tag.deleteField(key) else tag.setField(key, value)
                } catch (e: Exception) { /* campo não suportado nesse formato — ignora só esse campo */ }
            }

            setSafe(FieldKey.TITLE, values.title)
            setSafe(FieldKey.ARTIST, values.artist)
            setSafe(FieldKey.ALBUM, values.album)
            setSafe(FieldKey.GENRE, values.genre)
            setSafe(FieldKey.YEAR, values.year)
            setSafe(FieldKey.TRACK, values.trackNumber)
            setSafe(FieldKey.COMPOSER, values.composer)

            audioFile.commit()
            true
        } catch (e: Exception) {
            false
        }
    }
}
