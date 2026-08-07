package com.harmonic.player.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Backup num único arquivo .json: configurações do app, playlists e
 * favoritos — pensado pra restaurar tudo de uma vez ao trocar de aparelho
 * ou reinstalar.
 *
 * Playlists e favoritos são identificados pelo CAMINHO do arquivo de
 * áudio, não pelo id interno do banco (que muda a cada instalação/rescan
 * — o id de hoje não existe mais depois de reinstalar). Ao importar, cada
 * caminho é procurado na biblioteca atual; músicas que não existem mais
 * nesse aparelho (movidas, apagadas, ainda não escaneadas) são só
 * ignoradas, sem travar o resto da importação.
 */
object BackupManager {

    data class ImportResult(
        val favoritesRestored: Int,
        val playlistsRestored: Int,
        val songsNotFound: Int
    )

    suspend fun export(context: Context, dao: SongDao, settings: SettingsRepository): Uri {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        val settingsJson = JSONObject()
        settings.exportRawPreferences().forEach { (key, value) ->
            when (value) {
                is Set<*> -> settingsJson.put(key, JSONArray(value.toList()))
                else -> settingsJson.put(key, value)
            }
        }
        root.put("settings", settingsJson)

        val allSongs = dao.getAllSongsOnce()
        val favorites = allSongs.filter { it.isFavorite }
        root.put("favorites", JSONArray(favorites.map { it.path }))

        val playlists = dao.getPlaylistsOnce()
        val playlistsJson = JSONArray()
        playlists.forEach { playlist ->
            val songs = dao.getPlaylistSongsOnce(playlist.id)
            val obj = JSONObject()
            obj.put("name", playlist.name)
            obj.put("isFavorite", playlist.isFavorite)
            obj.put("songs", JSONArray(songs.map { it.path }))
            playlistsJson.put(obj)
        }
        root.put("playlists", playlistsJson)

        val dir = File(context.cacheDir, "backup").apply { mkdirs() }
        val file = File(dir, "music_box_backup.json")
        file.writeText(root.toString(2))

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun share(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Backup Music Box")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Salvar/compartilhar backup"))
    }

    suspend fun import(context: Context, uri: Uri, dao: SongDao, settings: SettingsRepository): ImportResult {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            ?: return ImportResult(0, 0, 0)
        val root = JSONObject(text)

        val settingsJson = root.optJSONObject("settings")
        if (settingsJson != null) {
            val values = mutableMapOf<String, Any?>()
            settingsJson.keys().forEach { key ->
                values[key] = when (val value = settingsJson.get(key)) {
                    is JSONArray -> (0 until value.length()).map { value.getString(it) }.toSet()
                    else -> value
                }
            }
            settings.importRawPreferences(values)
        }

        val allSongs = dao.getAllSongsOnce()
        val byPath = allSongs.associateBy { it.path }
        var notFound = 0
        var favoritesRestored = 0
        var playlistsRestored = 0

        val favoritesJson = root.optJSONArray("favorites")
        if (favoritesJson != null) {
            for (i in 0 until favoritesJson.length()) {
                val song = byPath[favoritesJson.getString(i)]
                if (song != null) {
                    dao.setFavorite(song.id, true)
                    favoritesRestored++
                } else {
                    notFound++
                }
            }
        }

        val playlistsJson = root.optJSONArray("playlists")
        if (playlistsJson != null) {
            for (i in 0 until playlistsJson.length()) {
                val obj = playlistsJson.getJSONObject(i)
                val newId = dao.insertPlaylist(
                    Playlist(name = obj.optString("name", "Playlist"), isFavorite = obj.optBoolean("isFavorite", false))
                )
                val songsJson = obj.optJSONArray("songs")
                if (songsJson != null) {
                    var position = 0
                    for (j in 0 until songsJson.length()) {
                        val song = byPath[songsJson.getString(j)]
                        if (song != null) {
                            dao.addToPlaylist(PlaylistSongCrossRef(newId, song.id, position))
                            position++
                        } else {
                            notFound++
                        }
                    }
                }
                playlistsRestored++
            }
        }

        return ImportResult(favoritesRestored, playlistsRestored, notFound)
    }
}
