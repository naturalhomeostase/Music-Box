package com.harmonic.player.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** Largura padrão de todos os menus do app — só o "Ordenar por" usa uma menor (ver [SortMenuButton]). */
val DefaultMenuWidth = 240.dp

/**
 * Versão "casca" do [DropdownMenu] padrão do Material3, só que pintada com
 * a cor de destaque do tema em vez do cinza neutro padrão — inspirado na
 * referência visual enviada (cartão sólido, cantos arredondados, itens em
 * lista com ícone + texto claro por cima).
 *
 * Reaproveita o [DropdownMenu]/[androidx.compose.material3.DropdownMenuItem]
 * de sempre por dentro (então todo o comportamento de posicionamento,
 * dismiss ao tocar fora, etc. continua igual) — a mudança é só que a gente
 * sobrescreve localmente as cores de superfície do MaterialTheme antes de
 * montar o menu, já que é dali que o Material3 tira a cor de fundo e do
 * texto/ícone dos itens. Como a cor de destaque muda com o tema escolhido
 * em Aparência, este menu acompanha automaticamente sem precisar de nenhum
 * ajuste manual em cada tela que o usa.
 *
 * A cor do texto/ícone (`onSurface`) não é mais fixa em branco — é
 * calculada pela luminância da cor de destaque, igual ao esquema já usado
 * na tela "Tocando agora" pra letra da música: texto escuro em cima de
 * destaques claros (amarelo, rosa clarinho...), branco em cima de
 * destaques escuros. Sem isso, alguns temas de cor deixavam o texto do
 * menu quase ilegível.
 */
@Composable
fun ThemedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    widthDp: Dp = DefaultMenuWidth,
    content: @Composable ColumnScope.() -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = if (accent.luminance() > 0.6f) Color(0xFF1A1A1A) else Color.White
    val themedScheme = MaterialTheme.colorScheme.copy(
        surface = accent,
        surfaceContainer = accent,
        surfaceContainerHigh = accent,
        surfaceContainerHighest = accent,
        surfaceContainerLow = accent,
        surfaceContainerLowest = accent,
        surfaceVariant = accent,
        onSurface = onAccent,
        onSurfaceVariant = onAccent.copy(alpha = 0.8f),
        outlineVariant = onAccent.copy(alpha = 0.18f)
    )

    // CORREÇÃO: na versão do Material3 usada neste projeto, `DropdownMenu`
    // não tem parâmetro `shape` (isso só foi adicionado em versões mais
    // novas da lib — testei contra a errada da primeira vez, causando erro
    // de compilação). A Surface interna do menu usa `MaterialTheme.shapes.
    // extraSmall` pra desenhar o fundo, então a forma de sobrescrever isso
    // sem esse parâmetro é sobrescrever esse shape no MaterialTheme local
    // (do mesmo jeito que já sobrescrevemos as cores logo abaixo) — assim o
    // fundo interno fica com a MESMA cantoneira do `.clip()`/`.border()`
    // externo, em vez de ficar quase quadrado por trás dela.
    val menuShape = RoundedCornerShape(26.dp)
    val themedShapes = MaterialTheme.shapes.copy(extraSmall = menuShape)

    MaterialTheme(colorScheme = themedScheme, shapes = themedShapes) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            offset = offset,
            modifier = Modifier
                .width(widthDp)
                .clip(menuShape)
                .border(1.dp, onAccent.copy(alpha = 0.16f), menuShape),
            content = content
        )
    }
}
