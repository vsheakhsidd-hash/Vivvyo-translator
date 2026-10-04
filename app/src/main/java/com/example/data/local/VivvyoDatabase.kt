package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        LanguagePackEntity::class,
        TranslationHistoryEntity::class,
        AppTargetEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class VivvyoDatabase : RoomDatabase() {
    abstract fun languagePackDao(): LanguagePackDao
    abstract fun translationHistoryDao(): TranslationHistoryDao
    abstract fun appTargetDao(): AppTargetDao

    companion object {
        @Volatile
        private var INSTANCE: VivvyoDatabase? = null

        fun getInstance(context: Context): VivvyoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VivvyoDatabase::class.java,
                    "vivvyo_translator.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
