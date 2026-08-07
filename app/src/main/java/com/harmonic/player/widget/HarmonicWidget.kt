package com.harmonic.player.widget

import android.content.ComponentName
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
import androidx.glance.layout.defaultWeight
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
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.harmonic.player.MainActivity
import com.harmonic.player.R
import com.harmonic.player.playback.PlaybackService
import com.harmonic.player.playback.PlaybackServiceHolder
import com.harmonic.player.playback.WidgetPlaybackState
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Três tamanhos de widget (pequeno/médio/grande), pra aparecerem como
 * opções separadas no seletor de widgets do sistema — em vez de um só
 * widget genérico que a pessoa tem que redimensionar na mão e torcer pra
 * ficar bom. Os três compartilham a mesma lógica de estado/desenho
 * ([WidgetChrome]); só o tamanho e a quantidade de informação mudam.
 *
 * Fundo: no widget pequeno, a capa da música preenche o espaço inteiro
 * (funciona bem nesse tamanho mínimo, sem espaço pra texto de qualquer
 * jeito); sem capa, cai num fundo simples na cor de destaque do app. Médio e
 * grande usam um cartão escuro sólido, sempre consistente, com a capa
 * aparecendo como uma miniatura de verdade ao lado do texto — em vez de um
 * recorte de fundo esticado, que ficava bem inconsistente dependendo da
 * proporção da capa.
 *
 * Médio e grande são bem mais HORIZONTAIS (barra curta e cartão largo,
 * respectivamente) em vez de quase quadrados.
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
    val gray = ColorProvider(Color(0xFFB8B4BE))
    val accent = ColorProvider(Color(0xFFE0A030))
    // Fundo em cartão sólido, sempre igual não importa a capa da música —
    // antes o fundo era a própria capa esticada preenchendo o widget
    // inteiro, o que ficava ótimo com algumas capas e uma bagunça
    // borrada/cortada estranho com outras (capas quadradas pequenas
    // esticadas num widget bem retangular, por exemplo). Um cartão escuro
    // consistente, com a capa aparecendo como uma miniatura de verdade (ver
    // [AlbumThumb]), fica previsível e "intencional" em qualquer capa.
    val cardBg = ColorProvider(Color(0xFF19171D))

    Box(modifier = GlanceModifier.fillMaxSize().cornerRadius(24.dp)) {
        when (size) {
            // Pequeno continua sendo a capa preenchendo tudo, com o botão
            // de play/pause flutuando por cima — nesse tamanho mínimo (1
            // célula) isso funciona bem, é basicamente "a capa do álbum
            // com um botão", sem espaço sobrando pra texto de qualquer jeito.
            WidgetSize.SMALL -> {
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
                Box(modifier = GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg_gradient))) {}
                SmallContent(state, white)
            }
            WidgetSize.MEDIUM -> {
                Box(modifier = GlanceModifier.fillMaxSize().background(cardBg)) {}
                MediumContent(state, white, gray, accent)
            }
            WidgetSize.LARGE -> {
                Box(modifier = GlanceModifier.fillMaxSize().background(cardBg)) {}
                LargeContent(state, white, gray, accent)
            }
        }
    }
}

/**
 * Miniatura quadrada de verdade da capa (não mais um recorte esticado de
 * fundo) — cantos arredondados proporcionais ao tamanho, e quando não há
 * capa (música sem imagem embutida, ou nada tocando ainda), mostra a nota
 * musical em vez de deixar um quadrado vazio/cinza sem explicação.
 */
@Composable
private fun AlbumThumb(state: WidgetPlaybackState, accent: ColorProvider, size: Dp) {
    Box(
        modifier = GlanceModifier
            .size(size)
            .cornerRadius(size / 4)
            .background(accent),
        contentAlignment = Alignment.Center
    ) {
        if (state.coverBitmap != null) {
            Image(
                provider = ImageProvider(state.coverBitmap),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(size / 4)
            )
        } else {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_note_placeholder),
                contentDescription = null,
                modifier = GlanceModifier.size(size / 2)
            )
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
 * Barra horizontal curta: miniatura da capa, título/artista ao lado,
 * controles compactos na ponta — tudo numa linha só, cabendo no formato
 * mais baixo (4x1) que o widget médio ganhou.
 */
@Composable
private fun MediumContent(state: WidgetPlaybackState, white: ColorProvider, gray: ColorProvider, accent: ColorProvider) {
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumThumb(state, accent, 40.dp)
        Spacer(modifier = GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = state.title ?: "Harmonic",
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
        Spacer(modifier = GlanceModifier.width(8.dp))
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

/**
 * Cartão horizontal maior: capa grande à esquerda, título/artista e
 * controles empilhados à direita — layout em "L" bem mais parecido com um
 * cartão de álbum de verdade do que texto+botões soltos sobre um recorte de
 * fundo.
 */
@Composable
private fun LargeContent(state: WidgetPlaybackState, white: ColorProvider, gray: ColorProvider, accent: ColorProvider) {
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumThumb(state, accent, 88.dp)
        Spacer(modifier = GlanceModifier.width(16.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = state.title ?: "Harmonic",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, color = white),
                maxLines = 1,
                modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>())
            )
            Text(
                text = state.artist ?: if (state.hasQueue) "" else "Nenhuma música tocando",
                style = TextStyle(fontSize = 13.sp, color = gray),
                maxLines = 1,
                modifier = GlanceModifier.fillMaxWidth()
            )
            Spacer(modifier = GlanceModifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                WidgetIconButton(R.drawable.ic_widget_previous, 40.dp, 18.dp, R.drawable.widget_button_secondary_selector, actionRunCallback<PreviousAction>())
                Spacer(modifier = GlanceModifier.width(14.dp))
                WidgetIconButton(
                    if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                    52.dp, 22.dp, R.drawable.widget_button_primary_selector, actionRunCallback<PlayPauseAction>()
                )
                Spacer(modifier = GlanceModifier.width(14.dp))
                WidgetIconButton(R.drawable.ic_widget_next, 40.dp, 18.dp, R.drawable.widget_button_secondary_selector, actionRunCallback<NextAction>())
            }
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

/**
 * Conecta um [MediaController] de verdade à sessão do [PlaybackService] pra
 * executar o comando — em vez de mexer direto no [PlaybackServiceHolder]
 * (um atalho em memória que só existe enquanto o processo do app está de
 * pé). Esse era o motivo dos botões do widget "morrerem": assim que o
 * Android encerra o processo em segundo plano (comum com a música pausada
 * por um tempo, ou depois que o app sai da lista de recentes), aquele
 * atalho ficava nulo e os toques não faziam nada.
 *
 * Conectar via [SessionToken] + [MediaController] é o mesmo caminho que o
 * próprio app usa (ver [com.harmonic.player.playback.PlayerController]) —
 * o Android sabe iniciar/religar o serviço sozinho quando um controller
 * pede pra se conectar a ele, mesmo com o processo anterior já morto.
 */
private suspend fun withMediaController(context: Context, action: (MediaController) -> Unit) {
    val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
    val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
    try {
        val controller = suspendCancellableCoroutine<MediaController> { cont ->
            controllerFuture.addListener({
                if (cont.isActive) {
                    runCatching { controllerFuture.get() }
                        .onSuccess { cont.resume(it) }
                        .onFailure { cont.cancel(it) }
                }
            }, MoreExecutors.directExecutor())
        }
        action(controller)
        // Empurra uma atualização otimista na hora — se o serviço já
        // estava vivo (app tocando em segundo plano, por exemplo), o
        // PlaybackServiceHolder já reflete o novo estado nesse instante e
        // não precisamos esperar o listener do player disparar sozinho.
        // Se o serviço acabou de "acordar" agora, essa chamada aqui pode
        // não pegar o estado mais novo ainda — sem problema, porque o
        // próprio PlaybackService (em onCreate/nos listeners do player) já
        // se encarrega de atualizar os widgets assim que a fila salva for
        // restaurada e a faixa carregar.
        PlaybackServiceHolder.refreshState()
        updateAllHarmonicWidgets(context)
    } finally {
        MediaController.releaseFuture(controllerFuture)
    }
}

class PlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withMediaController(context) { controller ->
            if (controller.isPlaying) controller.pause() else controller.play()
        }
    }
}

class NextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withMediaController(context) { controller -> controller.seekToNextMediaItem() }
    }
}

class PreviousAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withMediaController(context) { controller -> controller.seekToPreviousMediaItem() }
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
