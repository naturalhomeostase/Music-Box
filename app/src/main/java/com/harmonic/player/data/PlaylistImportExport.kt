package com.harmonic.player.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Suporte ao formato M3U — o padrão universal de playlist, entendido por
 * praticamente todo player de música (inclusive fora do Android), o que
 * permite importar playlists feitas em outros apps e exportar as suas.
 *
 * Formato gerado (M3U estendido):
 *   #EXTM3U
 *   #EXTINF:<duração em segundos>,<artista> - <título>
 *   /caminho/completo/do/arquivo.mp3
 */
object PlaylistImportExport {

    /**
     * Gera o arquivo .m3u8 em cache e devolve um Uri (via FileProvider) pronto
     * pra compartilhar com `Intent.ACTION_SEND` — funciona com qualquer app
     * (Drive, WhatsApp, e-mail, etc).
     */
    fun exportToM3U(context: Context, playlistName: String, songs: List<Song>): Uri {
        val sb = StringBuilder("#EXTM3U\n")
        songs.forEach { song ->
            val durationSeconds = song.durationMs / 1000
            sb.append("#EXTINF:$durationSeconds,${song.artist} - ${song.title}\n")
            sb.append(song.path).append("\n")
        }

        val exportDir = File(context.cacheDir, "exported_playlists").apply { mkdirs() }
        val safeFileName = playlistName.replace(Regex("[^a-zA-Z0-9À-ú _-]"), "_")
        val file = File(exportDir, "$safeFileName.m3u8")
        file.writeText(sb.toString())

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun shareM3U(context: Context, uri: Uri, playlistName: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/x-mpegurl"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, playlistName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartilhar playlist"))
    }

    /**
     * Lê um arquivo .m3u/.m3u8 e retorna os caminhos de arquivo encontrados
     * (ignorando linhas de metadado #EXTINF e comentários). A tela chamadora
     * é responsável por casar esses caminhos com músicas já existentes no
     * banco local — músicas que não estiverem na biblioteca são ignoradas,
     * já que apontam pra arquivos fora do aparelho.
     */
    fun parseM3U(context: Context, uri: Uri): List<String> {
        val paths = mutableListOf<String>()
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.useLines { lines ->
            lines.forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEach
                paths += line
            }
        }
        return paths
    }
}
