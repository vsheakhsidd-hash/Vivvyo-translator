package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "language_packs")
data class LanguagePackEntity(
    @PrimaryKey
    val code: String,
    val displayName: String,
    val nativeName: String,
    val engineType: String = "MLKIT", // "MLKIT", "ONNX_NLLB", "OPUS_MT"
    val isInstalled: Boolean = false,
    val sizeBytes: Long = 32_000_000L, // approx 30-45MB per pack
    val checksumSha256: String = "",
    val installedTimestamp: Long = 0L,
    val isDefaultPack: Boolean = false,
    val downloadProgress: Int = 0, // 0 - 100
    val isDownloading: Boolean = false
)
