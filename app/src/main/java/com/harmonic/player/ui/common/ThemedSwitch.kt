package com.harmonic.player.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Mesmo [Switch] do Material3, só que com o "polegar" (bolinha redonda)
 * pintado no tom da cor de destaque do tema em vez do branco padrão —
 * assim ele acompanha a cor de destaque escolhida (manual ou automática)
 * como o resto da interface.
 */
@Composable
fun ThemedSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = accent,
            checkedTrackColor = accent.copy(alpha = 0.35f),
            checkedBorderColor = accent
        )
    )
}
