package com.kipu.app.core.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreEncryptedSessionStorageTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var storage: KeystoreEncryptedSessionStorage
    private val sessionFile = File(context.filesDir, "kipu_session.enc")

    @Before
    fun setUp() {
        sessionFile.delete()
        storage = KeystoreEncryptedSessionStorage(context)
    }

    @After
    fun tearDown() {
        sessionFile.delete()
    }

    @Test
    fun saveEncryptsWithoutPlaintextAndLoadReturnsOriginalPayload() = runTest {
        val plainToken = "secret-access-token-123456789-abcdef"
        val sessionJson = """{"accessToken":"$plainToken","refreshToken":"refresh-xyz","expiresIn":3600}"""

        storage.save(sessionJson)

        assertTrue("Session file must exist on disk after save", sessionFile.exists())
        val rawBytes = sessionFile.readBytes()
        val rawString = String(rawBytes, Charsets.UTF_8)
        assertFalse(
            "Encrypted file must never contain plaintext access token",
            rawString.contains(plainToken),
        )

        val restored = storage.load()
        assertEquals(sessionJson, restored)
    }

    @Test
    fun deleteRemovesFileAndLoadReturnsNull() = runTest {
        val sessionJson = """{"accessToken":"token-to-delete"}"""
        storage.save(sessionJson)
        assertTrue(sessionFile.exists())

        storage.delete()

        assertFalse("Session file must be deleted", sessionFile.exists())
        assertNull("Load after delete must return null", storage.load())
    }

    @Test
    fun corruptedCiphertextReturnsNullAndPurgesCorruptedFile() = runTest {
        val sessionJson = """{"accessToken":"valid-before-tampering"}"""
        storage.save(sessionJson)
        assertTrue(sessionFile.exists())

        val bytes = sessionFile.readBytes()
        // Corrupt the ciphertext bytes (after iv length and iv)
        if (bytes.size > 20) {
            bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0xFF).toByte()
            sessionFile.writeBytes(bytes)
        }

        val restored = storage.load()
        assertNull("Corrupted ciphertext must fail authentication and return null", restored)
        assertFalse("Corrupted file must be deleted upon decryption failure", sessionFile.exists())
    }
}
