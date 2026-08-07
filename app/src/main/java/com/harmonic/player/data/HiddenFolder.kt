package com.harmonic.player.data

import androidx.room.Entity

/**
 * Pastas que o usuário escolheu esconder da biblioteca (aba Pastas e lista
 * de Músicas). `path` é a mesma string usada em `Song.folder`.
 */
@Entity(tableName = "hidden_folders", primaryKeys = ["path"])
data class HiddenFolder(val path: String)
