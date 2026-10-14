package com.javier.fitbitair.domain

import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SecureAuthStateStoreTest {
    private class MemoryKeyValueStore : KeyValueStore {
        val values = ConcurrentHashMap<String, String>()
        override fun get(key: String): String? = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
    }

    private class FixedKeyProvider : SecretKeyProvider {
        private val key: SecretKey = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
        override fun getOrCreateKey(): SecretKey = key
    }

    @Test
    fun sessionTokenIsEncryptedAndCanBeCleared() {
        val storage = MemoryKeyValueStore()
        val authStore = SecureAuthStateStore(storage, AesGcmValueCodec(FixedKeyProvider()))

        authStore.saveSessionToken("private-app-session-token")

        assertNotEquals("private-app-session-token", storage.values["session_token"])
        assertEquals("private-app-session-token", authStore.loadSessionToken())
        authStore.clearSessionToken()
        assertNull(authStore.loadSessionToken())
    }

    @Test
    fun pendingOauthVerifierIsConsumedOnlyForMatchingState() {
        val storage = MemoryKeyValueStore()
        val authStore = SecureAuthStateStore(storage, AesGcmValueCodec(FixedKeyProvider()))

        authStore.savePendingOAuth("signed-state-value", "a".repeat(64))
        assertNull(authStore.consumePendingOAuth("different-state"))
        assertNull(authStore.consumePendingOAuth("signed-state-value"))

        authStore.savePendingOAuth("signed-state-value", "b".repeat(64))
        val pending = authStore.consumePendingOAuth("signed-state-value")
        assertEquals("signed-state-value", pending?.appState)
        assertEquals("b".repeat(64), pending?.codeVerifier)
        assertNull(authStore.consumePendingOAuth("signed-state-value"))
    }

    @Test
    fun aesGcmUsesFreshIvAndRejectsTampering() {
        val codec = AesGcmValueCodec(FixedKeyProvider())
        val first = codec.encrypt("health-session")
        val second = codec.encrypt("health-session")

        assertNotEquals(first, second)
        assertFalse(first.contains("health-session"))
        assertEquals("health-session", codec.decrypt(first))
        val tampered = first.dropLast(1) + if (first.last() == 'A') "B" else "A"
        assertThrows(SecurityException::class.java) { codec.decrypt(tampered) }
    }
}
