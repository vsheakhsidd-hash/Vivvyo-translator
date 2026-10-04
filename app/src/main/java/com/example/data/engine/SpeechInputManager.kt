package com.example.data.engine

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SpeechInputManager(private val context: Context) {

    private val tag = "VivvyoSpeechInput"
    private var speechRecognizer: SpeechRecognizer? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var restartJob: Job? = null
    private val vadDetector = SileroVadDetector(context)

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isContinuousMode = MutableStateFlow(false)
    val isContinuousMode: StateFlow<Boolean> = _isContinuousMode.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    private val _soundLevel = MutableStateFlow(0f)
    val soundLevel: StateFlow<Float> = _soundLevel.asStateFlow()

    private val _isOnDeviceAvailable = MutableStateFlow(true)
    val isOnDeviceAvailable: StateFlow<Boolean> = _isOnDeviceAvailable.asStateFlow()

    private var activeLanguageCode: String = "en"
    private var onResultCallback: ((String) -> Unit)? = null

    init {
        checkOnDeviceAvailability()
    }

    fun checkOnDeviceAvailability(): Boolean {
        val available = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        } else {
            SpeechRecognizer.isRecognitionAvailable(context)
        }
        _isOnDeviceAvailable.value = available
        return available
    }

    fun setContinuousMode(enabled: Boolean) {
        _isContinuousMode.value = enabled
    }

    fun startListening(
        languageCode: String,
        continuous: Boolean = _isContinuousMode.value,
        onResult: (String) -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(tag, "Speech recognition is not available on this device")
            return
        }

        stopListening()
        this.activeLanguageCode = languageCode
        this._isContinuousMode.value = continuous
        this.onResultCallback = onResult
        this.vadDetector.reset()

        initAndStartRecognizer()
    }

    private fun initAndStartRecognizer() {
        try {
            // Android 12+ on-device recognizer for zero-latency offline transcription
            speechRecognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            ) {
                Log.d(tag, "Using Android 12+ On-Device SpeechRecognizer")
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                Log.d(tag, "Using Standard SpeechRecognizer with EXTRA_PREFER_OFFLINE")
                SpeechRecognizer.createSpeechRecognizer(context)
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                }

                override fun onBeginningOfSpeech() {
                    _isListening.value = true
                }

                override fun onRmsChanged(rmsdB: Float) {
                    val level = rmsdB.coerceIn(0f, 10f)
                    _soundLevel.value = level
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _isListening.value = false
                }

                override fun onError(error: Int) {
                    Log.w(tag, "Speech recognition error code: $error")
                    _isListening.value = false

                    // If continuous listening is enabled, recover after a brief pause
                    if (_isContinuousMode.value) {
                        scheduleContinuousRestart()
                    }
                }

                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        _recognizedText.value = text
                        onResultCallback?.invoke(text)
                    }

                    // Seamless continuous voice-to-voice flow
                    if (_isContinuousMode.value) {
                        scheduleContinuousRestart()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        _recognizedText.value = text
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, activeLanguageCode)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            speechRecognizer?.startListening(intent)
            _isListening.value = true
        } catch (e: Exception) {
            Log.e(tag, "Failed to start speech recognizer: ${e.message}", e)
            _isListening.value = false
        }
    }

    private fun scheduleContinuousRestart() {
        restartJob?.cancel()
        restartJob = scope.launch {
            delay(400) // Brief debounce between conversational turns
            if (_isContinuousMode.value) {
                initAndStartRecognizer()
            }
        }
    }

    fun stopListening() {
        restartJob?.cancel()
        _isContinuousMode.value = false
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(tag, "Error stopping recognizer", e)
        }
        speechRecognizer = null
        _isListening.value = false
        _soundLevel.value = 0f
    }

    /**
     * Fallback for older devices to download offline speech language models.
     */
    fun openVoiceInputSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val genericIntent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(genericIntent)
        }
    }

    fun release() {
        stopListening()
        vadDetector.close()
    }
}
