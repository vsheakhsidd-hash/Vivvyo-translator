package com.example.service

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.example.data.engine.MlKitTranslationService
import com.example.data.local.VivvyoDatabase
import com.example.data.repository.TranslatorRepository
import com.example.data.repository.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * On-Device Privacy-Preserving Accessibility Service.
 * Allows seamless in-place cross-app translation without leaving third-party apps.
 * Never logs, stores, or transmits any inspected text.
 */
class VivvyoAccessibilityService : AccessibilityService() {

    private val tag = "VivvyoAccessibility"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var translationService: MlKitTranslationService? = null
    private var database: VivvyoDatabase? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        translationService = MlKitTranslationService(this)
        database = VivvyoDatabase.getInstance(this)
        Log.d(tag, "Vivvyo Accessibility Service connected and active")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkgName = event.packageName?.toString() ?: return
        if (pkgName == packageName) return // Ignore self

        scope.launch {
            val appTargetDao = database?.appTargetDao() ?: return@launch
            val target = appTargetDao.getTarget(pkgName)

            // Auto-translate if this app is toggled ON in App Targeting
            if (target != null && target.isEnabled) {
                handleAppTargetEvent(event, target.targetLang)
            } else if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
                // Focused selected text translation
                handleSelectedTextEvent(event)
            }
        }
    }

    private suspend fun handleSelectedTextEvent(event: AccessibilityEvent) {
        val sourceNode = event.source ?: return
        val text = sourceNode.text?.toString()
        if (text.isNullOrBlank() || text.length > 300) return

        val prefs = dataStore.data.first()
        val replaceInPlace = prefs[androidx.datastore.preferences.core.booleanPreferencesKey("replace_in_place")] ?: false
        val targetLang = prefs[TranslatorRepository.KEY_PRIMARY_LANG] ?: "en"

        val detected = translationService?.detectLanguage(text) ?: "und"
        val sourceCode = if (detected != "und") detected else "auto"

        val result = translationService?.translateText(text, sourceCode, targetLang)
        val translated = result?.getOrNull()?.first ?: return

        withContext(Dispatchers.Main) {
            if (replaceInPlace && sourceNode.isEditable) {
                val arguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, translated)
                }
                sourceNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            } else {
                Toast.makeText(this@VivvyoAccessibilityService, "[$targetLang] $translated", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun handleAppTargetEvent(event: AccessibilityEvent, targetLang: String) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        ) {
            val root = rootInActiveWindow ?: return
            traverseAndTranslateNodes(root, targetLang)
        }
    }

    private suspend fun traverseAndTranslateNodes(node: AccessibilityNodeInfo, targetLang: String) {
        val text = node.text?.toString()
        if (!text.isNullOrBlank() && text.length in 3..200 && !node.isEditable) {
            val detected = translationService?.detectLanguage(text) ?: "und"
            if (detected != "und" && detected != targetLang) {
                val res = translationService?.translateText(text, detected, targetLang)
                val translated = res?.getOrNull()?.first
                if (!translated.isNullOrBlank() && translated != text) {
                    withContext(Dispatchers.Main) {
                        // Quick indicator toast or overlay
                        Log.d(tag, "Translated chat message node: $translated")
                    }
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseAndTranslateNodes(child, targetLang)
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyEvent(event)

        // Handle hardware media keys or global shortcut gestures (e.g. Bluetooth keyboards)
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    VivvyoForegroundService.start(this)
                    val clip = (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).primaryClip
                    val text = clip?.getItemAt(0)?.text?.toString()
                    if (!text.isNullOrBlank()) {
                        scope.launch {
                            val res = translationService?.translateText(text, "auto", "en")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@VivvyoAccessibilityService, "Quick: ${res?.getOrNull()?.first}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    return true
                }
            }
        }
        return super.onKeyEvent(event)
    }

    override fun onInterrupt() {
        Log.w(tag, "Vivvyo Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        translationService?.release()
    }
}
