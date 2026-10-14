package com.javier.fitbitair.domain

import java.security.GeneralSecurityException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

fun interface SecretKeyProvider {
    fun getOrCreateKey(): SecretKey
}

interface EncryptedValueCodec {
    fun encrypt(plaintext: String): String
    fun decrypt(ciphertext: String): String
}

interface SessionCredentialStore {
    fun loadSessionToken(): String?
    fun saveSessionToken(token: String)
    fun clearSession()
    fun savePendingOAuth(appState: String, codeVerifier: String)
    fun consumePendingOAuth(appState: String): PendingOAuth?
}

class AesGcmValueCodec(private val keyProvider: SecretKeyProvider) : EncryptedValueCodec {
    override fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider.getOrCreateKey())
        val iv = cipher.iv ?: throw SecurityException("Encryption IV unavailable")
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val payload = byteArrayOf(iv.size.toByte()) + iv + encrypted
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
    }

    override fun decrypt(ciphertext: String): String {
        try {
            val payload = Base64.getUrlDecoder().decode(ciphertext)
            if (payload.isEmpty()) throw SecurityException("Encrypted value is empty")
            val ivSize = payload[0].toInt() and 0xff
            if (ivSize !in MIN_IV_SIZE..MAX_IV_SIZE || payload.size <= 1 + ivSize + TAG_SIZE_BYTES) {
                throw SecurityException("Encrypted value is malformed")
            }
            val iv = payload.copyOfRange(1, 1 + ivSize)
            val encrypted = payload.copyOfRange(1 + ivSize, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                keyProvider.getOrCreateKey(),
                GCMParameterSpec(TAG_SIZE_BITS, iv),
            )
            return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        } catch (error: SecurityException) {
            throw error
        } catch (error: IllegalArgumentException) {
            throw SecurityException("Encrypted value could not be decoded", error)
        } catch (error: GeneralSecurityException) {
            throw SecurityException("Encrypted value could not be authenticated", error)
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_SIZE_BITS = 128
        const val TAG_SIZE_BYTES = TAG_SIZE_BITS / 8
        const val MIN_IV_SIZE = 12
        const val MAX_IV_SIZE = 16
    }
}

data class PendingOAuth(val appState: String, val codeVerifier: String)

/** Stores only an app session token and a short-lived PKCE verifier, encrypted with Android Keystore. */
class SecureAuthStateStore(
    private val values: KeyValueStore,
    private val codec: EncryptedValueCodec,
) : SessionCredentialStore {
    override fun saveSessionToken(token: String) {
        require(token.isNotBlank()) { "Session token cannot be blank" }
        values.put(SESSION_TOKEN, codec.encrypt(token))
    }

    override fun loadSessionToken(): String? = readDecrypted(SESSION_TOKEN)

    override fun clearSession() {
        clearAll()
    }

    fun clearSessionToken() {
        values.remove(SESSION_TOKEN)
    }

    @Synchronized
    override fun savePendingOAuth(appState: String, codeVerifier: String) {
        require(appState.isNotBlank()) { "OAuth app state cannot be blank" }
        require(VERIFIER_PATTERN.matches(codeVerifier)) { "OAuth code verifier is invalid" }
        values.put(PENDING_STATE, codec.encrypt(appState))
        values.put(PENDING_VERIFIER, codec.encrypt(codeVerifier))
    }

    @Synchronized
    override fun consumePendingOAuth(appState: String): PendingOAuth? {
        val savedAppState = readDecrypted(PENDING_STATE)
        val verifier = readDecrypted(PENDING_VERIFIER)
        values.remove(PENDING_STATE)
        values.remove(PENDING_VERIFIER)
        if (savedAppState == null || verifier == null || savedAppState != appState) return null
        return PendingOAuth(savedAppState, verifier)
    }

    @Synchronized
    fun clearAll() {
        values.remove(SESSION_TOKEN)
        values.remove(PENDING_STATE)
        values.remove(PENDING_VERIFIER)
    }

    private fun readDecrypted(key: String): String? {
        val encrypted = values.get(key) ?: return null
        return try {
            codec.decrypt(encrypted)
        } catch (_: SecurityException) {
            values.remove(key)
            null
        }
    }

    private companion object {
        const val SESSION_TOKEN = "session_token"
        const val PENDING_STATE = "pending_oauth_state"
        const val PENDING_VERIFIER = "pending_oauth_verifier"
        val VERIFIER_PATTERN = Regex("^[A-Za-z0-9._~-]{43,128}$")
    }
}
