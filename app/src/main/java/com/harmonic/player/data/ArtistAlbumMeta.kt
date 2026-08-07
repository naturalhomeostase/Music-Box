package com.harmonic.player.data

import androidx.room.Entity

/**
 * Artistas e álbuns não têm tabela própria — são derivados agrupando a
 * tabela `songs`. Pra guardar "favoritar"/"renomear" sem precisar de uma
 * tabela cheia de músicas duplicadas, usamos essas tabelas pequenas de
 * metadados, uma linha por artista/álbum.
 */
@Entity(tableName = "artist_meta", primaryKeys = ["name"])
data class ArtistMeta(
    val name: String,
    val isFavorite: Boolean = false
)

@Entity(tableName = "album_meta", primaryKeys = ["albumId"])
data class AlbumMeta(
    val albumId: Long,
    val isFavorite: Boolean = false
)
