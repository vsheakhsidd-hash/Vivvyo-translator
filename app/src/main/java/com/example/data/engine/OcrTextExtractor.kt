package com.example.data.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * High-precision on-device text recognition (OCR) supporting Latin,
 * Devanagari (Hindi, Marathi, etc.), Chinese, Japanese, and Korean scripts.
 */
class OcrTextExtractor(private val context: Context) {

    private val tag = "VivvyoOcrExtractor"

    private val defaultRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private val devanagariRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    }
    private val chineseRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }
    private val japaneseRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    }
    private val koreanRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    }

    /**
     * Extracts text from an image URI using the appropriate script recognizer.
     */
    suspend fun extractTextFromUri(uri: Uri, targetScript: String = "latin"): Result<String> = withContext(Dispatchers.IO) {
        try {
            val image = InputImage.fromFilePath(context, uri)
            return@withContext processImage(image, targetScript)
        } catch (e: Exception) {
            Log.e(tag, "Error loading image from URI: ${e.message}", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Extracts text from a Bitmap.
     */
    suspend fun extractTextFromBitmap(bitmap: Bitmap, targetScript: String = "latin"): Result<String> = withContext(Dispatchers.IO) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            return@withContext processImage(image, targetScript)
        } catch (e: Exception) {
            Log.e(tag, "Error loading bitmap: ${e.message}", e)
            return@withContext Result.failure(e)
        }
    }

    private suspend fun processImage(image: InputImage, script: String): Result<String> {
        val recognizer = when (script.lowercase()) {
            "devanagari", "hi", "mr", "ne" -> devanagariRecognizer
            "chinese", "zh" -> chineseRecognizer
            "japanese", "ja" -> japaneseRecognizer
            "korean", "ko" -> koreanRecognizer
            else -> defaultRecognizer
        }

        return suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val text = visionText.text
                    Log.d(tag, "OCR succeeded, extracted length: ${text.length}")
                    continuation.resume(Result.success(text))
                }
                .addOnFailureListener { error ->
                    Log.e(tag, "OCR failed: ${error.message}", error)
                    continuation.resume(Result.failure(error))
                }
        }
    }

    fun close() {
        defaultRecognizer.close()
        devanagariRecognizer.close()
        chineseRecognizer.close()
        japaneseRecognizer.close()
        koreanRecognizer.close()
    }
}
