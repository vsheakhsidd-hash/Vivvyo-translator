package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppTargetDao {
    @Query("SELECT * FROM app_targets ORDER BY appName ASC")
    fun getAllTargets(): Flow<List<AppTargetEntity>>

    @Query("SELECT * FROM app_targets WHERE isEnabled = 1")
    fun getEnabledTargets(): Flow<List<AppTargetEntity>>

    @Query("SELECT * FROM app_targets WHERE packageName = :pkg LIMIT 1")
    suspend fun getTarget(pkg: String): AppTargetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(targets: List<AppTargetEntity>)

    @Update
    suspend fun update(target: AppTargetEntity)

    @Query("UPDATE app_targets SET isEnabled = :enabled WHERE packageName = :pkg")
    suspend fun setEnabled(pkg: String, enabled: Boolean)
}
