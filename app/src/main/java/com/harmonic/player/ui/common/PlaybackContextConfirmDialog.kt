package com.harmonic.player.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.harmonic.player.playback.PlayerController

/**
 * Mostrado quando o usuário dá play em algo de um lugar diferente (ex: um
 * álbum) enquanto está ouvindo uma playlist com shuffle/repetir ligados —
 * avisa que isso vai resetar o shuffle/repetir do que estava tocando antes,
 * e deixa confirmar ou cancelar. Montado uma única vez, em cima de toda a
 * navegação, então funciona não importa de qual tela o play foi disparado.
 */
@Composable
fun PlaybackContextConfirmDialog(playerController: PlayerController) {
    val pending by playerController.pendingPlayRequest.collectAsState()
    val request = pending ?: return

    AlertDialog(
        onDismissRequest = { playerController.cancelPendingPlay() },
        title = { Text("Trocar o que está tocando?") },
        text = {
            Text(
                "Você está ouvindo algo com aleatório e/ou repetir ativados. " +
                "Tocar \"${request.sourceLabel}\" agora vai desligar isso e " +
                "começar do zero, na ordem normal. Quer continuar?"
            )
        },
        confirmButton = {
            TextButton(onClick = { playerController.confirmPendingPlay() }) { Text("Tocar mesmo assim") }
        },
        dismissButton = {
            TextButton(onClick = { playerController.cancelPendingPlay() }) { Text("Cancelar") }
        }
    )
}
