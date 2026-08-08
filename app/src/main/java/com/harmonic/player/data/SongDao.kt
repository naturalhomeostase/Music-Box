package com.harmonic.player.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    // ---------- Biblioteca ----------

    @Query("SELECT * FROM songs")
    suspend fun getAllSongsOnce(): List<Song>

    @Query("SELECT * FROM songs WHERE folder NOT IN (SELECT path FROM hidden_folders) AND isHidden = 0 ORDER BY title COLLATE NOCASE ASC")
    fun getAllSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE folder NOT IN (SELECT path FROM hidden_folders) AND isHidden = 0 AND " +
           "(title LIKE '%' || :query || '%' " +
           "OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%')")
    fun search(query: String): Flow<List<Song>>

    // COLLATE NOCASE no agrupamento (não só na ordenação) — sem isso,
    // "Metallica" e "metallica" (tags inconsistentes entre arquivos do
    // mesmo artista, bem comum em bibliotecas reais) contavam como dois
    // artistas diferentes e apareciam duas vezes na lista.
    @Query("SELECT artist FROM songs WHERE folder NOT IN (SELECT path FROM hidden_folders) AND isHidden = 0 GROUP BY artist COLLATE NOCASE ORDER BY artist COLLATE NOCASE ASC")
    fun getArtists(): Flow<List<String>>

    @Query("""
        SELECT artist AS name, COUNT(*) AS songCount, COUNT(DISTINCT albumId) AS albumCount, SUM(playCount) AS playCount
        FROM songs
        WHERE folder NOT IN (SELECT path FROM hidden_folders) AND isHidden = 0
        GROUP BY artist COLLATE NOCASE
        ORDER BY artist COLLATE NOCASE ASC
    """)
    fun getArtistSummaries(): Flow<List<ArtistSummary>>

    @Query("SELECT * FROM songs WHERE artist = :artist COLLATE NOCASE AND isHidden = 0 LIMIT 1")
    suspend fun getFirstSongForArtist(artist: String): Song?

    // COLLATE NOCASE aqui também — sem isso, um artista com tags em
    // capitalização diferente entre arquivos (comum em bibliotecas reais)
    // aparecia certo na lista (já agrupado por getArtistSummaries), mas ao
    // abrir a página dele só metade das músicas apareciam.
    @Query("SELECT * FROM songs WHERE artist = :artist COLLATE NOCASE AND folder NOT IN (SELECT path FROM hidden_folders) AND isHidden = 0 ORDER BY album, trackNumber")
    fun getSongsByArtist(artist: String): Flow<List<Song>>

    @Query("""
        SELECT album, albumId, artist, COUNT(*) AS trackCount, SUM(playCount) AS playCount, MIN(id) AS representativeSongId, MAX(year) AS year
        FROM songs
        WHERE folder NOT IN (SELECT path FROM hidden_folders) AND isHidden = 0
        GROUP BY albumId
        ORDER BY album COLLATE NOCASE ASC
    """)
    fun getAlbums(): Flow<List<AlbumSummary>>

    @Query("SELECT * FROM songs WHERE albumId = :albumId AND folder NOT IN (SELECT path FROM hidden_folders) AND isHidden = 0 ORDER BY trackNumber")
    fun getSongsByAlbum(albumId: Long): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE albumId = :albumId AND isHidden = 0 LIMIT 1")
    suspend fun getFirstSongForAlbum(albumId: Long): Song?

    @Query("SELECT DISTINCT genre FROM songs WHERE genre IS NOT NULL AND folder NOT IN (SELECT path FROM hidden_folders) AND isHidden = 0 ORDER BY genre ASC")
    fun getGenres(): Flow<List<String>>

    @Query("SELECT * FROM songs WHERE genre = :genre AND folder NOT IN (SELECT path FROM hidden_folders) AND isHidden = 0 ORDER BY title")
    fun getSongsByGenre(genre: String): Flow<List<Song>>

    @Query("SELECT DISTINCT folder FROM songs WHERE folder NOT IN (SELECT path FROM hidden_folders) AND isHidden = 0 ORDER BY folder ASC")
    fun getFolders(): Flow<List<String>>

    /** Todas as pastas, mesmo as escondidas — usada só na tela "Pastas ocultas". */
    @Query("SELECT DISTINCT folder FROM songs ORDER BY folder ASC")
    fun getAllFoldersIncludingHidden(): Flow<List<String>>

    @Query("SELECT * FROM songs WHERE folder = :folder AND isHidden = 0 ORDER BY title")
    fun getSongsByFolder(folder: String): Flow<List<Song>>

    // ---------- Pastas ocultas ----------

    @Query("SELECT path FROM hidden_folders")
    fun getHiddenFolders(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hideFolder(folder: HiddenFolder)

    @Query("DELETE FROM hidden_folders WHERE path = :path")
    suspend fun unhideFolder(path: String)

    // ---------- Edição de música (menu de opções) ----------

    @Query("SELECT * FROM songs WHERE id = :songId")
    suspend fun getSongById(songId: Long): Song?

    @Query("UPDATE songs SET isHidden = :hidden WHERE id = :songId")
    suspend fun setSongHidden(songId: Long, hidden: Boolean)

    @Query("UPDATE songs SET isHidden = 1 WHERE albumId = :albumId")
    suspend fun hideSongsByAlbum(albumId: Long)

    @Query("SELECT * FROM songs WHERE isHidden = 1 ORDER BY title COLLATE NOCASE ASC")
    fun getHiddenSongs(): Flow<List<Song>>

    @Query("UPDATE songs SET title = :title WHERE id = :songId")
    suspend fun renameSong(songId: Long, title: String)

    @Query("UPDATE songs SET path = :path WHERE id = :songId")
    suspend fun updateSongPath(songId: Long, path: String)

    @Query("""
        UPDATE songs SET title = :title, artist = :artist, album = :album, genre = :genre, trackNumber = :trackNumber, year = :year, composer = :composer
        WHERE id = :songId
    """)
    suspend fun updateSongMetadata(songId: Long, title: String, artist: String, album: String, genre: String?, trackNumber: Int?, year: Int?, composer: String?)

    @Query("UPDATE songs SET customCoverUri = :uri WHERE id = :songId")
    suspend fun setCustomCover(songId: Long, uri: String?)

    @Query("UPDATE songs SET trimStartMs = :startMs, trimEndMs = :endMs WHERE id = :songId")
    suspend fun setTrimPoints(songId: Long, startMs: Long, endMs: Long)

    @Query("DELETE FROM songs WHERE id = :songId")
    suspend fun deleteSongById(songId: Long)

    // ---------- Artistas / álbuns (renomear, excluir, favoritar) ----------

    // COLLATE NOCASE — renomear pega todas as variantes de capitalização
    // de uma vez (ex: "Metallica" e "METALLICA" viram uma coisa só), então
    // dá pra consertar um artista duplicado direto por aqui.
    @Query("UPDATE songs SET artist = :newName WHERE artist = :oldName COLLATE NOCASE")
    suspend fun renameArtist(oldName: String, newName: String)

    @Query("DELETE FROM songs WHERE artist = :artist")
    suspend fun deleteSongsByArtist(artist: String)

    @Query("UPDATE songs SET album = :newName WHERE albumId = :albumId")
    suspend fun renameAlbum(albumId: Long, newName: String)

    @Query("DELETE FROM songs WHERE albumId = :albumId")
    suspend fun deleteSongsByAlbum(albumId: Long)

    @Query("DELETE FROM songs WHERE folder = :folder")
    suspend fun deleteSongsByFolder(folder: String)

    @Query("INSERT OR REPLACE INTO artist_meta (name, isFavorite) VALUES (:name, :isFavorite)")
    suspend fun setArtistFavorite(name: String, isFavorite: Boolean)

    @Query("SELECT name FROM artist_meta WHERE isFavorite = 1")
    fun getFavoriteArtistNames(): Flow<List<String>>

    @Query("INSERT OR REPLACE INTO album_meta (albumId, isFavorite) VALUES (:albumId, :isFavorite)")
    suspend fun setAlbumFavorite(albumId: Long, isFavorite: Boolean)

    @Query("SELECT albumId FROM album_meta WHERE isFavorite = 1")
    fun getFavoriteAlbumIds(): Flow<List<Long>>

    // ---------- Favoritos / mais tocadas / recentes ----------

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY title")
    fun getFavorites(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE playCount > 0 ORDER BY playCount DESC LIMIT 100")
    fun getMostPlayed(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT 100")
    fun getRecentlyPlayed(): Flow<List<Song>>

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :songId")
    suspend fun setFavorite(songId: Long, isFavorite: Boolean)

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayedAt = :timestamp WHERE id = :songId")
    suspend fun registerPlay(songId: Long, timestamp: Long = System.currentTimeMillis())

    /** Zera histórico/estatísticas de reprodução de TODAS as músicas — usado no botão "Resetar" da tela de Histórico e estatísticas. Não mexe em favoritos nem em nenhum outro dado. */
    @Query("UPDATE songs SET playCount = 0, lastPlayedAt = NULL")
    suspend fun resetPlayStats()

    @Query("UPDATE songs SET playbackPositionMs = :positionMs WHERE id = :songId")
    suspend fun savePlaybackPosition(songId: Long, positionMs: Long)

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun getSongsByIds(ids: List<Long>): List<Song>

    // ---------- Escaneamento ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<Song>)

    @Query("SELECT mediaStoreId FROM songs")
    suspend fun getAllMediaStoreIds(): List<Long>

    @Query("DELETE FROM songs WHERE mediaStoreId IN (:mediaStoreIds)")
    suspend fun deleteByMediaStoreIds(mediaStoreIds: List<Long>)

    /** Músicas sem ano ainda — candidatas ao fallback de leitura de tag em segundo plano. */
    @Query("SELECT * FROM songs WHERE year IS NULL")
    suspend fun getSongsMissingYear(): List<Song>

    @Query("UPDATE songs SET year = :year WHERE id = :songId")
    suspend fun updateSongYear(songId: Long, year: Int?)

    // ---------- Playlists ----------

    @Insert
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getPlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    suspend fun getPlaylistsOnce(): List<Playlist>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToPlaylist(crossRef: PlaylistSongCrossRef)

    @Query("""
        SELECT songs.* FROM songs
        INNER JOIN playlist_song_cross_ref ON songs.id = playlist_song_cross_ref.songId
        WHERE playlist_song_cross_ref.playlistId = :playlistId
        ORDER BY playlist_song_cross_ref.position ASC
    """)
    fun getPlaylistSongs(playlistId: Long): Flow<List<Song>>

    @Query("""
        SELECT songs.* FROM songs
        INNER JOIN playlist_song_cross_ref ON songs.id = playlist_song_cross_ref.songId
        WHERE playlist_song_cross_ref.playlistId = :playlistId
        ORDER BY playlist_song_cross_ref.position ASC
    """)
    suspend fun getPlaylistSongsOnce(playlistId: Long): List<Song>

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeFromPlaylist(playlistId: Long, songId: Long)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("UPDATE playlists SET isFavorite = :isFavorite WHERE id = :playlistId")
    suspend fun setPlaylistFavorite(playlistId: Long, isFavorite: Boolean)

    @Query("UPDATE playlists SET modifiedAt = :timestamp WHERE id = :playlistId")
    suspend fun touchPlaylist(playlistId: Long, timestamp: Long = System.currentTimeMillis())

    /** Regrava a posição de cada música na playlist, na ordem dada (usado depois de arrastar pra reordenar). */
    @androidx.room.Transaction
    suspend fun updatePlaylistOrder(playlistId: Long, songIdsInOrder: List<Long>) {
        songIdsInOrder.forEachIndexed { index, songId ->
            setPlaylistSongPosition(playlistId, songId, index)
        }
        touchPlaylist(playlistId)
    }

    @Query("UPDATE playlist_song_cross_ref SET position = :position WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun setPlaylistSongPosition(playlistId: Long, songId: Long, position: Int)

    @Query("UPDATE playlists SET name = :name, modifiedAt = :timestamp WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, name: String, timestamp: Long = System.currentTimeMillis())

    // ---------- Marcadores (bookmarks) ----------

    @Insert
    suspend fun insertBookmark(bookmark: Bookmark): Long

    @Query("SELECT * FROM bookmarks WHERE songId = :songId ORDER BY positionMs ASC")
    fun getBookmarksForSong(songId: Long): Flow<List<Bookmark>>

    @Query("DELETE FROM bookmarks WHERE id = :bookmarkId")
    suspend fun deleteBookmark(bookmarkId: Long)
}

data class AlbumSummary(val album: String, val albumId: Long, val artist: String, val trackCount: Int, val playCount: Int, val representativeSongId: Long, val year: Int? = null)

data class ArtistSummary(val name: String, val songCount: Int, val albumCount: Int, val playCount: Int)
