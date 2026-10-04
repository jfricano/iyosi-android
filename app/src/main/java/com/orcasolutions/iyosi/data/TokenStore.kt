package com.orcasolutions.iyosi.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keeps the bearer token encrypted at rest with an Android Keystore AES key. */
class TokenStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("auth", Context.MODE_PRIVATE)

    fun read(): String? = try {
        val iv = prefs.getString("iv", null)
        val saved = prefs.getString("token", null)
        if (iv == null || saved == null) null
        else {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(saved, Base64.NO_WRAP)), Charsets.UTF_8)
        }
    } catch (_: Exception) {
        prefs.edit().clear().apply()
        null
    }

    fun save(token: String?) {
        if (token == null) {
            prefs.edit().clear().apply()
            return
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val bytes = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
            prefs.edit()
                .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString("token", Base64.encodeToString(bytes, Base64.NO_WRAP))
                .apply()
        } catch (_: Exception) {
            // The current session keeps working; the next launch asks for sign-in again.
            prefs.edit().clear().apply()
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ALIAS = "iyosi-access-token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
