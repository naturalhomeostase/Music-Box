package com.harmonic.player.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.harmonic.player.data.MusicRepository
import com.harmonic.player.data.SettingsRepository
import com.harmonic.player.data.SongDao
import com.harmonic.player.playback.PlayerController
import com.harmonic.player.ui.library.LibraryTab
import kotlinx.coroutines.launch

/**
 * Configurações "de verdade" do app — hub central. A customização de tema
 * (gradientes/imagem/cor de destaque) que antes era a própria tela de
 * Configurações agora é só uma das opções daqui ("Mudar tema").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: SettingsRepository,
    musicRepository: MusicRepository,
    playerController: PlayerController,
    dao: SongDao,
    onBack: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenHiddenFolders: () -> Unit,
    onOpenHiddenSongs: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenMaintenance: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val albumGridView by settings.albumGridView.collectAsState(initial = false)
    val artistGridView by settings.artistGridView.collectAsState(initial = false)
    val hiddenTabs by settings.hiddenTabs.collectAsState(initial = emptySet())
    var showTabsDialog by remember { mutableStateOf(false) }
    var showPlaybackDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var backupImportResult by remember { mutableStateOf<com.harmonic.player.data.BackupManager.ImportResult?>(null) }
    val backupImportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                backupImportResult = com.harmonic.player.data.BackupManager.import(context, uri, dao, settings)
            }
        }
    }
    var scanning by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Configurações") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        val settingsScrollState = androidx.compose.foundation.rememberScrollState()
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(settingsScrollState)
        ) {
            SettingsRow(
                icon = Icons.Filled.Palette,
                title = "Mudar tema",
                subtitle = "Gradientes, imagem de fundo, cor de destaque e gradiente dos títulos",
                onClick = onOpenTheme
            )

            SettingsRow(
                icon = Icons.Filled.GraphicEq,
                title = "Equalizador",
                subtitle = "Presets, bandas de frequência, bass boost, virtualizador e reverb",
                onClick = onOpenEqualizer
            )

            SettingsRow(
                icon = Icons.Filled.Speed,
                title = "Reprodução",
                subtitle = "Velocidade, crossfade entre músicas e normalizar volume",
                onClick = { showPlaybackDialog = true }
            )

            SettingsRow(
                icon = Icons.Filled.GridView,
                title = "Visualização em grade dos álbuns",
                subtitle = "Mostra os álbuns como capas em grade em vez de lista",
                onClick = { scope.launch { settings.setAlbumGridView(!albumGridView) } },
                trailing = {
                    com.harmonic.player.ui.common.ThemedSwitch(
                        checked = albumGridView,
                        onCheckedChange = { scope.launch { settings.setAlbumGridView(it) } }
                    )
                }
            )

            SettingsRow(
                icon = Icons.Filled.GridView,
                title = "Visualização em grade dos artistas",
                subtitle = "Mostra os artistas com foto em grade em vez de lista",
                onClick = { scope.launch { settings.setArtistGridView(!artistGridView) } },
                trailing = {
                    com.harmonic.player.ui.common.ThemedSwitch(
                        checked = artistGridView,
                        onCheckedChange = { scope.launch { settings.setArtistGridView(it) } }
                    )
                }
            )

            SettingsRow(
                icon = Icons.Filled.FolderOff,
                title = "Pastas ocultas",
                subtitle = "Escolha quais pastas ficam de fora da biblioteca",
                onClick = onOpenHiddenFolders
            )

            SettingsRow(
                icon = Icons.Filled.VisibilityOff,
                title = "Músicas ocultas",
                subtitle = "Veja e reexiba músicas ocultadas individualmente (ou álbuns inteiros)",
                onClick = onOpenHiddenSongs
            )

            SettingsRow(
                icon = Icons.Filled.Checklist,
                title = "Abas visíveis",
                subtitle = "Escolha quais abas aparecem na tela principal",
                onClick = { showTabsDialog = true }
            )

            SettingsRow(
                icon = Icons.Filled.Sync,
                title = if (scanning) "Escaneando..." else "Escanear novas músicas",
                subtitle = "Procura por músicas baixadas recentemente que ainda não apareceram no app",
                onClick = {
                    if (!scanning) {
                        scanning = true
                        musicRepository.rescanNow(scope)
                        // Feedback simples: a varredura roda em segundo
                        // plano, então só mostramos "escaneando" por um
                        // tempinho — não temos um sinal exato de "terminou"
                        // exposto aqui sem mudar mais a fundo o repositório.
                        scope.launch {
                            kotlinx.coroutines.delay(2500)
                            scanning = false
                        }
                    }
                }
            )

            val notifContext = androidx.compose.ui.platform.LocalContext.current
            SettingsRow(
                icon = Icons.Filled.Notifications,
                title = "Notificação do player",
                subtitle = "Se os botões de play/pause não aparecerem na barra de notificação, confira aqui se a permissão está ativada",
                onClick = {
                    val intent = if (android.os.Build.VERSION.SDK_INT >= 26) {
                        android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, notifContext.packageName)
                    } else {
                        android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(android.net.Uri.parse("package:${notifContext.packageName}"))
                    }
                    notifContext.startActivity(intent)
                }
            )

            SettingsRow(
                icon = Icons.Filled.BatteryChargingFull,
                title = "Otimização de bateria",
                subtitle = "Em alguns celulares (Xiaomi, Samsung...), isso também precisa estar desativado pro player não ser encerrado sozinho em segundo plano",
                onClick = {
                    val pm = notifContext.getSystemService(android.os.PowerManager::class.java)
                    if (pm != null && !pm.isIgnoringBatteryOptimizations(notifContext.packageName)) {
                        try {
                            notifContext.startActivity(
                                android.content.Intent(
                                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    android.net.Uri.parse("package:${notifContext.packageName}")
                                )
                            )
                        } catch (e: Exception) {
                            notifContext.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    } else {
                        android.widget.Toast.makeText(notifContext, "Já está desativada pra esse app", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            )

            SettingsRow(
                icon = Icons.Filled.Insights,
                title = "Histórico e estatísticas",
                subtitle = "Resumo de reproduções, mais tocadas e tocadas recentemente",
                onClick = onOpenHistory
            )

            SettingsRow(
                icon = Icons.Filled.ContentCopy,
                title = "Duplicatas e arquivos quebrados",
                subtitle = "Encontra músicas repetidas na biblioteca e arquivos que não existem mais",
                onClick = onOpenMaintenance
            )

            SettingsRow(
                icon = Icons.Filled.Backup,
                title = "Backup",
                subtitle = "Salvar ou restaurar configurações, playlists e favoritos",
                onClick = { showBackupDialog = true }
            )

            SettingsRow(
                icon = Icons.Filled.Info,
                title = "Sobre",
                subtitle = "Versão, licenças, contato e privacidade",
                onClick = onOpenAbout
            )
        }
        }
    }

    if (showTabsDialog) {
        AlertDialog(
            onDismissRequest = { showTabsDialog = false },
            title = { Text("Abas visíveis") },
            text = {
                Column {
                    Text(
                        "Músicas não pode ser escondida.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    LibraryTab.values().filter { it != LibraryTab.SONGS }.forEach { tab ->
                        val isHidden = hiddenTabs.contains(tab.name)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { scope.launch { settings.setTabHidden(tab.name, !isHidden) } }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(tab.label)
                            com.harmonic.player.ui.common.ThemedSwitch(
                                checked = !isHidden,
                                onCheckedChange = { visible -> scope.launch { settings.setTabHidden(tab.name, !visible) } }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTabsDialog = false }) { Text("Fechar") }
            }
        )
    }

    if (showPlaybackDialog) {
        val playbackSpeed by settings.playbackSpeed.collectAsState(initial = 1f)
        val crossfadeMs by settings.crossfadeMs.collectAsState(initial = 0)
        val replayGainEnabled by settings.replayGainEnabled.collectAsState(initial = false)

        AlertDialog(
            onDismissRequest = { showPlaybackDialog = false },
            title = { Text("Reprodução") },
            text = {
                Column {
                    Text(
                        "Velocidade: ${String.format("%.2fx", playbackSpeed)}",
                        color = Color.White
                    )
                    Text(
                        "O tom da música continua o mesmo em qualquer velocidade.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Slider(
                        value = playbackSpeed,
                        onValueChange = { speed ->
                            scope.launch { settings.setPlaybackSpeed(speed) }
                            playerController.setPlaybackSpeed(speed)
                        },
                        valueRange = 0.5f..2f,
                        steps = 29 // passos de 0.05x entre 0.5x e 2x
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        if (crossfadeMs <= 0) "Crossfade: desligado" else "Crossfade: ${String.format("%.1f", crossfadeMs / 1000f)}s",
                        color = Color.White
                    )
                    Text(
                        "O volume desce suavemente no fim de uma música e sobe no início da próxima, em vez do corte seco.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Slider(
                        value = crossfadeMs.toFloat(),
                        onValueChange = { ms -> scope.launch { settings.setCrossfadeMs(ms.toInt()) } },
                        valueRange = 0f..8000f,
                        steps = 15 // passos de 500ms
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { scope.launch { settings.setReplayGainEnabled(!replayGainEnabled) } },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Normalizar volume", color = Color.White)
                            Text(
                                "Deixa músicas gravadas mais altas mais parecidas em volume com as outras. Só funciona em músicas que já trazem essa informação salva no arquivo (a maioria não traz).",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                        com.harmonic.player.ui.common.ThemedSwitch(
                            checked = replayGainEnabled,
                            onCheckedChange = { scope.launch { settings.setReplayGainEnabled(it) } }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaybackDialog = false }) { Text("Fechar") }
            }
        )
    }

    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text("Backup") },
            text = {
                Column {
                    Text(
                        "Salva configurações, playlists e favoritos num arquivo .json. Ao restaurar, as músicas são reencontradas pelo caminho do arquivo — as que não existirem mais nesse aparelho são só ignoradas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    val uri = com.harmonic.player.data.BackupManager.export(context, dao, settings)
                                    com.harmonic.player.data.BackupManager.share(context, uri)
                                }
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Backup, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(12.dp))
                        Text("Criar e compartilhar backup", color = Color.White)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { backupImportLauncher.launch("application/json") }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(12.dp))
                        Text("Restaurar de um backup", color = Color.White)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBackupDialog = false }) { Text("Fechar") }
            }
        )
    }

    backupImportResult?.let { result ->
        AlertDialog(
            onDismissRequest = { backupImportResult = null },
            title = { Text("Backup restaurado") },
            text = {
                Column {
                    Text("${result.favoritesRestored} favoritos e ${result.playlistsRestored} playlists restaurados.", color = Color.White)
                    if (result.songsNotFound > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${result.songsNotFound} música(s) do backup não foram encontradas nesse aparelho (movidas, apagadas, ou a pasta ainda não foi escaneada) e ficaram de fora.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { backupImportResult = null }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        },
        headlineContent = { Text(title, color = Color.White) },
        supportingContent = { Text(subtitle, color = Color.White.copy(alpha = 0.65f)) },
        trailingContent = trailing ?: {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
        }
    )
}
