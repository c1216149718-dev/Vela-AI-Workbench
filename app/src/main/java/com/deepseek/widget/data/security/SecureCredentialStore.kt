package com.deepseek.widget.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureCredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences("vela_secure_credentials", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun put(reference: String, value: String) {
        require(reference.isNotBlank())
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
        check(preferences.edit().putString(reference, payload).commit())
        check(get(reference) == value)
    }

    fun get(reference: String): String? {
        val payload = preferences.getString(reference, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(payload, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, IV_SIZE)
            val cipherText = bytes.copyOfRange(IV_SIZE, bytes.size)
            Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
                String(doFinal(cipherText), Charsets.UTF_8)
            }
        }.getOrNull()
    }

    fun remove(reference: String) {
        preferences.edit().remove(reference).apply()
    }

    fun contains(reference: String): Boolean = preferences.contains(reference)

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "vela_provider_credentials_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
    }
}
