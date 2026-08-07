package com.harmonic.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class OnlineTrackInfo(
    val album: String?,
    val year: String?,
    val trackNumber: String?,
    val genre: String?
)

/**
 * Busca álbum/ano/faixa/gênero automaticamente a partir de título + artista,
 * usando a API de busca do iTunes (gratuita, sem chave de API, boa cobertura
 * pra música comercial/mainstream — não tem tudo, mas não exige cadastro nem
 * limite de uso agressivo, diferente de outras alternativas). O resultado é
 * só uma SUGESTÃO pra pré-preencher o formulário — o usuário sempre pode
 * editar/corrigir antes de salvar, exatamente como já podia digitar manual.
 */
object AlbumMetadataLookup {

    suspend fun lookup(title: String, artist: String): OnlineTrackInfo? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        try {
            val term = URLEncoder.encode("$artist $title".trim(), "UTF-8")
            val url = "https://itunes.apple.com/search?term=$term&entity=song&limit=5"

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("User-Agent", "MusicBox Android (contato via app)")

            if (connection.responseCode != 200) return@withContext null

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = org.json.JSONObject(body)
            val results = json.optJSONArray("results") ?: return@withContext null
            if (results.length() == 0) return@withContext null

            // O iTunes já ordena por relevância — entre os primeiros
            // resultados, prefere um cujo artista bate (ignorando maiúsculas
            // /acentos/espaços) com o que já temos, pra evitar pegar uma
            // versão de outro artista com nome de música parecido.
            var best = results.getJSONObject(0)
            if (artist.isNotBlank()) {
                val normalizedArtist = normalize(artist)
                for (i in 0 until results.length()) {
                    val candidate = results.getJSONObject(i)
                    if (normalize(candidate.optString("artistName")) == normalizedArtist) {
                        best = candidate
                        break
                    }
                }
            }

            val album = best.optString("collectionName", "").ifBlank { null }
            val releaseDate = best.optString("releaseDate", "")
            val year = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else null
            val trackNumber = if (best.has("trackNumber")) best.optInt("trackNumber", 0).let { if (it > 0) it.toString() else null } else null
            val genre = best.optString("primaryGenreName", "").ifBlank { null }

            if (album == null && year == null && trackNumber == null && genre == null) return@withContext null
            OnlineTrackInfo(album = album, year = year, trackNumber = trackNumber, genre = genre)
        } catch (e: Exception) {
            // Sem internet, timeout, nada encontrado — não é erro grave,
            // só significa que não deu pra preencher automaticamente dessa
            // vez; o usuário continua podendo preencher na mão.
            null
        }
    }

    private fun normalize(s: String): String =
        java.text.Normalizer.normalize(s.lowercase().trim(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
}
