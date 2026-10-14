package com.javier.fitbitair.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.javier.fitbitair.domain.AesGcmValueCodec
import com.javier.fitbitair.domain.KeyValueStore
import com.javier.fitbitair.domain.PendingOAuth
import com.javier.fitbitair.domain.SecretKeyProvider
import com.javier.fitbitair.domain.SecureAuthStateStore
import com.javier.fitbitair.domain.SessionCredentialStore
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class SessionTokenStore(context: Context) : SessionCredentialStore {
    private val state = SecureAuthStateStore(
        values = SharedPreferencesKeyValueStore(context.applicationContext),
        codec = AesGcmValueCodec(AndroidKeystoreSecretKeyProvider()),
    )

    fun hasSession(): Boolean = state.loadSessionToken() != null

    override fun loadSessionToken(): String? = state.loadSessionToken()

    override fun saveSessionToken(token: String) = state.saveSessionToken(token)

    override fun clearSession() = state.clearSession()

    override fun savePendingOAuth(appState: String, codeVerifier: String) {
        state.savePendingOAuth(appState, codeVerifier)
    }

    override fun consumePendingOAuth(appState: String): PendingOAuth? =
        state.consumePendingOAuth(appState)
}

private class SharedPreferencesKeyValueStore(context: Context) : KeyValueStore {
    private val preferences = context.getSharedPreferences("secure_auth_state", Context.MODE_PRIVATE)

    override fun get(key: String): String? = preferences.getString(key, null)

    override fun put(key: String, value: String) {
        check(preferences.edit().putString(key, value).commit()) {
            "Could not persist encrypted app authentication state"
        }
    }

    override fun remove(key: String) {
        check(preferences.edit().remove(key).commit()) {
            "Could not clear app authentication state"
        }
    }
}

private class AndroidKeystoreSecretKeyProvider : SecretKeyProvider {
    override fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "com.javier.fitbitair.session.aesgcm.v1"
    }
}
