package com.harmonic.player.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Carrega a capa do álbum, em ordem: capa escolhida manualmente pelo
 * usuário → capa embutida no arquivo de áudio → cache de uma busca online
 * anterior → busca de verdade na internet (iTunes Search API — gratuita,
 * sem chave de acesso, e cobre a grande maioria dos álbuns comerciais).
 *
 * No Android 10+ (API 29+), o jeito certo pra capa embutida é
 * `ContentResolver.loadThumbnail` direto na URI da música — é a API que o
 * próprio Google recomenda pra scoped storage, e funciona pra praticamente
 * qualquer formato com arte embutida (MP3/ID3, FLAC, M4A...).
 *
 * Em versões mais antigas, usamos a URI legada `content://media/.../albumart`,
 * que ainda funciona bem antes do scoped storage existir.
 */
object AlbumArtLoader {

    suspend fun load(context: Context, song: Song, sizePx: Int = 512): Bitmap? = withContext(Dispatchers.IO) {
        loadLocal(context, song, sizePx) ?: loadOnline(context, song)
    }

    private fun loadLocal(context: Context, song: Song, sizePx: Int): Bitmap? {
        return try {
            // Capa escolhida manualmente pelo usuário ("Mudar capa" no menu
            // da música) tem prioridade sobre a capa embutida no arquivo.
            val customUri = song.customCoverUri
            if (customUri != null) {
                context.contentResolver.openInputStream(Uri.parse(customUri))?.use {
                    return BitmapFactory.decodeStream(it)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.mediaStoreId)
                context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
            } else {
                val legacyUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), song.albumId
                )
                context.contentResolver.openInputStream(legacyUri)?.use { BitmapFactory.decodeStream(it) }
            }
        } catch (e: Exception) {
            // Nem toda música tem capa embutida — cai pra busca online.
            null
        }
    }

    private fun cacheDir(context: Context) = File(context.cacheDir, "album_art_cache").apply { mkdirs() }

    /** Busca a capa na internet (iTunes Search API) — uma vez por álbum, com cache em disco pras próximas vezes. */
    private fun loadOnline(context: Context, song: Song): Bitmap? {
        val cacheFile = File(cacheDir(context), "${song.albumId}.jpg")
        if (cacheFile.exists()) {
            return BitmapFactory.decodeFile(cacheFile.absolutePath)
        }
        val notFoundMarker = File(cacheDir(context), "${song.albumId}.notfound")
        if (notFoundMarker.exists() && System.currentTimeMillis() - notFoundMarker.lastModified() < 7 * 24 * 60 * 60 * 1000L) {
            return null
        }

        return try {
            val term = URLEncoder.encode("${song.artist} ${song.album}", "UTF-8")
            val searchUrl = "https://itunes.apple.com/search?term=$term&media=music&entity=song&limit=1"
            val connection = URL(searchUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("User-Agent", "MusicBox Android (contato via app)")

            if (connection.responseCode != 200) {
                notFoundMarker.writeText("")
                return null
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val results = org.json.JSONObject(body).optJSONArray("results")
            val artworkUrlSmall = results?.optJSONObject(0)?.optString("artworkUrl100")

            if (artworkUrlSmall.isNullOrBlank()) {
                notFoundMarker.writeText("")
                return null
            }

            // A API sempre retorna uma miniatura 100x100 — trocar o tamanho
            // na própria URL pede uma versão bem maior, sem custo extra.
            val artworkUrlLarge = artworkUrlSmall.replace("100x100", "600x600")
            val imageConnection = URL(artworkUrlLarge).openConnection() as HttpURLConnection
            imageConnection.connectTimeout = 8000
            imageConnection.readTimeout = 8000

            val bitmap = imageConnection.inputStream.use { BitmapFactory.decodeStream(it) }
            if (bitmap != null) {
                cacheFile.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
            } else {
                notFoundMarker.writeText("")
            }
            bitmap
        } catch (e: Exception) {
            // Sem internet, álbum não encontrado no catálogo, etc — não é
            // um erro grave, só significa "sem capa dessa vez".
            null
        }
    }
}
