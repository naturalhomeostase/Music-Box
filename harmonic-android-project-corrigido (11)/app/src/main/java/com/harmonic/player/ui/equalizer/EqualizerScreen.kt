package com.harmonic.player.ui.equalizer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.harmonic.player.data.SettingsRepository
import com.harmonic.player.playback.EqualizerController
import com.harmonic.player.playback.equalizerPresets
import com.harmonic.player.playback.reverbPresetNames
import com.harmonic.player.playback.toBandLevels
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    equalizerController: EqualizerController,
    settings: SettingsRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val eqState by equalizerController.uiState.collectAsState()
    var showResetConfirm by remember { mutableStateOf(false) }

    // Rede de segurança extra: sempre que essa tela abre, tenta conectar de
    // novo com o ID de sessão mais recente conhecido — em vez de confiar
    // 100% no listener reativo lá em cima (que só reage quando o ID MUDA e,
    // na prática, pode perder alguma transição). Isso é barato: se já
    // estiver conectado com o mesmo ID, `attach()` não faz nada.
    LaunchedEffect(Unit) {
        val currentSessionId = com.harmonic.player.playback.PlaybackAudioSession.sessionId.value
        if (currentSessionId != 0 && !eqState.ready) {
            equalizerController.attach(currentSessionId)
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Resetar equalizador?") },
            text = { Text("Bandas de frequência, Bass Boost, Virtualizador e Reverb voltam todos a zero. Essa ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(onClick = {
                    equalizerController.resetAll()
                    scope.launch {
                        settings.setEqBandLevels(eqState.bands.map { 0 })
                        settings.setBassBoostStrength(0)
                        settings.setVirtualizerStrength(0)
                        settings.setReverbPreset(0)
                    }
                    showResetConfirm = false
                }) {
                    Text("Resetar", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Equalizador", color = MaterialTheme.colorScheme.primary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    // Só faz sentido resetar se já existe reprodução conectada — sem
                    // isso, um toque aqui não teria efeito nenhum (nada pra resetar).
                    IconButton(
                        onClick = { showResetConfirm = true },
                        enabled = eqState.ready
                    ) {
                        Icon(
                            Icons.Filled.RestartAlt,
                            contentDescription = "Resetar equalizador",
                            tint = if (eqState.ready) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                    com.harmonic.player.ui.common.ThemedSwitch(
                        checked = eqState.enabled,
                        onCheckedChange = { enabled ->
                            equalizerController.setEnabled(enabled)
                            scope.launch { settings.setEqEnabled(enabled) }
                        },
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (!eqState.ready) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (eqState.attachFailed)
                            "Não consegui conectar o equalizador ao áudio deste aparelho.\n" +
                            "Tenta pausar e tocar a música de novo — se continuar assim, esse aparelho pode não suportar o equalizador do sistema."
                        else
                            "Toque em uma música pra ativar o equalizador.\n" +
                            "Ele precisa de uma reprodução em andamento pra se conectar ao áudio.",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(32.dp)
                    )
                    if (eqState.attachFailed && eqState.attachErrorMessage != null) {
                        // Mostra o erro de verdade direto na tela — sem
                        // isso, só dava pra saber a causa real olhando o
                        // logcat pelo computador.
                        Text(
                            eqState.attachErrorMessage!!,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Presets", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            // Nenhum chip de preset nunca aparecia marcado, mesmo quando os
            // níveis atuais batiam exatamente com um preset (ex: acabou de
            // tocar nele). Comparando os níveis atuais com o que cada
            // preset geraria, dá pra saber e destacar qual está ativo.
            val activePreset = equalizerPresets.firstOrNull {
                it.toBandLevels(eqState.bands) == eqState.bandLevels
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(equalizerPresets) { preset ->
                    val isSelected = preset == activePreset
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            val newLevels = preset.toBandLevels(eqState.bands)
                            newLevels.forEachIndexed { index, level ->
                                equalizerController.setBandLevel(index, level)
                            }
                            scope.launch { settings.setEqBandLevels(newLevels, preset.name) }
                            if (!eqState.enabled) {
                                equalizerController.setEnabled(true)
                                scope.launch { settings.setEqEnabled(true) }
                            }
                        },
                        label = { Text(preset.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            labelColor = MaterialTheme.colorScheme.primary,
                            iconColor = MaterialTheme.colorScheme.primary,
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        // A borda padrão do FilterChip é um cinza fixo do
                        // Material, que em temas mais escuros/saturados
                        // ficava quase invisível. Usando a cor de destaque
                        // do tema (mais discreta, com alpha), o contorno
                        // sempre tem contraste suficiente, seja qual for o
                        // tema escolhido.
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                            borderWidth = 1.dp
                        )
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Bandas de frequência", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                eqState.bands.forEach { band ->
                    val level = eqState.bandLevels.getOrElse(band.index) { 0 }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier.width(48.dp).height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Slider(
                                value = level.toFloat(),
                                onValueChange = { newValue ->
                                    equalizerController.setBandLevel(band.index, newValue.toInt())
                                },
                                onValueChangeFinished = {
                                    scope.launch { settings.setEqBandLevels(eqState.bandLevels) }
                                },
                                valueRange = band.minLevel.toFloat()..band.maxLevel.toFloat(),
                                // Mesmo ajuste da tela de Aparência (blur/sombra): a
                                // trilha "restante" cinza destoava da cor de destaque
                                // do tema, então agora usa a mesma cor, só mais clara.
                                colors = SliderDefaults.colors(
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                                ),
                                // O Box em volta só tem 48dp de largura — menos que os
                                // 160dp pedidos aqui — então o `.width(160.dp)` comum
                                // era espremido pra caber nesses 48dp ANTES de girar,
                                // e só depois disso o `.rotate(-90f)` agia. Resultado:
                                // um slider giradinho só que pequeno (era isso que
                                // aparecia pequeno no print). `requiredWidth` ignora
                                // essa restrição do pai e garante os 160dp de verdade
                                // antes de girar, preenchendo a altura toda da caixinha.
                                modifier = Modifier
                                    .requiredWidth(160.dp)
                                    .rotate(-90f)
                            )
                        }
                        Text(
                            if (band.centerFreqHz >= 1000) "${band.centerFreqHz / 1000}kHz" else "${band.centerFreqHz}Hz",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            // O HorizontalDivider() sem cor cai no outlineVariant padrão do
            // Material, que é praticamente preto — destoava de qualquer
            // tema. Usando a cor de destaque bem discreta (12% de opacidade,
            // igual já fazemos nos separadores do menu de Reverb).
            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            Spacer(Modifier.height(16.dp))

            Text("Bass Boost", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (!eqState.bassBoostAvailable) {
                Text(
                    "Não suportado neste aparelho.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Slider(
                enabled = eqState.bassBoostAvailable,
                value = eqState.bassBoostStrength.toFloat(),
                onValueChange = { equalizerController.setBassBoostStrength(it.toInt()) },
                onValueChangeFinished = { scope.launch { settings.setBassBoostStrength(eqState.bassBoostStrength) } },
                valueRange = 0f..1000f,
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                )
            )

            Spacer(Modifier.height(8.dp))

            Text("Virtualizador (efeito surround)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (!eqState.virtualizerAvailable) {
                Text(
                    "Não suportado neste aparelho.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Slider(
                enabled = eqState.virtualizerAvailable,
                value = eqState.virtualizerStrength.toFloat(),
                onValueChange = { equalizerController.setVirtualizerStrength(it.toInt()) },
                onValueChangeFinished = { scope.launch { settings.setVirtualizerStrength(eqState.virtualizerStrength) } },
                valueRange = 0f..1000f,
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                )
            )

            Spacer(Modifier.height(8.dp))

            Text("Reverb", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (!eqState.reverbAvailable) {
                Text(
                    "Não suportado neste aparelho.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Spacer(Modifier.height(4.dp))
            var reverbMenuExpanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { reverbMenuExpanded = true },
                    enabled = eqState.reverbAvailable,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                    )
                ) {
                    Text(reverbPresetNames.getOrElse(eqState.reverbPreset) { "Nenhum" })
                }
                com.harmonic.player.ui.common.ThemedDropdownMenu(expanded = reverbMenuExpanded, onDismissRequest = { reverbMenuExpanded = false }) {
                    val onAccent = MaterialTheme.colorScheme.onSurface
                    reverbPresetNames.forEachIndexed { index, name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            modifier = Modifier.heightIn(min = 40.dp),
                            onClick = {
                                equalizerController.setReverbPreset(index)
                                scope.launch { settings.setReverbPreset(index) }
                                reverbMenuExpanded = false
                            }
                        )
                        if (index != reverbPresetNames.lastIndex) {
                            HorizontalDivider(thickness = 0.5.dp, color = onAccent.copy(alpha = 0.12f))
                        }
                    }
                }
            }
        }
    }
}
