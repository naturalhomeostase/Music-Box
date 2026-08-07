package com.harmonic.player.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Song::class, Playlist::class, PlaylistSongCrossRef::class, Bookmark::class, HiddenFolder::class, ArtistMeta::class, AlbumMeta::class],
    version = 5,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao

    companion object {
        @Volatile private var INSTANCE: MusicDatabase? = null

        fun getInstance(context: Context): MusicDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    "harmonic_music.db"
                )
                    // App ainda não foi lançado publicamente — destruir e
                    // recriar o banco numa mudança de schema é seguro (só
                    // reescaneia a biblioteca do zero, não perde nada real).
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
