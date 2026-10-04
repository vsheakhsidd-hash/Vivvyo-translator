package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationHistoryDao {
    @Query("SELECT * FROM translation_history ORDER BY timestamp DESC LIMIT 50")
    fun getAllHistory(): Flow<List<TranslationHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: TranslationHistoryEntity)

    @Query("DELETE FROM translation_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM translation_history")
    suspend fun clearAll()
}
