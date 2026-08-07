package com.harmonic.player.ui.settings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.harmonic.player.data.DefaultWallpaper
import com.harmonic.player.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Paleta ampla de cores de destaque — cobre bem mais gostos do que os 5 originais. */
private val accentPresets = listOf(
    Color(0xFFFF7043), Color(0xFFFF5252), Color(0xFFEC407A), Color(0xFFAB47BC),
    Color(0xFF7E57C2), Color(0xFF5C6BC0), Color(0xFF29B6F6), Color(0xFF26C6DA),
    Color(0xFF26A69A), Color(0xFF66BB6A), Color(0xFF9CCC65), Color(0xFFD4E157),
    Color(0xFFFFCA28), Color(0xFFFFA726), Color(0xFFF8BBD0), Color(0xFFB388FF)
)

/** Qual das duas cores do gradiente de título está sendo editada no momento. */
private enum class GradientSwatch { START, END }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(settings: SettingsRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val currentWallpaper by settings.defaultWallpaper.collectAsState(initial = null)
    val currentCustomBg by settings.backgroundUri.collectAsState(initial = null)
    val currentGradient by settings.gradientTheme.collectAsState(initial = null)
    val currentAccent by settings.accentColor.collectAsState(initial = null)
    val blurRadius by settings.backgroundBlurRadius.collectAsState(initial = 0)
    val scrimAlpha by settings.backgroundScrimAlpha.collectAsState(initial = 45)
    val titleGradientEnabled by settings.titleGradientEnabled.collectAsState(initial = false)
    val titleGradientColorStart by settings.titleGradientColorStart.collectAsState(initial = null)
    val titleGradientColorEnd by settings.titleGradientColorEnd.collectAsState(initial = null)

    var showCustomColorDialog by remember { mutableStateOf(false) }

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val savedPath = copyImageToInternalStorage(context, uri)
                if (savedPath != null) {
                    settings.setCustomBackground(savedPath)
                    // Mesma lógica dos temas prontos: a cor de destaque passa a
                    // combinar com a foto escolhida da galeria automaticamente.
                    accentFromUri(context, savedPath)?.let { settings.setAccentColor(it.toArgb()) }
                    // O mesmo vale pro gradiente dos títulos, se o usuário
                    // tiver ativado essa opção — nada de pedir pra escolher
                    // as cores na mão, igual já fazemos com a cor de destaque.
                    titleGradientFromUri(context, savedPath)?.let { (start, end) ->
                        settings.setTitleGradientColors(start.toArgb(), end.toArgb())
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Aparência", color = MaterialTheme.colorScheme.primary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Cor de destaque", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(200.dp)
            ) {
                item {
                    // Botão "+": abre o seletor de cor personalizada, com
                    // liberdade total (RGB), em vez de ficar preso a presets.
                    // Fundo transparente (só uma borda pontilhada-like sutil)
                    // pra não competir visualmente com as cores sólidas ao lado.
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Transparent)
                            .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), CircleShape)
                            .clickable { showCustomColorDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Cor personalizada", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                items(accentPresets) { color ->
                    val isSelected = currentAccent == color.toArgb()
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable {
                                scope.launch { settings.setAccentColor(color.toArgb()) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Text("Desfocar o fundo (blur)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                "0 = nítido. Só tem efeito real no Android 12 ou mais recente.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = blurRadius.toFloat(),
                onValueChange = { scope.launch { settings.setBackgroundBlurRadius(it.toInt()) } },
                valueRange = 0f..40f,
                // Mesmo ajuste feito na barra de progresso da tela "Tocando
                // agora": a trilha "restante" (mais fina) não tinha cor
                // customizada e caía no cinza padrão do Material, destoando
                // da cor de destaque do tema. Agora usa a mesma cor, só
                // mais discreta.
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                )
            )

            Spacer(Modifier.height(16.dp))

            Text("Sombra sobre o fundo", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                "Escurece a imagem/gradiente pra o texto ficar mais legível.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = scrimAlpha.toFloat(),
                onValueChange = { scope.launch { settings.setBackgroundScrimAlpha(it.toInt()) } },
                valueRange = 0f..90f,
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                )
            )

            Spacer(Modifier.height(24.dp))

            Text("Fundo do app", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                "Gradientes leves ou uma imagem — toque pra aplicar. O preview acima mostra exatamente como fica.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            // Preview "printscreen" ao vivo: reflete o estado real das
            // configurações (gradiente/imagem, blur, sombra e gradiente de
            // texto), então ele já reage assim que o usuário toca em outro
            // tema logo abaixo — sem precisar de nenhum estado paralelo.
            AppearancePreviewMockup(
                gradientTheme = com.harmonic.player.data.GradientTheme.values()
                    .find { it.name == currentGradient } ?: com.harmonic.player.data.GradientTheme.APP_ICON,
                useImageBackground = currentWallpaper != null || currentCustomBg != null,
                imageModel = currentCustomBg ?: currentWallpaper?.let {
                    "file:///android_asset/${com.harmonic.player.data.DefaultWallpaper.valueOf(it).assetPath}"
                },
                accentColor = currentAccent?.let { Color(it) } ?: MaterialTheme.colorScheme.primary,
                scrimAlphaPercent = scrimAlpha,
                titleGradientEnabled = titleGradientEnabled,
                titleColorStart = titleGradientColorStart?.let { Color(it) },
                titleColorEnd = titleGradientColorEnd?.let { Color(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.62f)
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(16.dp))

            // "Gradientes" e "Imagens" lado a lado como duas abas — só o
            // conteúdo da aba selecionada aparece abaixo, em vez de
            // empilhar as duas seções inteiras (gradientes E imagens ao
            // mesmo tempo), que ocupava espaço demais na tela.
            var selectedBackgroundTab by remember { mutableStateOf(if (currentCustomBg != null || currentWallpaper != null) 1 else 0) }
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("Gradientes", "Imagens").forEachIndexed { index, label ->
                    val isSelectedTab = selectedBackgroundTab == index
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelectedTab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isSelectedTab) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                        modifier = Modifier
                            .padding(end = 24.dp, bottom = 6.dp)
                            .clickable { selectedBackgroundTab = index }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            if (selectedBackgroundTab == 0) {
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(com.harmonic.player.data.GradientTheme.values().toList()) { theme ->
                    // `currentGradient == null` é o estado de fábrica (nunca
                    // foi salvo nada) — nesse caso o app já cai no gradiente
                    // padrão (APP_ICON) em todo lugar que lê essa config, mas
                    // a comparação abaixo não reconhecia isso e a lista de
                    // temas ficava sem NENHUM item marcado, mesmo com
                    // "Music Box" sendo o que realmente está em uso.
                    val isSelected = currentCustomBg == null && currentWallpaper == null &&
                        (currentGradient == theme.name || (currentGradient == null && theme == com.harmonic.player.data.GradientTheme.APP_ICON))
                    Box(
                        modifier = Modifier
                            .size(width = 88.dp, height = 120.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(theme.colorsArgb.map { Color(it) }))
                            .then(
                                if (isSelected)
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                                else Modifier
                            )
                            .clickable {
                                scope.launch {
                                    settings.setGradientTheme(theme)
                                    // Cor de destaque acompanha o tema escolhido
                                    // automaticamente — a opção de escolher uma
                                    // cor preferida continua disponível acima,
                                    // bastando tocar nela depois pra sobrescrever.
                                    // Exceção: o tema padrão "Music Box" já TEM
                                    // uma cor de destaque própria (o dourado do
                                    // ícone, definida em HarmonicTheme) — usar
                                    // setAccentColor aqui prenderia permanentemente
                                    // a cor mais saturada do gradiente, deixando o
                                    // "tema padrão" com uma cor errada depois de
                                    // trocar pra outro tema e voltar. Limpando a
                                    // cor customizada em vez de fixar uma nova,
                                    // ele volta a usar o dourado de verdade.
                                    if (theme == com.harmonic.player.data.GradientTheme.APP_ICON) {
                                        settings.clearAccentColor()
                                    } else {
                                        settings.setAccentColor(accentFromGradient(theme).toArgb())
                                    }
                                    // Idem pro gradiente dos títulos.
                                    val (start, end) = titleGradientFromGradientTheme(theme)
                                    settings.setTitleGradientColors(start.toArgb(), end.toArgb())
                                }
                            },
                        contentAlignment = Alignment.BottomStart
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                            }
                        }
                        Text(
                            theme.label,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                }
            }
            } else {
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                item {
                    // O "+" de volta dentro da própria caixa de demonstração
                    // de tema (primeiro card da lista de Imagens) — igual ao
                    // "+" da paleta de cores acima, em vez de um ícone solto
                    // do lado do título ocupando uma linha extra na página.
                    Box(
                        modifier = Modifier
                            .size(width = 88.dp, height = 120.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Transparent)
                            .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                            .clickable { pickImageLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Escolher imagem da galeria", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                items(DefaultWallpaper.values().toList()) { wallpaper ->
                    val isSelected = currentCustomBg == null && currentWallpaper == wallpaper.name
                    Box(
                        modifier = Modifier
                            .size(width = 88.dp, height = 120.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                scope.launch {
                                    settings.setDefaultWallpaper(wallpaper)
                                    // Extrai a cor predominante da imagem do tema
                                    // (assets do próprio app) pra combinar a cor
                                    // de destaque com o fundo escolhido.
                                    accentFromAsset(context, wallpaper.assetPath)?.let {
                                        settings.setAccentColor(it.toArgb())
                                    }
                                    // Idem pro gradiente dos títulos.
                                    titleGradientFromAsset(context, wallpaper.assetPath)?.let { (start, end) ->
                                        settings.setTitleGradientColors(start.toArgb(), end.toArgb())
                                    }
                                }
                            }
                    ) {
                        AsyncImage(
                            model = "file:///android_asset/${wallpaper.assetPath}",
                            contentDescription = wallpaper.label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                            }
                        }
                        Text(
                            wallpaper.label,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                }

                item {
                    // Sua foto já escolhida (se houver) continua aparecendo
                    // como um card dentro da linha de Imagens, marcado como
                    // selecionada — tocar nela de novo abre a galeria pra
                    // trocar por outra foto.
                    if (currentCustomBg != null) {
                        Box(
                            modifier = Modifier
                                .size(width = 88.dp, height = 120.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                                .clickable { pickImageLauncher.launch("image/*") }
                        ) {
                            AsyncImage(
                                model = Uri.parse(currentCustomBg),
                                contentDescription = "Sua foto",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                            }
                        }
                    }
                }
            }
            }

            Spacer(Modifier.height(20.dp))

            // Opção de aplicar o mesmo gradiente também no texto dos
            // títulos das listas (em vez de só no fundo) — fica a critério
            // do usuário, já que nem todo mundo gosta do efeito em texto.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Gradiente também nos títulos", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Text(
                        "Usa as cores do tema acima no título das músicas nas listas, em vez de branco sólido.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                com.harmonic.player.ui.common.ThemedSwitch(
                    checked = titleGradientEnabled,
                    onCheckedChange = { scope.launch { settings.setTitleGradientEnabled(it) } }
                )
            }

            if (titleGradientEnabled) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Por padrão, combina automaticamente com o fundo escolhido acima " +
                    "(tema, papel de parede ou foto) — igual já acontece com a cor de destaque. " +
                    "Toque numa bolinha pra escolher qualquer cor livremente, ou em \"Usar cores automáticas\" pra voltar ao padrão.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                val activeTheme = com.harmonic.player.data.GradientTheme.values()
                    .find { it.name == currentGradient } ?: com.harmonic.player.data.GradientTheme.APP_ICON
                val previewStart = titleGradientColorStart?.let { Color(it) } ?: Color(activeTheme.colorsArgb[0])
                val previewEnd = titleGradientColorEnd?.let { Color(it) } ?: Color(activeTheme.colorsArgb.last())
                var editingGradientSwatch by remember { mutableStateOf<GradientSwatch?>(null) }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cor inicial", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(previewStart)
                            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                            .clickable { editingGradientSwatch = GradientSwatch.START }
                    )
                    Spacer(Modifier.width(20.dp))
                    Text("Cor final", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(previewEnd)
                            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                            .clickable { editingGradientSwatch = GradientSwatch.END }
                    )
                }

                if (titleGradientColorStart != null || titleGradientColorEnd != null) {
                    TextButton(onClick = {
                        scope.launch {
                            val auto = when {
                                currentCustomBg != null -> titleGradientFromUri(context, currentCustomBg!!)
                                currentWallpaper != null -> titleGradientFromAsset(
                                    context,
                                    com.harmonic.player.data.DefaultWallpaper.valueOf(currentWallpaper!!).assetPath
                                )
                                else -> titleGradientFromGradientTheme(activeTheme)
                            }
                            if (auto != null) {
                                settings.setTitleGradientColors(auto.first.toArgb(), auto.second.toArgb())
                            } else {
                                settings.clearTitleGradientColors()
                            }
                        }
                    }) {
                        Text("Usar cores automáticas")
                    }
                }

                editingGradientSwatch?.let { swatch ->
                    CustomColorPickerDialog(
                        initialColor = if (swatch == GradientSwatch.START) previewStart else previewEnd,
                        onDismiss = { editingGradientSwatch = null },
                        onConfirm = { color ->
                            val newStart = if (swatch == GradientSwatch.START) color else previewStart
                            val newEnd = if (swatch == GradientSwatch.END) color else previewEnd
                            scope.launch { settings.setTitleGradientColors(newStart.toArgb(), newEnd.toArgb()) }
                            editingGradientSwatch = null
                        }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showCustomColorDialog) {
        CustomColorPickerDialog(
            initialColor = currentAccent?.let { Color(it) } ?: Color(0xFFFF7043),
            onDismiss = { showCustomColorDialog = false },
            onConfirm = { color ->
                scope.launch { settings.setAccentColor(color.toArgb()) }
                showCustomColorDialog = false
            }
        )
    }
}

/**
 * Mini "printscreen" ao vivo de como a Biblioteca fica com as configurações
 * atuais: mesmo fundo (imagem ou gradiente + blur/sombra), mesma cor de
 * destaque, e até o gradiente no título das músicas, se ativado — tudo
 * numa moldura de tela pra dar a sensação de preview real do app.
 */
@Composable
private fun AppearancePreviewMockup(
    gradientTheme: com.harmonic.player.data.GradientTheme,
    useImageBackground: Boolean,
    imageModel: Any?,
    accentColor: Color,
    scrimAlphaPercent: Int,
    titleGradientEnabled: Boolean,
    titleColorStart: Color? = null,
    titleColorEnd: Color? = null,
    modifier: Modifier = Modifier
) {
    val fakeSongs = listOf("Noite sem fim" to "Coletivo Aurora", "Deriva" to "Baía Sul", "Eco de vidro" to "Marte 91")

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(28.dp))
    ) {
        // Fundo: mesma lógica do AppBackground de verdade (imagem crop, ou gradiente)
        if (useImageBackground && imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(gradientTheme.colorsArgb.map { Color(it) }))
            )
        }

        if (scrimAlphaPercent > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlphaPercent / 100f))
            )
        }

        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            Text("Music Box", color = accentColor, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))

            // Abinha falsa, só pra dar o contexto visual do menu horizontal
            Row {
                Text(
                    "Músicas",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    "Artistas",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .width(44.dp)
                    .height(2.dp)
                    .background(accentColor)
            )

            Spacer(Modifier.height(16.dp))

            val titleBrush = if (titleGradientEnabled) {
                // Mesma função usada na Biblioteca de verdade (ver
                // LibraryScreen.kt) — sem isso o preview mostrava a cor
                // "crua" do tema, diferente do que a pessoa via depois na
                // lista de músicas.
                val source = if (titleColorStart != null && titleColorEnd != null) {
                    listOf(titleColorStart, titleColorEnd)
                } else {
                    gradientTheme.colorsArgb.map { Color(it) }
                }
                val backgroundIsDark = if (useImageBackground) {
                    true
                } else {
                    val avgLuminance = gradientTheme.colorsArgb.map { Color(it).luminance() }.average().toFloat()
                    (avgLuminance * (1f - scrimAlphaPercent / 100f)) < 0.5f
                }
                Brush.linearGradient(
                    com.harmonic.player.ui.library.readableGradientTextColors(source, backgroundIsDark)
                )
            } else null

            fakeSongs.forEach { (title, artist) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Photo,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        if (titleBrush != null) {
                            Text(
                                title,
                                style = MaterialTheme.typography.bodySmall.copy(brush = titleBrush)
                            )
                        } else {
                            Text(title, color = Color.White, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            artist,
                            color = Color.White.copy(alpha = 0.65f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit
) {
    var red by remember { mutableStateOf(initialColor.red) }
    var green by remember { mutableStateOf(initialColor.green) }
    var blue by remember { mutableStateOf(initialColor.blue) }
    val previewColor = Color(red, green, blue)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cor personalizada") },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(previewColor)
                )
                Spacer(Modifier.height(16.dp))

                Text("Vermelho", style = MaterialTheme.typography.labelMedium)
                Slider(value = red, onValueChange = { red = it }, valueRange = 0f..1f)

                Text("Verde", style = MaterialTheme.typography.labelMedium)
                Slider(value = green, onValueChange = { green = it }, valueRange = 0f..1f)

                Text("Azul", style = MaterialTheme.typography.labelMedium)
                Slider(value = blue, onValueChange = { blue = it }, valueRange = 0f..1f)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(previewColor) }) { Text("Aplicar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

/**
 * Clareia cores extraídas escuras demais, senão ícones/texto que dependem
 * dela (que ficam brancos por cima, via [com.harmonic.player.ui.theme.withSingleAccent])
 * perdem contraste — mesmo ajuste já usado na extração de cor da capa do
 * álbum na tela Agora Tocando.
 */
private fun adjustAccentForReadability(color: Color): Color =
    if (color.luminance() < 0.35f) lerp(color, Color.White, 0.35f) else color

/**
 * Aplica [adjustAccentForReadability] nas duas pontas de um gradiente, mas
 * garante que elas continuem visivelmente diferentes uma da outra depois do
 * ajuste. Sem isso, um início escuro (clareado 35% em direção ao branco) e um
 * fim já claro podiam convergir pra tons muito próximos — o gradiente
 * "sumia", parecendo quase uma cor só em vez de um degradê.
 */
private fun adjustGradientPairForReadability(start: Color, end: Color): Pair<Color, Color> {
    val adjustedStart = adjustAccentForReadability(start)
    val adjustedEnd = adjustAccentForReadability(end)
    val contrast = kotlin.math.abs(adjustedStart.luminance() - adjustedEnd.luminance())
    if (contrast >= 0.22f) return adjustedStart to adjustedEnd
    // Pouca diferença de luminância: afasta as pontas uma da outra (a mais
    // escura vai mais pro escuro, a mais clara vai mais pro claro) até
    // ficarem claramente distinguíveis como gradiente.
    return if (adjustedStart.luminance() <= adjustedEnd.luminance()) {
        lerp(adjustedStart, Color.Black, 0.3f) to lerp(adjustedEnd, Color.White, 0.3f)
    } else {
        lerp(adjustedStart, Color.White, 0.3f) to lerp(adjustedEnd, Color.Black, 0.3f)
    }
}

/**
 * Extrai DUAS cores (pra combinar com [SettingsRepository.setTitleGradientColors])
 * a partir de um bitmap — mesma ideia da extração da cor de destaque única
 * ([extractAccentFromBitmap]), só que pegando dois tons vibrantes
 * diferentes pra formar um gradiente que combine com o fundo escolhido.
 */
private fun extractTitleGradientFromBitmap(bitmap: Bitmap): Pair<Color, Color>? = try {
    val palette = androidx.palette.graphics.Palette.from(bitmap).generate()
    val start = palette.vibrantSwatch ?: palette.lightVibrantSwatch
        ?: palette.dominantSwatch ?: palette.mutedSwatch
    val end = palette.lightVibrantSwatch?.takeIf { it.rgb != start?.rgb }
        ?: palette.darkVibrantSwatch?.takeIf { it.rgb != start?.rgb }
        ?: palette.mutedSwatch?.takeIf { it.rgb != start?.rgb }
        ?: start
    if (start == null || end == null) null
    else adjustGradientPairForReadability(Color(start.rgb), Color(end.rgb))
} catch (e: Exception) {
    null
}

/** Título em gradiente pra uma imagem de assets/ (temas prontos). */
private suspend fun titleGradientFromAsset(context: android.content.Context, assetPath: String): Pair<Color, Color>? =
    withContext(Dispatchers.IO) {
        try {
            context.assets.open(assetPath).use { input ->
                BitmapFactory.decodeStream(input)?.let { extractTitleGradientFromBitmap(it) }
            }
        } catch (e: Exception) {
            null
        }
    }

/** Título em gradiente pra uma imagem já salva no armazenamento interno (foto da galeria). */
private suspend fun titleGradientFromUri(context: android.content.Context, uriString: String): Pair<Color, Color>? =
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use { input ->
                BitmapFactory.decodeStream(input)?.let { extractTitleGradientFromBitmap(it) }
            }
        } catch (e: Exception) {
            null
        }
    }

/** Título em gradiente pra um tema de gradiente pronto — usa as pontas dele. */
private fun titleGradientFromGradientTheme(theme: com.harmonic.player.data.GradientTheme): Pair<Color, Color> =
    adjustGradientPairForReadability(Color(theme.colorsArgb.first()), Color(theme.colorsArgb.last()))

/**
 * Extrai uma cor de destaque "vibrante" de um bitmap com a Palette API —
 * mesma técnica usada pra combinar a cor com a capa do álbum tocando.
 */
private fun extractAccentFromBitmap(bitmap: Bitmap): Color? = try {
    val palette = androidx.palette.graphics.Palette.from(bitmap).generate()
    val swatch = palette.vibrantSwatch ?: palette.lightVibrantSwatch
        ?: palette.dominantSwatch ?: palette.mutedSwatch
    swatch?.let { adjustAccentForReadability(Color(it.rgb)) }
} catch (e: Exception) {
    null
}

/** Extrai a cor de destaque de uma imagem de tema embutida em assets/. */
private suspend fun accentFromAsset(context: android.content.Context, assetPath: String): Color? =
    withContext(Dispatchers.IO) {
        try {
            context.assets.open(assetPath).use { input ->
                BitmapFactory.decodeStream(input)?.let { extractAccentFromBitmap(it) }
            }
        } catch (e: Exception) {
            null
        }
    }

/** Extrai a cor de destaque de uma imagem já salva no armazenamento interno (foto da galeria). */
private suspend fun accentFromUri(context: android.content.Context, uriString: String): Color? =
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use { input ->
                BitmapFactory.decodeStream(input)?.let { extractAccentFromBitmap(it) }
            }
        } catch (e: Exception) {
            null
        }
    }

/**
 * Pra temas de gradiente (sem nenhuma imagem por trás), não há bitmap pra
 * rodar a Palette API — em vez disso, escolhe a cor mais saturada/viva
 * dentre as paradas do gradiente, que costuma ser a que melhor representa
 * o "clima" do tema como cor de destaque.
 */
private fun accentFromGradient(theme: com.harmonic.player.data.GradientTheme): Color {
    val hsv = FloatArray(3)
    // Antes só olhava a SATURAÇÃO (hsv[1]) pra escolher a cor "mais viva"
    // do gradiente — mas saturação sozinha não basta: um azul-marinho quase
    // preto (ex: #020024) tem saturação MÁXIMA no HSV mesmo parecendo preto
    // a olho nu, porque saturação é independente do quão escura a cor é.
    // Isso fazia temas como "Meia-noite", "Oceano" e "Rosé" escolherem o
    // tom escuro-demais como destaque em vez do tom vivo de verdade — daí
    // o ajuste de legibilidade (que clareia cores escuras) resultava numa
    // cor "suja"/opaca (cinza-arroxeado) em vez de um azul ou rosa bonito.
    // Multiplicando saturação pelo brilho (hsv[2]), uma cor escura demais
    // nunca ganha da vibrante de verdade.
    val mostVivid = theme.colorsArgb.maxByOrNull { argb ->
        android.graphics.Color.colorToHSV(argb.toInt(), hsv)
        hsv[1] * hsv[2] // saturação × brilho
    } ?: theme.colorsArgb.first()
    return adjustAccentForReadability(Color(mostVivid))
}

/**
 * Copia a imagem escolhida na galeria pra dentro do armazenamento do
 * próprio app. Isso evita depender da permissão da URI original, que pode
 * expirar ou não sobreviver a um reinício do aparelho — copiando, o fundo
 * escolhido continua funcionando pra sempre, exatamente como qualquer outra
 * configuração salva.
 */
private suspend fun copyImageToInternalStorage(context: android.content.Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        try {
            // Nome de arquivo ÚNICO a cada foto escolhida (em vez de sempre
            // "custom_background.jpg") — com um nome fixo, trocar de uma
            // foto customizada pra OUTRA foto customizada não mudava nem o
            // valor salvo (mesma string de caminho) nem a chave de cache do
            // Coil, então a tela continuava mostrando a imagem antiga até
            // reabrir o app (quando o processo recarregava e perdia o
            // cache em memória). Só não atualizava na hora quando o antes
            // era um tema pronto, porque aí o valor salvo genuinely mudava
            // de "nenhum" pra um caminho novo.
            val destFile = File(context.filesDir, "custom_background_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            // Limpa fotos customizadas antigas pra não acumular arquivo
            // órfão a cada troca.
            context.filesDir.listFiles { f -> f.name.startsWith("custom_background_") && f.name != destFile.name }
                ?.forEach { it.delete() }
            destFile.toURI().toString()
        } catch (e: Exception) {
            null
        }
    }
