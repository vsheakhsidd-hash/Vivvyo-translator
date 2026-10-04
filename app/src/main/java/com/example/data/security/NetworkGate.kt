package com.example.data.security

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.example.data.repository.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Privacy Gate: Strictly controls and tracks network egress.
 * Vivvyo Translator makes zero outbound network calls after setup.
 * All HTTP requests must be explicitly permitted by the user
 * (e.g. for user-initiated offline model pack downloads).
 */
class NetworkGate(private val context: Context) {

    companion object {
        val KEY_NETWORK_REQUEST_COUNT = intPreferencesKey("privacy_network_request_count")
        private val isDownloadPermitted = AtomicBoolean(false)
    }

    val requestCount: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_NETWORK_REQUEST_COUNT] ?: 0
    }

    /**
     * Executes a network operation only if explicitly permitted.
     * Throws SecurityException if network is locked down.
     */
    suspend fun <T> executeGatedCall(reason: String, block: suspend () -> T): Result<T> {
        if (!isDownloadPermitted.get()) {
            return Result.failure(
                SecurityException("NetworkGate: Outbound connection blocked ($reason). Vivvyo Translator operates strictly offline.")
            )
        }

        // Increment verified network request counter
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_NETWORK_REQUEST_COUNT] ?: 0
            prefs[KEY_NETWORK_REQUEST_COUNT] = current + 1
        }

        return try {
            Result.success(block())
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Temporarily opens the gate for a user-confirmed download.
     */
    suspend fun <T> withDownloadPermission(block: suspend () -> T): T {
        isDownloadPermitted.set(true)
        try {
            return block()
        } finally {
            isDownloadPermitted.set(false)
        }
    }

    suspend fun resetRequestCount() {
        context.dataStore.edit { prefs ->
            prefs[KEY_NETWORK_REQUEST_COUNT] = 0
        }
    }

    fun isGateOpen(): Boolean = isDownloadPermitted.get()
}
