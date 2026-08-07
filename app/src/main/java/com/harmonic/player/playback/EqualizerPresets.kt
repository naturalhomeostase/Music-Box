package com.harmonic.player.playback

/**
 * Presets clássicos de equalizador, definidos como frações de -1f a 1f
 * (não em milibels) — cada aparelho relata um range de banda diferente
 * (`Equalizer.bandLevelRange`), então guardamos a "forma" da curva e
 * escalamos pro range real na hora de aplicar. Também não presumimos
 * exatamente 10 bandas: se o aparelho relatar menos, reamostramos.
 */
data class EqPreset(val name: String, val curve: List<Float>)

val equalizerPresets = listOf(
    EqPreset("Padrão",   List(10) { 0f }),
    EqPreset("Rock",     listOf(0.5f, 0.35f, 0.1f, -0.1f, -0.15f, -0.1f, 0.05f, 0.25f, 0.4f, 0.45f)),
    EqPreset("Pop",      listOf(-0.1f, 0.1f, 0.3f, 0.35f, 0.15f, -0.05f, -0.1f, -0.1f, -0.05f, -0.05f)),
    EqPreset("Jazz",     listOf(0.3f, 0.2f, 0.05f, 0.15f, -0.1f, -0.1f, 0.0f, 0.15f, 0.25f, 0.35f)),
    EqPreset("Clássica", listOf(0.35f, 0.3f, 0.25f, 0.15f, 0.0f, 0.0f, -0.1f, -0.1f, -0.15f, -0.25f)),
    EqPreset("Eletrônica", listOf(0.4f, 0.35f, 0.15f, 0.0f, -0.15f, -0.1f, 0.0f, 0.2f, 0.3f, 0.4f)),
    EqPreset("Hip-Hop",  listOf(0.5f, 0.4f, 0.1f, 0.2f, -0.1f, -0.05f, 0.1f, 0.05f, 0.2f, 0.3f)),
    EqPreset("Reforço de graves", listOf(0.6f, 0.55f, 0.4f, 0.15f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)),
    EqPreset("Reforço de vocais", listOf(-0.15f, -0.1f, -0.05f, 0.15f, 0.35f, 0.35f, 0.2f, 0.05f, -0.05f, -0.1f)),
    EqPreset("Reforço de agudos", listOf(0f, 0f, 0f, 0f, 0f, 0.1f, 0.25f, 0.4f, 0.5f, 0.55f))
)

/**
 * Converte a curva normalizada (-1..1) do preset pros níveis reais (em
 * milibels) daquele aparelho específico, reamostrando se o número de
 * bandas do preset (sempre 10) não bater com o número de bandas reais.
 */
fun EqPreset.toBandLevels(bands: List<EqualizerBandInfo>): List<Int> {
    if (bands.isEmpty()) return emptyList()
    return bands.mapIndexed { index, band ->
        val curveIndex = if (bands.size == 1) 0
        else (index * (curve.size - 1) / (bands.size - 1).coerceAtLeast(1)).coerceIn(0, curve.size - 1)
        val fraction = curve[curveIndex]
        val range = if (fraction >= 0) band.maxLevel else -band.minLevel
        (fraction * range).toInt()
    }
}
