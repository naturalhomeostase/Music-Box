package com.harmonic.player

import android.app.Application
import android.content.Context
import android.content.Intent
import com.harmonic.player.data.MediaStoreScanner
import com.harmonic.player.data.MusicDatabase
import com.harmonic.player.data.MusicRepository
import com.harmonic.player.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class HarmonicApp : Application() {
    // Escopo de corrotina que vive enquanto o app existir — o escaneamento
    // do MediaStore roda aqui, não dentro de uma tela, então não reinicia
    // toda vez que o usuário navega entre telas.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { MusicDatabase.getInstance(this) }
    val settings by lazy { SettingsRepository(this) }
    private val scanner by lazy { MediaStoreScanner(this) }
    val musicRepository by lazy { MusicRepository(scanner, database.songDao(), settings) }

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
        musicRepository.startObserving(appScope)
    }

    /**
     * Sem isso, qualquer exceção não tratada matava o processo na hora — o
     * app "abre e fecha" sem deixar nenhum rastro visível, e como não dá
     * pra plugar num Android Studio pra olhar o Logcat, não tinha como
     * saber o que realmente aconteceu. Agora, ao travar, abre a
     * [CrashActivity] mostrando o erro exato (com botão de copiar) em vez
     * de só fechar silenciosamente.
     */
    private fun installCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            try {
                val sw = java.io.StringWriter()
                throwable.printStackTrace(java.io.PrintWriter(sw))
                val trace = sw.toString()

                // Salva também em SharedPreferences: se por algum motivo a
                // CrashActivity não conseguir abrir a tempo (processo
                // morrendo rápido demais), o erro ainda fica recuperável.
                getSharedPreferences(CrashActivity.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(CrashActivity.KEY_LAST_CRASH, trace)
                    .apply()

                val intent = Intent(this, CrashActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra(CrashActivity.EXTRA_STACK_TRACE, trace)
                }
                startActivity(intent)
            } catch (e: Exception) {
                // Se nem isso funcionar, cai pro comportamento padrão do
                // Android abaixo em vez de travar sem chance nenhuma.
            } finally {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }
}
