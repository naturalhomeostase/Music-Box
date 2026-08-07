package com.harmonic.player.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harmonic.player.data.MusicDatabase
import com.harmonic.player.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Duas checagens que a biblioteca nunca fazia sozinha:
 *
 * - Duplicatas: mesma música indexada mais de uma vez (comum quando o
 *   mesmo arquivo existe em dois formatos, ou foi baixado duas vezes em
 *   pastas diferentes) — aqui agrupamos por título+artista (ignorando
 *   maiúsculas/espaços nas pontas) e deixamos a pessoa escolher qual manter.
 * - Arquivos quebrados: o MediaStore só confirma que o arquivo EXISTIA na
 *   hora que foi indexado — se ele foi apagado/movido por fora do app
 *   depois disso e ainda não rolou um rescan, ou se o arquivo ficou com
 *   0 bytes (download incompleto, corrompido), a música continua
 *   aparecendo normalmente na biblioteca até tentar tocar e falhar. Aqui
 *   testamos os arquivos de verdade contra o disco.
 *
 * Nenhum arquivo é apagado do armazenamento — só a entrada na biblioteca
 * do app é removida (a música "some do app", mas continua no aparelho).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryMaintenanceScreen(database: MusicDatabase, onBack: () -> Unit) {
    val dao = database.songDao()
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var duplicateGroups by remember { mutableStateOf<List<List<Song>>>(emptyList()) }
    var brokenFiles by remember { mutableStateOf<List<Song>>(emptyList()) }
    // Por grupo de duplicata, qual id está marcado pra manter (o resto do grupo é removido).
    var keepSelections by remember { mutableStateOf<Map<Int, Long>>(emptyMap()) }

    suspend fun analyze() {
        loading = true
        val all = withContext(Dispatchers.IO) { dao.getAllSongsOnce() }
        val groups = withContext(Dispatchers.Default) {
            all.groupBy { it.title.trim().lowercase() to it.artist.trim().lowercase() }
                .values
                .filter { it.size > 1 }
                .toList()
        }
        val broken = withContext(Dispatchers.IO) {
            all.filter { song ->
                val file = File(song.path)
                !file.exists() || file.length() == 0L
            }
        }
        duplicateGroups = groups
        // Padrão: mantém a de maior duração (geralmente a versão sem corte/melhor qualidade).
        keepSelections = groups.mapIndexed { index, group -> index to group.maxByOrNull { it.durationMs }!!.id }.toMap()
        brokenFiles = broken
        loading = false
    }

    LaunchedEffect(Unit) { analyze() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Duplicatas e arquivos quebrados") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }

            if (duplicateGroups.isEmpty() && brokenFiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nada encontrado — biblioteca limpa!", color = Color.White.copy(alpha = 0.6f))
                }
                return@Scaffold
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                if (duplicateGroups.isNotEmpty()) {
                    item {
                        Text(
                            "Possíveis duplicatas (${duplicateGroups.size})",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
                        Text(
                            "Agrupadas por título e artista iguais. A marcada com ✓ é a que fica; toque nas outras pra escolher outra.",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    itemsIndexed(duplicateGroups) { index, group ->
                        val keepId = keepSelections[index]
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .padding(8.dp)
                        ) {
                            group.forEach { song ->
                                val isKeep = song.id == keepId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { keepSelections = keepSelections + (index to song.id) }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (isKeep) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (isKeep) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(song.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        val minutes = song.durationMs / 60000
                                        val seconds = (song.durationMs / 1000) % 60
                                        Text(
                                            "%d:%02d — %s".format(minutes, seconds, song.path.substringAfterLast('/')),
                                            color = Color.White.copy(alpha = 0.5f),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        group.filter { it.id != keepId }.forEach { dao.deleteSongById(it.id) }
                                        analyze()
                                    }
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Remover as outras desse grupo")
                            }
                        }
                    }
                }

                if (brokenFiles.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Arquivos quebrados (${brokenFiles.size})", color = Color.White, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Não existem mais no aparelho, ou estão vazios (0 bytes).",
                                    color = Color.White.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    brokenFiles.forEach { dao.deleteSongById(it.id) }
                                    analyze()
                                }
                            }) { Text("Remover todos") }
                        }
                    }
                    items(brokenFiles, key = { "broken_${it.id}" }) { song ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.BrokenImage, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(song.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(song.path, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { scope.launch { dao.deleteSongById(song.id); analyze() } }) {
                                Text("✕", color = Color.White.copy(alpha = 0.6f))
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
