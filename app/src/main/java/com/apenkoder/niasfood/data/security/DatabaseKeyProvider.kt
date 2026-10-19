package com.apenkoder.niasfood.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Creates a random database passphrase once and stores it only in wrapped (AES-GCM encrypted) form.
 * The wrapping key lives in the Android Keystore and cannot be exported.
 */
class DatabaseKeyProvider(private val context: Context) {

    fun passphrase(): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_WRAPPED, null)
        val raw: ByteArray = if (stored != null) {
            unwrap(Base64.decode(stored, Base64.NO_WRAP))
        } else {
            val fresh = ByteArray(32)
            SecureRandom().nextBytes(fresh)
            prefs.edit().putString(KEY_WRAPPED, Base64.encodeToString(wrap(fresh), Base64.NO_WRAP)).commit()
            fresh
        }
        // Hex-encode so the passphrase is plain ASCII for SQLCipher.
        return raw.joinToString("") { b -> "%02x".format(b) }.toByteArray(Charsets.US_ASCII)
    }

    private fun wrappingKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = ks.getKey(ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun wrap(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        return cipher.iv + cipher.doFinal(plain)
    }

    private fun unwrap(blob: ByteArray): ByteArray {
        val iv = blob.copyOfRange(0, IV_LENGTH)
        val body = blob.copyOfRange(IV_LENGTH, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(body)
    }

    private companion object {
        const val PREFS = "nias_key_store"
        const val KEY_WRAPPED = "wrapped_db_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "nias_db_wrap_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
    }
}
