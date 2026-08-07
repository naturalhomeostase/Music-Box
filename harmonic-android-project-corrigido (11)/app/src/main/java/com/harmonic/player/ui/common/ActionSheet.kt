package com.harmonic.player.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Vermelho fixo do próprio app pra ações "perigosas" (Excluir) dentro do
 * [ThemedDropdownMenu]. Não usa `MaterialTheme.colorScheme.error` porque
 * esse vem do tema dinâmico/padrão do Android (Material You no Android
 * 12+, ou o vermelho padrão do Material Design), e o fundo desse menu é
 * pintado com a cor de destaque ESCOLHIDA NO APP — a combinação das duas
 * cores, vindas de lugares diferentes, às vezes ficava ilegível.
 */
val DangerColor = Color(0xFFFF6B5B)

/**
 * Um item do menu de opções (música/playlist/pasta/artista/álbum...).
 * [tint] é opcional — usado por ações "perigosas" tipo Excluir, que ficam
 * em vermelho em vez da cor padrão.
 */
data class ActionSheetItem(
    val icon: ImageVector,
    val label: String,
    val tint: Color? = null,
    val onClick: () -> Unit
)

/**
 * Menu suspenso genérico com uma lista de ações — usado por todos os menus
 * "..."/"⋮" do app (música, playlist, pasta, artista, álbum). Em vez de uma
 * folha subindo do rodapé da tela, abre colado ao lado do botão que o
 * aciona (mesmo comportamento do menu "Ordenar por"), com a cor de destaque
 * do tema como fundo — pintura feita pelo [ThemedDropdownMenu] por baixo.
 *
 * IMPORTANTE PRA QUEM CHAMA: assim como o [androidx.compose.material3.DropdownMenu]
 * padrão, este composable precisa ser declarado logo em seguida (dentro do
 * mesmo Box/Row) do botão que abre o menu, senão ele não vai aparecer
 * alinhado do lado certo. Ele também não fecha sozinho quando um item é
 * tocado — quem chama decide isso dentro do próprio `onClick` do item (ex:
 * `{ expanded = false; showRenameDialog = true }`), já que várias ações
 * (renomear, cortar, escolher playlist...) precisam abrir um diálogo de
 * continuação logo depois.
 */
@Composable
fun ActionSheet(
    expanded: Boolean,
    onDismiss: () -> Unit,
    title: String? = null,
    subtitle: String? = null,
    items: List<ActionSheetItem>
) {
    ThemedDropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        val onAccent = MaterialTheme.colorScheme.onSurface
        if (title != null) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = onAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = onAccent.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            HorizontalDivider(color = onAccent.copy(alpha = 0.18f))
        }
        items.forEachIndexed { index, item ->
            DropdownMenuItem(
                // "Excluir" (e outras ações antes marcadas com tint =
                // DangerColor) agora usa a MESMA cor dos outros itens do
                // menu — o vermelho fixo às vezes ficava pouco legível em
                // cima de certas cores de destaque (o fundo do menu muda
                // com o tema escolhido), e não tinha necessidade de ser
                // vermelho pra começo de conversa.
                text = { Text(item.label, color = onAccent) },
                leadingIcon = { Icon(item.icon, contentDescription = null, tint = onAccent) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = Modifier.heightIn(min = 40.dp),
                onClick = item.onClick
            )
            // Linha fininha quase transparente entre as opções — só não
            // depois da última, pra não sobrar uma linha solta embaixo.
            if (index != items.lastIndex) {
                HorizontalDivider(thickness = 0.5.dp, color = onAccent.copy(alpha = 0.12f))
            }
        }
    }
}
