package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.engine.MlKitTranslationService
import com.example.data.local.AppTargetDao
import com.example.data.local.AppTargetEntity
import com.example.data.local.LanguagePackDao
import com.example.data.local.LanguagePackEntity
import com.example.data.local.TranslationHistoryDao
import com.example.data.local.TranslationHistoryEntity
import com.example.data.model.SupportedLanguages
import com.example.data.security.KeystoreEncryption
import com.example.data.security.NetworkGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vivvyo_preferences")

class TranslatorRepository(
    private val context: Context,
    private val languagePackDao: LanguagePackDao,
    private val historyDao: TranslationHistoryDao,
    private val appTargetDao: AppTargetDao,
    val translationService: MlKitTranslationService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val networkGate = NetworkGate(context)
    val keystoreEncryption = KeystoreEncryption()

    companion object {
        val KEY_PRIMARY_LANG = stringPreferencesKey("primary_language")
        val KEY_INITIAL_SETUP_DONE = booleanPreferencesKey("initial_setup_done")
        val KEY_AUTO_DETECT = booleanPreferencesKey("auto_detect_enabled")
        val KEY_FLOATING_BUBBLE = booleanPreferencesKey("floating_bubble_enabled")
        val KEY_REPLACE_IN_PLACE = booleanPreferencesKey("replace_in_place")
        val KEY_SIMPLE_MODE = booleanPreferencesKey("simple_mode_enabled")
    }

    val allPacks: Flow<List<LanguagePackEntity>> = languagePackDao.getAllPacks()
    val installedPacks: Flow<List<LanguagePackEntity>> = languagePackDao.getInstalledPacks()

    // Encrypted at rest, decrypted in memory
    val translationHistory: Flow<List<TranslationHistoryEntity>> = historyDao.getAllHistory().map { list ->
        list.map { item ->
            item.copy(
                sourceText = keystoreEncryption.decrypt(item.sourceText),
                translatedText = keystoreEncryption.decrypt(item.translatedText)
            )
        }
    }

    val appTargets: Flow<List<AppTargetEntity>> = appTargetDao.getAllTargets()
    val networkRequestCount: Flow<Int> = networkGate.requestCount

    val primaryLanguage: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_PRIMARY_LANG] ?: "en"
    }

    val isInitialSetupDone: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_INITIAL_SETUP_DONE] ?: false
    }

    val isFloatingBubbleEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_FLOATING_BUBBLE] ?: true
    }

    val isReplaceInPlaceEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_REPLACE_IN_PLACE] ?: false
    }

    val isSimpleModeEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SIMPLE_MODE] ?: false
    }

    suspend fun initializeDatabaseIfNeeded() = withContext(Dispatchers.IO) {
        val count = languagePackDao.getPackCount()
        if (count == 0) {
            val initialEntities = SupportedLanguages.ALL_LANGUAGES.map { lang ->
                LanguagePackEntity(
                    code = lang.code,
                    displayName = lang.name,
                    nativeName = lang.nativeName,
                    engineType = lang.engine,
                    isInstalled = false,
                    sizeBytes = lang.sizeBytes,
                    checksumSha256 = lang.sha256Checksum,
                    isDefaultPack = lang.isDefault
                )
            }
            languagePackDao.insertAll(initialEntities)
        }

        syncInstalledPacks()
    }

    suspend fun syncInstalledPacks() = withContext(Dispatchers.IO) {
        val downloadedCodes = translationService.getDownloadedLanguageCodes()
        for (code in downloadedCodes) {
            languagePackDao.setInstalled(code, true)
        }
    }

    suspend fun downloadDefaultPacks(
        onPackProgress: (String, Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        networkGate.withDownloadPermission {
            for (code in SupportedLanguages.DEFAULT_PACK_CODES) {
                languagePackDao.updateDownloadState(code, 15, true)
                onPackProgress(code, 15)

                val result = translationService.downloadLanguagePack(code) { progress ->
                    scope.launch {
                        languagePackDao.updateDownloadState(code, progress, progress < 100)
                        onPackProgress(code, progress)
                    }
                }

                if (result.isSuccess) {
                    languagePackDao.setInstalled(code, true)
                    onPackProgress(code, 100)
                } else {
                    languagePackDao.updateDownloadState(code, 0, false)
                }
            }

            setInitialSetupDone(true)
        }
    }

    suspend fun downloadPack(code: String, onProgress: (Int) -> Unit): Result<Boolean> = withContext(Dispatchers.IO) {
        networkGate.withDownloadPermission {
            languagePackDao.updateDownloadState(code, 10, true)
            val result = translationService.downloadLanguagePack(code) { progress ->
                scope.launch {
                    languagePackDao.updateDownloadState(code, progress, progress < 100)
                    onProgress(progress)
                }
            }
            if (result.isSuccess) {
                languagePackDao.setInstalled(code, true)
            } else {
                languagePackDao.updateDownloadState(code, 0, false)
            }
            result
        }
    }

    suspend fun removePack(code: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val result = translationService.deleteLanguagePack(code)
        if (result.isSuccess) {
            languagePackDao.removePack(code)
        }
        result
    }

    suspend fun setPrimaryLanguage(code: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PRIMARY_LANG] = code
        }
    }

    suspend fun setInitialSetupDone(done: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_INITIAL_SETUP_DONE] = done
        }
    }

    suspend fun setFloatingBubbleEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FLOATING_BUBBLE] = enabled
        }
    }

    suspend fun setReplaceInPlaceEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_REPLACE_IN_PLACE] = enabled
        }
    }

    suspend fun setSimpleModeEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SIMPLE_MODE] = enabled
        }
    }

    suspend fun saveHistory(
        sourceText: String,
        translatedText: String,
        sourceLang: String,
        targetLang: String
    ) {
        if (sourceText.isNotBlank() && translatedText.isNotBlank()) {
            val encSource = keystoreEncryption.encrypt(sourceText.trim())
            val encTranslated = keystoreEncryption.encrypt(translatedText.trim())
            historyDao.insert(
                TranslationHistoryEntity(
                    sourceText = encSource,
                    translatedText = encTranslated,
                    sourceLang = sourceLang,
                    targetLang = targetLang
                )
            )
        }
    }

    suspend fun clearHistory() {
        historyDao.clearAll()
    }

    suspend fun clearAllLanguagePacks() = withContext(Dispatchers.IO) {
        val installed = languagePackDao.getInstalledPacks().first()
        for (pack in installed) {
            translationService.deleteLanguagePack(pack.code)
            languagePackDao.removePack(pack.code)
        }
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        historyDao.clearAll()
        clearAllLanguagePacks()
        networkGate.resetRequestCount()
        context.dataStore.edit { it.clear() }
    }

    suspend fun saveAppTargets(targets: List<AppTargetEntity>) {
        appTargetDao.insertAll(targets)
    }

    suspend fun setAppTargetEnabled(pkg: String, enabled: Boolean) {
        appTargetDao.setEnabled(pkg, enabled)
    }
}
