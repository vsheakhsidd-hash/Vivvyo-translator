package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.engine.MlKitTranslationService
import com.example.data.local.AppTargetEntity
import com.example.data.local.VivvyoDatabase
import com.example.data.model.SupportedLanguages
import com.example.data.security.MemoryWiper
import com.example.data.security.NetworkGate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Vivvyo Translator", appName)
    }

    @Test
    fun `verify default language packs catalog`() {
        assertEquals(10, SupportedLanguages.DEFAULT_PACK_CODES.size)
        assertTrue(SupportedLanguages.ALL_LANGUAGES.size >= 50)

        val spanish = SupportedLanguages.findByCode("es")
        assertNotNull(spanish)
        assertEquals("Spanish", spanish?.name)

        val welsh = SupportedLanguages.findByCode("cy")
        assertNotNull(welsh)
        assertEquals("ONNX_NLLB", welsh?.engine)
    }

    @Test
    fun `verify app targeting room database`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = VivvyoDatabase.getInstance(context)
        val dao = db.appTargetDao()

        val item = AppTargetEntity(
            packageName = "com.test.chat",
            appName = "Test Chat",
            isEnabled = true,
            targetLang = "es"
        )
        dao.insertAll(listOf(item))

        val retrieved = dao.getTarget("com.test.chat")
        assertNotNull(retrieved)
        assertEquals("Test Chat", retrieved?.appName)
        assertTrue(retrieved?.isEnabled == true)
    }

    @Test
    fun `verify network gate blocks unauthorized egress`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val gate = NetworkGate(context)

        // Gate should be closed by default
        assertFalse(gate.isGateOpen())

        val blockedResult = gate.executeGatedCall("test") {
            "unauthorized_data"
        }
        assertTrue(blockedResult.isFailure)
        assertTrue(blockedResult.exceptionOrNull() is SecurityException)

        // Opening gate with explicit permission succeeds
        val permittedResult = gate.withDownloadPermission {
            gate.executeGatedCall("download_pack") {
                "downloaded_model_bytes"
            }
        }
        assertTrue(permittedResult.isSuccess)
        assertEquals("downloaded_model_bytes", permittedResult.getOrNull())
    }

    @Test
    fun `verify memory wiper clears sensitive buffers`() {
        val chars = "sensitive_transcript".toCharArray()
        MemoryWiper.wipe(chars)
        for (c in chars) {
            assertEquals('\u0000', c)
        }

        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        MemoryWiper.wipe(bytes)
        for (b in bytes) {
            assertEquals(0.toByte(), b)
        }
    }

    @Test
    fun `verify sha256 checksum validation logic`() {
        val sampleData = "vivvyo_offline_language_pack_v1".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256")
        val expectedSha = digest.digest(sampleData).joinToString("") { "%02x".format(it) }

        assertTrue(MlKitTranslationService.verifyChecksum(sampleData, expectedSha))
        assertFalse(MlKitTranslationService.verifyChecksum(sampleData, "invalid_sha256_hash"))
    }
}
