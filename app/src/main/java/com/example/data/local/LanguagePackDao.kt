package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LanguagePackDao {
    @Query("SELECT * FROM language_packs ORDER BY isDefaultPack DESC, displayName ASC")
    fun getAllPacks(): Flow<List<LanguagePackEntity>>

    @Query("SELECT * FROM language_packs WHERE isInstalled = 1 ORDER BY displayName ASC")
    fun getInstalledPacks(): Flow<List<LanguagePackEntity>>

    @Query("SELECT * FROM language_packs WHERE code = :code LIMIT 1")
    suspend fun getPackByCode(code: String): LanguagePackEntity?

    @Query("SELECT COUNT(*) FROM language_packs")
    suspend fun getPackCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(packs: List<LanguagePackEntity>)

    @Update
    suspend fun update(pack: LanguagePackEntity)

    @Query("UPDATE language_packs SET isInstalled = :installed, downloadProgress = 100, isDownloading = 0, installedTimestamp = :timestamp WHERE code = :code")
    suspend fun setInstalled(code: String, installed: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE language_packs SET downloadProgress = :progress, isDownloading = :isDownloading WHERE code = :code")
    suspend fun updateDownloadState(code: String, progress: Int, isDownloading: Boolean)

    @Query("UPDATE language_packs SET isInstalled = 0, downloadProgress = 0, isDownloading = 0 WHERE code = :code")
    suspend fun removePack(code: String)
}
