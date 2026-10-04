package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_targets")
data class AppTargetEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val isEnabled: Boolean = false,
    val targetLang: String = "en"
)
