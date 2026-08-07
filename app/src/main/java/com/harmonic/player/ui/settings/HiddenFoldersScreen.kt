package com.harmonic.player.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.harmonic.player.data.HiddenFolder
import com.harmonic.player.data.MusicDatabase
import kotlinx.coroutines.launch

/**
 * Lista todas as pastas com música (mesmo as já escondidas) com um switch
 * pra cada uma — liga = aparece na biblioteca, desliga = some da aba
 * Pastas e da lista de Músicas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenFoldersScreen(database: MusicDatabase, onBack: () -> Unit) {
    val dao = database.songDao()
    val scope = rememberCoroutineScope()
    val allFolders by dao.getAllFoldersIncludingHidden().collectAsState(initial = emptyList())
    val hiddenFolders by dao.getHiddenFolders().collectAsState(initial = emptyList())

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Pastas ocultas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (allFolders.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("Nenhuma pasta encontrada ainda", color = Color.White.copy(alpha = 0.6f))
            }
            return@Scaffold
        }

        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(allFolders, key = { it }) { folder ->
                val isHidden = hiddenFolders.contains(folder)
                val folderName = folder.trimEnd('/').substringAfterLast('/')
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        Icon(
                            if (isHidden) Icons.Filled.VisibilityOff else Icons.Filled.Folder,
                            contentDescription = null,
                            tint = if (isHidden) Color.White.copy(alpha = 0.35f) else MaterialTheme.colorScheme.primary
                        )
                    },
                    headlineContent = {
                        Text(
                            folderName.ifBlank { folder },
                            color = if (isHidden) Color.White.copy(alpha = 0.4f) else Color.White
                        )
                    },
                    supportingContent = {
                        Text(folder, color = Color.White.copy(alpha = if (isHidden) 0.3f else 0.55f))
                    },
                    trailingContent = {
                        com.harmonic.player.ui.common.ThemedSwitch(
                            checked = !isHidden,
                            onCheckedChange = { visible ->
                                scope.launch {
                                    if (visible) dao.unhideFolder(folder) else dao.hideFolder(HiddenFolder(folder))
                                }
                            }
                        )
                    }
                )
            }
        }
        com.harmonic.player.ui.common.FastScrollbar(
            listState = listState,
            itemCount = allFolders.size,
            modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd)
        )
        }
    }
}
