package com.kevan.hangry.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Holds the user's own OpenRouter API key, encrypted with an AES-256-GCM key generated inside
 * the Android Keystore (hardware-backed where the device supports it, and never exportable in
 * either case). Deliberately not androidx.security:security-crypto - Google deprecated that
 * library at its 1.1.0 release without an in-place replacement - and deliberately not the Room
 * database, which isn't encrypted at rest and has no business holding a credential.
 *
 * Never synced anywhere; only used to build the Authorization header on a request the user
 * explicitly triggered.
 */
class SecureKeyStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private val secretKey: SecretKey
        get() = (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: generateKey()

    private fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun getApiKey(): String? {
        val ivB64 = prefs.getString(PREF_IV, null) ?: return null
        val ciphertextB64 = prefs.getString(PREF_CIPHERTEXT, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            val plaintext = cipher.doFinal(Base64.decode(ciphertextB64, Base64.NO_WRAP))
            String(plaintext, Charsets.UTF_8)
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun setApiKey(key: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val ciphertext = cipher.doFinal(key.trim().toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(PREF_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(PREF_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .apply()
    }

    fun clearApiKey() {
        prefs.edit().remove(PREF_IV).remove(PREF_CIPHERTEXT).apply()
    }

    companion object {
        private const val PREFS_NAME = "hangry_secure_prefs"
        private const val PREF_IV = "openrouter_api_key_iv"
        private const val PREF_CIPHERTEXT = "openrouter_api_key_ciphertext"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "hangry_openrouter_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
