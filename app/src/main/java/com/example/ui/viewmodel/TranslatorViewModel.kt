package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.engine.MlKitTranslationService
import com.example.data.engine.OcrTextExtractor
import com.example.data.engine.SpeechInputManager
import com.example.data.engine.TextToSpeechManager
import com.example.data.local.AppTargetEntity
import com.example.data.local.LanguagePackEntity
import com.example.data.local.TranslationHistoryEntity
import com.example.data.local.VivvyoDatabase
import com.example.data.model.SupportedLanguages
import com.example.data.repository.TranslatorRepository
import com.example.service.VivvyoForegroundService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PackFilter {
    ALL, INSTALLED, AVAILABLE, EXTENDED
}

data class TextTranslationState(
    val inputText: String = "",
    val translatedText: String = "",
    val sourceLang: String = "auto", // "auto" for auto-detect
    val targetLang: String = "es",
    val detectedLangCode: String? = null,
    val detectedLangName: String? = null,
    val isTranslating: Boolean = false,
    val engineUsed: String = "ML Kit On-Device",
    val errorMessage: String? = null
)

data class VoiceTranslationState(
    val isListening: Boolean = false,
    val isContinuousMode: Boolean = false,
    val isMirrorMode: Boolean = false,
    val transcript: String = "",
    val translation: String = "",
    val sourceLang: String = "en",
    val targetLang: String = "es",
    val soundLevel: Float = 0f,
    val isSpeaking: Boolean = false
)

data class ImageTranslationState(
    val imageUri: Uri? = null,
    val extractedText: String = "",
    val editableExtractedText: String = "",
    val translatedText: String = "",
    val isLensOverlayActive: Boolean = true,
    val isProcessing: Boolean = false,
    val sourceLang: String = "auto",
    val targetLang: String = "en",
    val errorMessage: String? = null
)

class TranslatorViewModel(application: Application) : AndroidViewModel(application) {

    private val db = VivvyoDatabase.getInstance(application)
    private val translationService = MlKitTranslationService(application)
    private val repository = TranslatorRepository(
        application,
        db.languagePackDao(),
        db.translationHistoryDao(),
        db.appTargetDao(),
        translationService
    )

    private val ttsManager = TextToSpeechManager(application)
    private val speechManager = SpeechInputManager(application)
    private val ocrExtractor = OcrTextExtractor(application)

    // UI States
    private val _textState = MutableStateFlow(TextTranslationState())
    val textState: StateFlow<TextTranslationState> = _textState.asStateFlow()

    private val _voiceState = MutableStateFlow(VoiceTranslationState())
    val voiceState: StateFlow<VoiceTranslationState> = _voiceState.asStateFlow()

    private val _imageState = MutableStateFlow(ImageTranslationState())
    val imageState: StateFlow<ImageTranslationState> = _imageState.asStateFlow()

    private val _packFilter = MutableStateFlow(PackFilter.ALL)
    val packFilter: StateFlow<PackFilter> = _packFilter.asStateFlow()

    private val _firstLaunchPackProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val firstLaunchPackProgress: StateFlow<Map<String, Int>> = _firstLaunchPackProgress.asStateFlow()

    private val _showFirstLaunchBanner = MutableStateFlow(false)
    val showFirstLaunchBanner: StateFlow<Boolean> = _showFirstLaunchBanner.asStateFlow()

    private val _isDownloadingDefaults = MutableStateFlow(false)
    val isDownloadingDefaults: StateFlow<Boolean> = _isDownloadingDefaults.asStateFlow()

    val allPacks: StateFlow<List<LanguagePackEntity>> = repository.allPacks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val installedPacks: StateFlow<List<LanguagePackEntity>> = repository.installedPacks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<TranslationHistoryEntity>> = repository.translationHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appTargets: StateFlow<List<AppTargetEntity>> = repository.appTargets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val primaryLanguage: StateFlow<String> = repository.primaryLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")

    val isFloatingBubbleEnabled: StateFlow<Boolean> = repository.isFloatingBubbleEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isReplaceInPlaceEnabled: StateFlow<Boolean> = repository.isReplaceInPlaceEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isSimpleModeEnabled: StateFlow<Boolean> = repository.isSimpleModeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val networkRequestCount: StateFlow<Int> = repository.networkRequestCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val lastLatencyMs: StateFlow<Long> = translationService.lastLatencyMs

    val isSpeaking: StateFlow<Boolean> = ttsManager.isSpeaking

    private var detectionJob: Job? = null

    init {
        viewModelScope.launch {
            repository.initializeDatabaseIfNeeded()
            loadInstalledApps()
        }

        // Check if first launch
        viewModelScope.launch {
            repository.isInitialSetupDone.collectLatest { done ->
                if (!done) {
                    _showFirstLaunchBanner.value = true
                    startDefaultPacksDownload()
                }
            }
        }

        // Collect voice recognition output
        viewModelScope.launch {
            speechManager.isListening.collectLatest { listening ->
                _voiceState.update { it.copy(isListening = listening) }
            }
        }
        viewModelScope.launch {
            speechManager.soundLevel.collectLatest { level ->
                _voiceState.update { it.copy(soundLevel = level) }
            }
        }
        viewModelScope.launch {
            speechManager.recognizedText.collectLatest { text ->
                if (text.isNotBlank()) {
                    _voiceState.update { it.copy(transcript = text) }
                    translateVoiceText(text)
                }
            }
        }
    }

    fun startDefaultPacksDownload() {
        if (_isDownloadingDefaults.value) return
        _isDownloadingDefaults.value = true

        viewModelScope.launch {
            val initial = SupportedLanguages.DEFAULT_PACK_CODES.associateWith { 0 }
            _firstLaunchPackProgress.value = initial

            repository.downloadDefaultPacks { code, progress ->
                _firstLaunchPackProgress.update { map ->
                    map.toMutableMap().apply { put(code, progress) }
                }
            }
            _isDownloadingDefaults.value = false
        }
    }

    fun dismissFirstLaunchBanner() {
        _showFirstLaunchBanner.value = false
        viewModelScope.launch {
            repository.setInitialSetupDone(true)
        }
    }

    fun onInputTextChanged(newText: String) {
        _textState.update { it.copy(inputText = newText, errorMessage = null) }

        detectionJob?.cancel()
        detectionJob = viewModelScope.launch {
            delay(350)
            if (newText.isNotBlank()) {
                val detected = translationService.detectLanguage(newText)
                if (detected != "und") {
                    val lang = SupportedLanguages.findByCode(detected)
                    _textState.update {
                        it.copy(
                            detectedLangCode = detected,
                            detectedLangName = lang?.name ?: detected.uppercase()
                        )
                    }
                } else {
                    _textState.update { it.copy(detectedLangCode = null, detectedLangName = null) }
                }
            } else {
                _textState.update { it.copy(detectedLangCode = null, detectedLangName = null, translatedText = "") }
            }
        }
    }

    fun translateText() {
        val current = _textState.value
        val text = current.inputText.trim()
        if (text.isBlank()) return

        val effectiveSource = if (current.sourceLang == "auto") {
            current.detectedLangCode ?: "en"
        } else {
            current.sourceLang
        }

        val target = current.targetLang

        _textState.update { it.copy(isTranslating = true, errorMessage = null) }

        viewModelScope.launch {
            val result = translationService.translateText(text, effectiveSource, target)
            result.onSuccess { (translated, engine) ->
                _textState.update {
                    it.copy(
                        translatedText = translated,
                        engineUsed = engine,
                        isTranslating = false
                    )
                }
                repository.saveHistory(text, translated, effectiveSource, target)
            }.onFailure { error ->
                _textState.update {
                    it.copy(
                        isTranslating = false,
                        errorMessage = error.localizedMessage ?: "Offline translation failed"
                    )
                }
            }
        }
    }

    fun swapLanguages() {
        val current = _textState.value
        val newTarget = if (current.sourceLang == "auto") {
            current.detectedLangCode ?: "en"
        } else {
            current.sourceLang
        }
        val newSource = current.targetLang

        val swappedInput = current.translatedText
        val swappedOutput = current.inputText

        _textState.update {
            it.copy(
                sourceLang = newSource,
                targetLang = newTarget,
                inputText = swappedInput,
                translatedText = swappedOutput,
                detectedLangCode = null,
                detectedLangName = null
            )
        }
    }

    fun setSourceLanguage(code: String) {
        _textState.update { it.copy(sourceLang = code) }
    }

    fun setTargetLanguage(code: String) {
        _textState.update { it.copy(targetLang = code) }
    }

    fun clearInput() {
        _textState.update {
            it.copy(
                inputText = "",
                translatedText = "",
                detectedLangCode = null,
                detectedLangName = null,
                errorMessage = null
            )
        }
    }

    fun speakText(text: String, langCode: String) {
        if (text.isNotBlank()) {
            ttsManager.speak(text, langCode)
        }
    }

    fun stopSpeaking() {
        ttsManager.stop()
    }

    // Voice Tab methods
    fun startVoiceListening(languageCode: String) {
        val continuous = _voiceState.value.isContinuousMode
        if (continuous) {
            VivvyoForegroundService.start(getApplication())
        }
        speechManager.startListening(languageCode, continuous) { recognized ->
            _voiceState.update { it.copy(transcript = recognized) }
            translateVoiceText(recognized)
        }
    }

    fun stopVoiceListening() {
        speechManager.stopListening()
    }

    fun toggleContinuousListening() {
        val next = !_voiceState.value.isContinuousMode
        _voiceState.update { it.copy(isContinuousMode = next) }
        speechManager.setContinuousMode(next)
        if (next && _voiceState.value.isListening) {
            VivvyoForegroundService.start(getApplication())
        }
    }

    fun toggleMirrorMode() {
        _voiceState.update { it.copy(isMirrorMode = !it.isMirrorMode) }
    }

    fun setVoiceLanguages(source: String, target: String) {
        _voiceState.update { it.copy(sourceLang = source, targetLang = target) }
    }

    fun swapVoiceLanguages() {
        _voiceState.update {
            it.copy(
                sourceLang = it.targetLang,
                targetLang = it.sourceLang,
                transcript = it.translation,
                translation = it.transcript
            )
        }
    }

    private fun translateVoiceText(text: String) {
        val current = _voiceState.value
        viewModelScope.launch {
            val result = translationService.translateText(text, current.sourceLang, current.targetLang)
            result.onSuccess { (translated, _) ->
                _voiceState.update { it.copy(translation = translated) }
                speakText(translated, current.targetLang)
                repository.saveHistory(text, translated, current.sourceLang, current.targetLang)
            }
        }
    }

    // Image Tab methods
    fun onImageSelected(uri: Uri?) {
        _imageState.update {
            it.copy(
                imageUri = uri,
                extractedText = "",
                editableExtractedText = "",
                translatedText = ""
            )
        }
        if (uri != null) {
            extractAndTranslateImage(uri)
        }
    }

    fun extractAndTranslateImage(uri: Uri) {
        _imageState.update { it.copy(isProcessing = true, errorMessage = null) }
        viewModelScope.launch {
            val ocrResult = ocrExtractor.extractTextFromUri(uri)
            ocrResult.onSuccess { extracted ->
                if (extracted.isBlank()) {
                    _imageState.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = "No readable text detected in this image."
                        )
                    }
                    return@onSuccess
                }

                _imageState.update {
                    it.copy(
                        extractedText = extracted,
                        editableExtractedText = extracted
                    )
                }

                val detected = translationService.detectLanguage(extracted)
                val source = if (detected != "und") detected else "en"
                val target = _imageState.value.targetLang

                val transResult = translationService.translateText(extracted, source, target)
                transResult.onSuccess { (translated, _) ->
                    _imageState.update {
                        it.copy(
                            translatedText = translated,
                            sourceLang = source,
                            isProcessing = false
                        )
                    }
                    repository.saveHistory(extracted, translated, source, target)
                }.onFailure { err ->
                    _imageState.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = err.localizedMessage ?: "Image translation failed."
                        )
                    }
                }
            }.onFailure { error ->
                _imageState.update {
                    it.copy(
                        isProcessing = false,
                        errorMessage = error.localizedMessage ?: "OCR text extraction failed."
                    )
                }
            }
        }
    }

    fun onEditableOcrTextChanged(text: String) {
        _imageState.update { it.copy(editableExtractedText = text) }
    }

    fun retranslateEditedOcrText() {
        val current = _imageState.value
        val text = current.editableExtractedText.trim()
        if (text.isBlank()) return

        _imageState.update { it.copy(isProcessing = true) }
        viewModelScope.launch {
            val source = current.sourceLang
            val target = current.targetLang
            val result = translationService.translateText(text, source, target)
            result.onSuccess { (translated, _) ->
                _imageState.update {
                    it.copy(translatedText = translated, isProcessing = false)
                }
            }.onFailure {
                _imageState.update { it.copy(isProcessing = false) }
            }
        }
    }

    fun toggleLensOverlay() {
        _imageState.update { it.copy(isLensOverlayActive = !it.isLensOverlayActive) }
    }

    fun setImageTargetLanguage(code: String) {
        _imageState.update { it.copy(targetLang = code) }
        val uri = _imageState.value.imageUri
        val text = _imageState.value.editableExtractedText.ifBlank { _imageState.value.extractedText }
        if (uri != null && text.isNotBlank()) {
            val source = _imageState.value.sourceLang
            viewModelScope.launch {
                val transResult = translationService.translateText(text, source, code)
                transResult.onSuccess { (translated, _) ->
                    _imageState.update { it.copy(translatedText = translated) }
                }
            }
        }
    }

    // App Targeting
    fun loadInstalledApps() {
        viewModelScope.launch {
            try {
                val pm = getApplication<Application>().packageManager
                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                val apps = resolveInfos.mapNotNull { ri ->
                    val pkg = ri.activityInfo.packageName
                    if (pkg != getApplication<Application>().packageName) {
                        val name = ri.loadLabel(pm).toString()
                        AppTargetEntity(packageName = pkg, appName = name, isEnabled = false)
                    } else null
                }.distinctBy { it.packageName }

                if (apps.isNotEmpty()) {
                    repository.saveAppTargets(apps)
                }
            } catch (e: Exception) {
                Log.w("VivvyoVM", "Error querying installed apps: ${e.message}")
            }
        }
    }

    fun toggleAppTarget(pkg: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.setAppTargetEnabled(pkg, enabled)
        }
    }

    // Settings
    fun setFloatingBubbleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setFloatingBubbleEnabled(enabled)
            if (enabled) {
                VivvyoForegroundService.start(getApplication())
            }
        }
    }

    fun setReplaceInPlaceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setReplaceInPlaceEnabled(enabled)
        }
    }

    fun setSimpleModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setSimpleModeEnabled(enabled)
        }
    }

    // Language pack operations
    fun setPackFilter(filter: PackFilter) {
        _packFilter.value = filter
    }

    fun downloadPack(code: String) {
        viewModelScope.launch {
            repository.downloadPack(code) { }
        }
    }

    fun removePack(code: String) {
        viewModelScope.launch {
            repository.removePack(code)
        }
    }

    fun setPrimaryLanguage(code: String) {
        viewModelScope.launch {
            repository.setPrimaryLanguage(code)
            _textState.update { it.copy(targetLang = code) }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun clearAllLanguagePacks() {
        viewModelScope.launch {
            repository.clearAllLanguagePacks()
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
        speechManager.release()
        ocrExtractor.close()
        translationService.release()
    }
}
