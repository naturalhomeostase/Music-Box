package com.harmonic.player.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Representa uma música indexada a partir do MediaStore do Android.
 * `mediaStoreId` é o id original do MediaStore, usado para detectar
 * duplicatas e músicas removidas do aparelho durante o re-scan.
 *
 * O índice único em `mediaStoreId` é essencial: sem ele, o Room não tem
 * como saber que uma música "nova" encontrada num re-scan já existe na
 * tabela (o `id` interno é autogerado e sempre seria diferente), e cada
 * reinício do app duplicava todas as músicas na biblioteca.
 */
@Entity(tableName = "songs", indices = [Index(value = ["mediaStoreId"], unique = true)])
data class Song(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaStoreId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val genre: String?,
    val year: Int?,
    val composer: String?,
    val trackNumber: Int?,
    val durationMs: Long,
    val sizeBytes: Long,
    val path: String,
    val folder: String,
    val bitrate: Int?,
    val sampleRate: Int?,
    val format: String,
    val dateAdded: Long,
    val dateModified: Long,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null,
    val playbackPositionMs: Long = 0,
    // --- adicionados na fase 3 ---
    val isHidden: Boolean = false,
    /** Uri de uma imagem escolhida pelo usuário pra essa música (sobrepõe a capa embutida no arquivo). */
    val customCoverUri: String? = null,
    /** "Corte": pontos de início/fim usados na reprodução (0 = usa a música inteira). Não recodifica o arquivo. */
    val trimStartMs: Long = 0,
    val trimEndMs: Long = 0
)
