package com.example.data.engine

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TextToSpeechManager(private val context: Context) {

    private val tag = "VivvyoTTS"
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                Log.d(tag, "TTS initialized successfully")
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }
                })
            } else {
                Log.e(tag, "TTS initialization failed with code: $status")
            }
        }
    }

    fun speak(text: String, languageCode: String) {
        if (!isInitialized || tts == null) {
            Log.w(tag, "TTS not ready yet")
            return
        }

        try {
            val locale = Locale.forLanguageTag(languageCode)
            val langResult = tts?.setLanguage(locale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to default or English
                tts?.setLanguage(Locale.getDefault())
            }

            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "VIVVYO_UTTERANCE_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.e(tag, "Failed to speak text", e)
            _isSpeaking.value = false
        }
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun openInstallTtsDataSettings(ctx: Context) {
        try {
            val installIntent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(installIntent)
        } catch (e: Exception) {
            Log.w(tag, "Could not open TTS install data intent: ${e.message}")
        }
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }
}
