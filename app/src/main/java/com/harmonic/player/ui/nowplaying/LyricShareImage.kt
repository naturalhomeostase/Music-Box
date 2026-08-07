package com.harmonic.player.ui.nowplaying

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Monta uma imagem quadrada (formato "story") com a capa da música
 * desfocada/escurecida de fundo e o trecho de letra escolhido centralizado
 * por cima — do jeito que Spotify/Apple Music fazem no "compartilhar
 * letra". Desenhado direto em Canvas (não via Compose) porque a versão do
 * Compose usada neste projeto ainda não tem a API de captura de bitmap de
 * uma árvore de composables.
 */
object LyricShareImage {

    suspend fun generate(
        context: Context,
        albumArt: Bitmap?,
        lyricText: String,
        songTitle: String,
        artist: String
    ): Uri? = withContext(Dispatchers.Default) {
        val size = 1080
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Fundo: a própria capa, cortada em quadrado e esticada pra cobrir
        // tudo — mesma lógica de "blur no fundo" da tela normal, mas sem
        // recursos de blur do Compose (aqui é Canvas puro).
        if (albumArt != null) {
            val cropped = cropToSquare(albumArt)
            canvas.drawBitmap(cropped, null, android.graphics.Rect(0, 0, size, size), null)
        } else {
            canvas.drawColor(Color.parseColor("#141414"))
        }

        // Véu escuro (mais forte embaixo, onde fica o texto) pra garantir
        // contraste com a letra branca, não importa a cor/brilho da capa.
        val scrimPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, size.toFloat(),
                intArrayOf(0x66000000, 0xB3000000.toInt(), 0xE6000000.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), scrimPaint)

        // Trecho da letra, centralizado verticalmente na metade de baixo.
        val lyricPaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 54f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val textWidth = size - 160
        val staticLayout = StaticLayout.Builder
            .obtain(lyricText, 0, lyricText.length, lyricPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(1.15f, 1.15f)
            .build()

        canvas.save()
        canvas.translate(size / 2f, size * 0.58f - staticLayout.height / 2f)
        staticLayout.draw(canvas)
        canvas.restore()

        // Rodapé: título + artista + marca do app.
        val footerPaint = TextPaint().apply {
            color = Color.argb(200, 255, 255, 255)
            textSize = 32f
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val footerSubPaint = TextPaint(footerPaint).apply {
            textSize = 26f
            typeface = Typeface.DEFAULT
            color = Color.argb(160, 255, 255, 255)
        }
        canvas.drawText(songTitle, size / 2f, size - 130f, footerPaint)
        canvas.drawText(artist, size / 2f, size - 90f, footerSubPaint)
        canvas.drawText("Music Box", size / 2f, size - 40f, footerSubPaint)

        val dir = File(context.cacheDir, "lyric_shares").apply { mkdirs() }
        val file = File(dir, "letra_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        bitmap.recycle()

        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun cropToSquare(source: Bitmap): Bitmap {
        val side = minOf(source.width, source.height)
        val x = (source.width - side) / 2
        val y = (source.height - side) / 2
        val cropped = Bitmap.createBitmap(source, x, y, side, side)
        return if (side == 1080) cropped else Bitmap.createScaledBitmap(cropped, 1080, 1080, true)
    }
}
