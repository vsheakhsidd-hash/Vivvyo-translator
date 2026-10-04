package com.example.data.security

import java.util.Arrays

/**
 * Utility to securely wipe sensitive in-memory audio, text, and character buffers
 * immediately after translation completion.
 */
object MemoryWiper {

    fun wipe(buffer: CharArray?) {
        if (buffer != null) {
            Arrays.fill(buffer, '\u0000')
        }
    }

    fun wipe(buffer: ByteArray?) {
        if (buffer != null) {
            Arrays.fill(buffer, 0.toByte())
        }
    }

    fun wipe(buffer: FloatArray?) {
        if (buffer != null) {
            Arrays.fill(buffer, 0.0f)
        }
    }

    /**
     * Helper to process sensitive text in an auto-cleared buffer.
     */
    inline fun <R> withSecureChars(text: String, block: (CharArray) -> R): R {
        val chars = text.toCharArray()
        return try {
            block(chars)
        } finally {
            wipe(chars)
        }
    }
}
