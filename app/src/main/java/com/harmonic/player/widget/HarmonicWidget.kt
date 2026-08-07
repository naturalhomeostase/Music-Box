package com.harmonic.player.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.action.ActionParameters
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.harmonic.player.MainActivity
import com.harmonic.player.R
import com.harmonic.player.playback.PlaybackServiceHolder
import com.harmonic.player.playback.WidgetPlaybackState

/**
 * Três tamanhos de widget (pequeno/médio/grande), pra aparecerem como
 * opções separadas no seletor de widgets do sistema — em vez de um só
 * widget genérico que a pessoa tem que redimensionar na mão e torcer pra
 * ficar bom. Os três compartilham a mesma lógica de estado/desenho
 * ([WidgetChrome]); só o tamanho e a quantidade de informação mudam.
 *
 * Fundo: a capa da música (quando disponível) preenche o widget inteiro,
 * com um degradê escuro por cima só o suficiente pra manter texto/botões
 * legíveis — sem capa (nada tocando, ou música sem capa), cai num fundo
 * simples na cor de destaque do app, nunca uma caixa cinza vazia.
 *
 * Médio e grande agora são bem mais HORIZONTAIS (barra curta e cartão
 * largo, respectivamente) em vez de quase quadrados — o conteúdo dentro
 * deles também virou uma Row (texto de um lado, controles do outro) pra
 * caber direito nesse formato mais baixo.
 *
 * Os botões deixaram de simular um relevo 3D (gradiente + brilho + sombra
 * falsos, um estilo datado) e agora são círculos chapados — translúcidos
 * pros secundários (anterior/próxima), sólidos na cor de destaque pro
 * principal (play/pause) — com ícones vetoriais de verdade no lugar de
 * emoji (que renderizam diferente e meio "amador" dependendo do teclado/SO
 * do aparelho).
 *
 * Consumo de bateria: os widgets só recompõem quando o estado observado
 * muda de verdade (comportamento padrão do Glance) — não há nenhum
 * polling/timer aqui. A capa é carregada em tamanho reduzido (300px) e
 * cacheada pelo [com.harmonic.player.data.AlbumArtLoader], então trocar de
 * música numa mesma sessão não implica recarregar/redecodificar do zero.
 */
private enum class WidgetSize { SMALL, MEDIUM, LARGE }

class HarmonicWidgetSmall : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetChrome(WidgetSize.SMALL) }
    }
}

class HarmonicWidgetMedium : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetChrome(WidgetSize.MEDIUM) }
    }
}

class HarmonicWidgetLarge : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetChrome(WidgetSize.LARGE) }
    }
}

@Composable
private fun WidgetChrome(size: WidgetSize) {
    val state = PlaybackServiceHolder.state.value
    val white = ColorProvider(Color.White)
    val gray = ColorProvider(Color(0xFFE0E0E0))
    val accent = ColorProvider(Color(0xFFE0A030))

    Box(modifier = GlanceModifier.fillMaxSize().cornerRadius(24.dp)) {
        // Camada 1: capa (ou um fundo sólido na cor de destaque quando não há).
        if (state.coverBitmap != null) {
            Image(
                provider = ImageProvider(state.coverBitmap),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize()
            )
        } else {
            Box(modifier = GlanceModifier.fillMaxSize().background(accent)) {}
        }
        // Camada 2: degradê escuro por cima, só pra dar contraste ao texto/botões.
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_bg_gradient))
        ) {}
        // Camada 3: conteúdo de verdade.
        when (size) {
            WidgetSize.SMALL -> SmallContent(state, white)
            WidgetSize.MEDIUM -> MediumContent(state, white, gray)
            WidgetSize.LARGE -> LargeContent(state, white, gray)
        }
    }
}

/** Só a capa + play/pause no centro — pro espaço mínimo (ex: 1 célula). Tocar fora do botão abre o app. */
@Composable
private fun SmallContent(state: WidgetPlaybackState, white: ColorProvider) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center
    ) {
        WidgetIconButton(
            icon = if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            size = 46.dp,
            iconSize = 20.dp,
            background = R.drawable.widget_button_primary_selector,
            onClick = actionRunCallback<PlayPauseAction>()
        )
    }
}

/**
 * Barra horizontal curta: título/artista de um lado, controles compactos do
 * outro, tudo numa linha só — cabe no formato mais baixo (4x1) que o widget
 * médio ganhou. Antes era uma Column empilhada (texto em cima, botões
 * embaixo) pensada pro tamanho antigo, quase quadrado.
 */
@Composable
private fun MediumContent(state: WidgetPlaybackState, white: ColorProvider, gray: ColorProvider) {
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = state.title ?: "Music Box",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = white),
                maxLines = 1,
                modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>())
            )
            Text(
                text = state.artist ?: if (state.hasQueue) "" else "Nenhuma música tocando",
                style = TextStyle(fontSize = 12.sp, color = gray),
                maxLines = 1,
                modifier = GlanceModifier.fillMaxWidth()
            )
        }
        Spacer(modifier = GlanceModifier.width(10.dp))
        WidgetIconButton(R.drawable.ic_widget_previous, 32.dp, 15.dp, R.drawable.widget_button_secondary_selector, actionRunCallback<PreviousAction>())
        Spacer(modifier = GlanceModifier.width(8.dp))
        WidgetIconButton(
            if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            40.dp, 18.dp, R.drawable.widget_button_primary_selector, actionRunCallback<PlayPauseAction>()
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        WidgetIconButton(R.drawable.ic_widget_next, 32.dp, 15.dp, R.drawable.widget_button_secondary_selector, actionRunCallback<NextAction>())
    }
}

/** Cartão horizontal maior: mesma ideia do médio, com mais espaço pra capa/texto respirarem e botões maiores. */
@Composable
private fun LargeContent(state: WidgetPlaybackState, white: ColorProvider, gray: ColorProvider) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = state.title ?: "Music Box",
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = white),
            maxLines = 1,
            modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>())
        )
        Text(
            text = state.artist ?: if (state.hasQueue) "" else "Nenhuma música tocando",
            style = TextStyle(fontSize = 14.sp, color = gray),
            maxLines = 1,
            modifier = GlanceModifier.fillMaxWidth()
        )
        Spacer(modifier = GlanceModifier.height(10.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            WidgetIconButton(R.drawable.ic_widget_previous, 44.dp, 20.dp, R.drawable.widget_button_secondary_selector, actionRunCallback<PreviousAction>())
            Spacer(modifier = GlanceModifier.width(18.dp))
            WidgetIconButton(
                if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                56.dp, 24.dp, R.drawable.widget_button_primary_selector, actionRunCallback<PlayPauseAction>()
            )
            Spacer(modifier = GlanceModifier.width(18.dp))
            WidgetIconButton(R.drawable.ic_widget_next, 44.dp, 20.dp, R.drawable.widget_button_secondary_selector, actionRunCallback<NextAction>())
        }
    }
}

/**
 * Botão circular chapado (sem bisel/brilho falso simulando 3D) com um
 * ícone vetorial de verdade centralizado — usado tanto pros botões
 * secundários (translúcidos) quanto pro principal (cor de destaque sólida),
 * só trocando o drawable de fundo e o tamanho.
 */
@Composable
private fun WidgetIconButton(
    icon: Int,
    size: Dp,
    iconSize: Dp,
    background: Int,
    onClick: Action
) {
    Box(
        modifier = GlanceModifier
            .size(size)
            .background(ImageProvider(background))
            .clickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = null,
            modifier = GlanceModifier.size(iconSize)
        )
    }
}

class PlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        PlaybackServiceHolder.togglePlayPause()
        PlaybackServiceHolder.refreshState()
        updateAllHarmonicWidgets(context)
    }
}

class NextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        PlaybackServiceHolder.skipNext()
        PlaybackServiceHolder.refreshState()
        updateAllHarmonicWidgets(context)
    }
}

class PreviousAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        PlaybackServiceHolder.skipPrevious()
        PlaybackServiceHolder.refreshState()
        updateAllHarmonicWidgets(context)
    }
}

private suspend fun updateAllHarmonicWidgets(context: Context) {
    HarmonicWidgetSmall().updateAll(context)
    HarmonicWidgetMedium().updateAll(context)
    HarmonicWidgetLarge().updateAll(context)
}

/** Um receiver por tamanho — é o que faz cada um aparecer como opção separada no seletor de widgets do sistema. */
class HarmonicWidgetSmallReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HarmonicWidgetSmall()
}

class HarmonicWidgetMediumReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HarmonicWidgetMedium()
}

class HarmonicWidgetLargeReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HarmonicWidgetLarge()
}
