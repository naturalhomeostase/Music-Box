package com.harmonic.player.data

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lê a biblioteca de áudio direto do MediaStore (a mesma fonte de dados que
 * o próprio Android usa) e sincroniza com o Room. Não precisamos varrer o
 * sistema de arquivos manualmente — o MediaStore já mantém esse índice,
 * atualizado pelo sistema, e conseguimos ouvir mudanças em tempo real via
 * ContentObserver.
 */
class MediaStoreScanner(private val context: Context) {

    private val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.GENRE,
        MediaStore.Audio.Media.YEAR,
        MediaStore.Audio.Media.COMPOSER,
        MediaStore.Audio.Media.TRACK,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.SIZE,
        MediaStore.Audio.Media.DATA, // caminho completo do arquivo
        MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.Audio.Media.DATE_MODIFIED,
        MediaStore.Audio.Media.MIME_TYPE,
        MediaStore.Audio.Media.BITRATE
    )

    /**
     * Escaneia o MediaStore e retorna a lista de músicas encontradas,
     * ignorando qualquer pasta presente em [ignoredFolders].
     */
    suspend fun scan(ignoredFolders: Set<String>): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val song = cursor.toSong() ?: continue
                    if (ignoredFolders.any { song.folder.startsWith(it) }) continue
                    songs += song
                }
            }
        } catch (e: SecurityException) {
            // Ainda sem permissão de leitura de áudio — devolve lista vazia
            // em vez de derrubar o app. O MusicRepository sabe não apagar
            // nada do banco quando o scan vem vazio por esse motivo.
        }
        songs
    }

    private fun Cursor.toSong(): Song? {
        val path = getStringOrNull(MediaStore.Audio.Media.DATA) ?: return null
        val file = File(path)
        val mediaStoreId = getLong(getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
        val albumId = getLong(getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))

        return Song(
            mediaStoreId = mediaStoreId,
            title = getStringOrNull(MediaStore.Audio.Media.TITLE) ?: file.nameWithoutExtension,
            artist = getStringOrNull(MediaStore.Audio.Media.ARTIST) ?: "Artista desconhecido",
            album = getStringOrNull(MediaStore.Audio.Media.ALBUM) ?: "Álbum desconhecido",
            albumId = albumId,
            genre = getStringOrNull(MediaStore.Audio.Media.GENRE),
            year = getIntOrNull(MediaStore.Audio.Media.YEAR)?.takeIf { it > 0 },
            composer = getStringOrNull(MediaStore.Audio.Media.COMPOSER),
            trackNumber = getIntOrNull(MediaStore.Audio.Media.TRACK),
            durationMs = getLongOrNull(MediaStore.Audio.Media.DURATION) ?: 0,
            sizeBytes = getLongOrNull(MediaStore.Audio.Media.SIZE) ?: file.length(),
            path = path,
            folder = file.parent ?: "/",
            bitrate = getIntOrNull(MediaStore.Audio.Media.BITRATE),
            sampleRate = null, // extraído sob demanda via MediaExtractor (fase 2)
            format = getStringOrNull(MediaStore.Audio.Media.MIME_TYPE)
                ?.substringAfterLast("/")?.uppercase() ?: file.extension.uppercase(),
            dateAdded = getLongOrNull(MediaStore.Audio.Media.DATE_ADDED) ?: 0,
            dateModified = getLongOrNull(MediaStore.Audio.Media.DATE_MODIFIED) ?: 0
        )
    }

    fun albumArtUri(albumId: Long) =
        ContentUris.withAppendedId(
            android.net.Uri.parse("content://media/external/audio/albumart"), albumId
        )

    /** Emite um evento toda vez que o MediaStore de áudio muda (nova música, remoção, etc). */
    fun observeChanges() = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer
        )
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }

    private fun Cursor.getStringOrNull(column: String): String? {
        val idx = getColumnIndex(column)
        return if (idx == -1 || isNull(idx)) null else getString(idx)
    }
    private fun Cursor.getIntOrNull(column: String): Int? {
        val idx = getColumnIndex(column)
        return if (idx == -1 || isNull(idx)) null else getInt(idx)
    }
    private fun Cursor.getLongOrNull(column: String): Long? {
        val idx = getColumnIndex(column)
        return if (idx == -1 || isNull(idx)) null else getLong(idx)
    }
}
