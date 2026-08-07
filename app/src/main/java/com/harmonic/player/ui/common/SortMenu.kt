package com.harmonic.player.ui.common

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Uma opção de ordenação — [key] é um identificador interno estável, [label] é o texto mostrado. */
data class SortOption(val key: String, val label: String)

/**
 * Botão de "ordenar por" com um menu suspenso: lista de critérios (ex.
 * Título/Artista/Duração/Data adicionada) + um toggle de
 * crescente/decrescente no final. Reutilizado em Músicas, Playlists,
 * Pastas, Artistas e Álbuns — só a lista de [options] muda.
 */
@Composable
fun SortMenuButton(
    options: List<SortOption>,
    selectedKey: String,
    ascending: Boolean,
    onSelect: (String) -> Unit,
    onToggleDirection: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // Mesmo tamanho do botão de shuffle ao lado (32dp/18dp) — antes este
    // ficava no tamanho padrão do IconButton (48dp), forçando a barra de
    // totais inteira a ficar mais alta que o necessário só por causa dele,
    // sobrando bastante espaço vazio acima/abaixo do texto pequeno ao lado.
    IconButton(onClick = { expanded = true }, modifier = androidx.compose.ui.Modifier.size(32.dp)) {
        Icon(
            Icons.Filled.Sort,
            contentDescription = "Ordenar por",
            tint = Color.White.copy(alpha = 0.85f),
            modifier = androidx.compose.ui.Modifier.size(18.dp)
        )
    }

    // Menu bem mais curto que os outros (só uma lista de critérios + um
    // toggle crescente/decrescente), então usa uma largura menor — os
    // demais menus do app (música, álbum, playlist...) usam a largura
    // padrão de [ThemedDropdownMenu] pra ficarem visualmente consistentes
    // entre si.
    ThemedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, widthDp = 200.dp) {
        val onAccent = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
        androidx.compose.material3.Text(
            "Ordenar por",
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            color = onAccent.copy(alpha = 0.6f),
            modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        HorizontalDivider(thickness = 0.5.dp, color = onAccent.copy(alpha = 0.15f))
        options.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.label, color = onAccent) },
                leadingIcon = {
                    if (option.key == selectedKey) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = onAccent)
                    }
                },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = androidx.compose.ui.Modifier.heightIn(min = 40.dp),
                onClick = {
                    onSelect(option.key)
                    expanded = false
                }
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = onAccent.copy(alpha = 0.15f))
        DropdownMenuItem(
            text = { Text(if (ascending) "Crescente" else "Decrescente", color = onAccent) },
            leadingIcon = {
                Icon(
                    if (ascending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                    contentDescription = null,
                    tint = onAccent
                )
            },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 0.dp),
            modifier = androidx.compose.ui.Modifier.heightIn(min = 40.dp),
            onClick = {
                onToggleDirection()
                expanded = false
            }
        )
    }
}
