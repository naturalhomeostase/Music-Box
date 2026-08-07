package com.harmonic.player.data

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LyricLine(val timestampMs: Long, val text: String)

sealed class LyricsResult {
    data class Synced(val lines: List<LyricLine>) : LyricsResult()
    data class PlainText(val text: String) : LyricsResult()
    object NotFound : LyricsResult()
}

/**
 * Letras: primeiro tenta um arquivo ao lado da música (.lrc/.txt — padrão
 * usado por Poweramp, VLC etc.), depois um cache local de uma busca online
 * anterior, e por fim busca de verdade na internet (lrclib.net, um banco de
 * letras sincronizadas aberto e gratuito) — salvando o resultado em cache
 * pra não precisar baixar de novo toda vez.
 *
 *   música.mp3
 *   música.lrc   <- letra sincronizada (linha a linha, com timestamp)
 *   música.txt   <- letra simples (sem sincronismo), usada como fallback
 */
object LyricsRepository {

    /** Busca em todas as fontes, na ordem: arquivo local → cache → internet. */
    suspend fun load(context: Context, song: Song): LyricsResult {
        val local = loadLocalFile(song)
        if (local !is LyricsResult.NotFound) return local

        val cached = loadFromCache(context, song)
        if (cached !is LyricsResult.NotFound) return cached

        if (hasRecentNotFoundMarker(context, song)) return LyricsResult.NotFound

        return fetchOnlineAndCache(context, song)
    }

    private fun loadLocalFile(song: Song): LyricsResult {
        val audioFile = File(song.path)
        val baseName = audioFile.nameWithoutExtension
        val folder = audioFile.parentFile ?: return LyricsResult.NotFound

        val lrcFile = File(folder, "$baseName.lrc")
        if (lrcFile.exists()) {
            val lines = parseLRC(lrcFile.readText())
            if (lines.isNotEmpty()) return LyricsResult.Synced(lines)
        }

        val txtFile = File(folder, "$baseName.txt")
        if (txtFile.exists()) {
            val text = txtFile.readText().trim()
            if (text.isNotEmpty()) return LyricsResult.PlainText(text)
        }

        return LyricsResult.NotFound
    }

    private fun cacheDir(context: Context) = File(context.cacheDir, "lyrics_cache").apply { mkdirs() }

    private fun loadFromCache(context: Context, song: Song): LyricsResult {
        val syncedFile = File(cacheDir(context), "${song.id}.lrc")
        if (syncedFile.exists()) {
            val lines = parseLRC(syncedFile.readText())
            if (lines.isNotEmpty()) return LyricsResult.Synced(lines)
        }
        val plainFile = File(cacheDir(context), "${song.id}.txt")
        if (plainFile.exists()) {
            val text = plainFile.readText().trim()
            if (text.isNotEmpty()) return LyricsResult.PlainText(text)
        }
        return LyricsResult.NotFound
    }

    private fun hasRecentNotFoundMarker(context: Context, song: Song): Boolean {
        // Marca de "já procurei e não achei" — evita bater na internet de
        // novo toda vez que a mesma música sem letra tocar. Expira em 7
        // dias, caso a letra apareça no banco de dados depois.
        val marker = File(cacheDir(context), "${song.id}.notfound")
        return marker.exists() && System.currentTimeMillis() - marker.lastModified() < 7 * 24 * 60 * 60 * 1000L
    }

    /** Busca letra sincronizada (preferencialmente) no lrclib.net — banco de letras aberto e gratuito. */
    private fun fetchOnlineAndCache(context: Context, song: Song): LyricsResult {
        return try {
            val url = "https://lrclib.net/api/get?" +
                "track_name=${URLEncoder.encode(song.title, "UTF-8")}" +
                "&artist_name=${URLEncoder.encode(song.artist, "UTF-8")}" +
                "&album_name=${URLEncoder.encode(song.album, "UTF-8")}" +
                "&duration=${song.durationMs / 1000}"

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("User-Agent", "MusicBox Android (contato via app)")

            if (connection.responseCode != 200) {
                File(cacheDir(context), "${song.id}.notfound").writeText("")
                return LyricsResult.NotFound
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = org.json.JSONObject(body)

            val synced = json.optString("syncedLyrics", "")
            if (synced.isNotBlank()) {
                val lines = parseLRC(synced)
                if (lines.isNotEmpty()) {
                    File(cacheDir(context), "${song.id}.lrc").writeText(synced)
                    return LyricsResult.Synced(lines)
                }
            }

            val plain = json.optString("plainLyrics", "")
            if (plain.isNotBlank()) {
                File(cacheDir(context), "${song.id}.txt").writeText(plain)
                return LyricsResult.PlainText(plain)
            }

            File(cacheDir(context), "${song.id}.notfound").writeText("")
            LyricsResult.NotFound
        } catch (e: Exception) {
            // Sem internet, timeout, música não encontrada no banco, etc —
            // não é um erro grave, só significa "sem letra dessa vez".
            LyricsResult.NotFound
        }
    }

    /**
     * Formato LRC padrão: `[mm:ss.xx]texto da linha`, podendo ter mais de
     * uma tag de tempo por linha (letras "duplicadas" em múltiplos pontos)
     * e metadados como `[ar:Artista]` que são ignorados aqui.
     */
    private fun parseLRC(raw: String): List<LyricLine> {
        val timeTagRegex = Regex("""\[(\d{2}):(\d{2})(?:\.(\d{1,3}))?]""")
        val lines = mutableListOf<LyricLine>()

        raw.lineSequence().forEach { rawLine ->
            val matches = timeTagRegex.findAll(rawLine).toList()
            if (matches.isEmpty()) return@forEach

            val text = rawLine.substring(matches.last().range.last + 1).trim()
            matches.forEach { match ->
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                val timestampMs = minutes * 60_000 + seconds * 1000 + fraction
                lines += LyricLine(timestampMs, text)
            }
        }

        return lines.sortedBy { it.timestampMs }
    }
}
