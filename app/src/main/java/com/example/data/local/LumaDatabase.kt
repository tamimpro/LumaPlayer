package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MountedFolderEntity::class,
        VideoItemEntity::class,
        PlaylistEntity::class,
        PlaylistEntryEntity::class,
        StreamBookmarkEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class LumaDatabase : RoomDatabase() {
    abstract fun lumaDao(): LumaDao

    companion object {
        @Volatile
        private var INSTANCE: LumaDatabase? = null

        fun getInstance(context: Context): LumaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LumaDatabase::class.java,
                    "luma_player.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
