package com.harmonic.player

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Tela que aparece quando o app trava, em vez de simplesmente fechar sem
 * explicação. Escrita só com Views do Android puro (sem Compose, sem nada
 * do resto do app) de propósito — assim ela continua funcionando mesmo que
 * o crash tenha sido causado por algo no Compose/tema/recursos do app.
 *
 * Mostra o erro exato (stack trace) numa área selecionável, com um botão
 * "Copiar erro" pra facilitar mandar pra mim depurar, já que não dá pra
 * plugar no Android Studio e olhar o Logcat diretamente.
 */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val trace = intent.getStringExtra(EXTRA_STACK_TRACE)
            ?: getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_LAST_CRASH, null)
            ?: "Sem detalhes do erro."

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0221"))
            setPadding(40, 90, 40, 40)
        }

        root.addView(TextView(this).apply {
            text = "O Music Box travou"
            setTextColor(Color.WHITE)
            textSize = 20f
            setPadding(0, 0, 0, 12)
        })

        root.addView(TextView(this).apply {
            text = "Copie o erro abaixo (botão \"Copiar erro\") e envie pra ele ser corrigido."
            setTextColor(Color.LTGRAY)
            textSize = 14f
            setPadding(0, 0, 0, 24)
        })

        val traceView = TextView(this).apply {
            text = trace
            setTextColor(Color.parseColor("#33FF88"))
            textSize = 12f
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val scroll = ScrollView(this).apply { addView(traceView) }
        root.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        root.addView(Button(this).apply {
            text = "Copiar erro"
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("crash_log", trace))
                Toast.makeText(this@CrashActivity, "Copiado!", Toast.LENGTH_SHORT).show()
            }
        })

        root.addView(Button(this).apply {
            text = "Fechar"
            setOnClickListener { finishAffinity() }
        })

        setContentView(root)
    }

    companion object {
        const val EXTRA_STACK_TRACE = "stack_trace"
        const val PREFS_NAME = "crash_log"
        const val KEY_LAST_CRASH = "last_crash"
    }
}
