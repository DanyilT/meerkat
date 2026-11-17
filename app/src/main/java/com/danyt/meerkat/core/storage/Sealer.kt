package com.danyt.meerkat.core.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts data before it is written to disk, and decrypts it again. */
interface Sealer {
    /** Encrypts [plain]. The result can only be read back by [open] with the same key. */
    fun seal(plain: ByteArray): ByteArray

    /**
     * Decrypts data made by [seal]. Throws [GeneralSecurityException] when the data was changed, is damaged,
     * or was sealed with another key.
     */
    fun open(sealed: ByteArray): ByteArray
}

/**
 * AES-256 in GCM mode. Every [seal] call uses a fresh random nonce, stored in front of the encrypted data.
 * GCM also detects any change to the data.
 *
 * @param key gives the key. Called on every use, so a Keystore key is looked up only when needed.
 */
class AesGcmSealer(private val key: () -> SecretKey) : Sealer {

    override fun seal(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // The cipher picks the nonce itself. The Android Keystore insists on that, to rule out reuse.
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val nonce = cipher.iv
        check(nonce.size == NONCE_BYTES) { "Unexpected nonce length" }
        return nonce + cipher.doFinal(plain)
    }

    override fun open(sealed: ByteArray): ByteArray {
        if (sealed.size < NONCE_BYTES + TAG_BITS / 8) throw GeneralSecurityException("Sealed data is too short")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, NONCE_BYTES))
        return cipher.doFinal(sealed, NONCE_BYTES, sealed.size - NONCE_BYTES)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
    }
}

/**
 * Returns the AES key stored in the Android Keystore under [alias], creating it on first use.
 *
 * The key never leaves the Keystore (hardware-backed where the device supports it) and cannot be exported, so
 * the files it protects are unreadable outside this app on this device. Clearing the app's data or resetting
 * the device destroys the key, and the data with it. That is intended.
 */
fun keystoreKey(alias: String): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
    val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build()
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
        init(spec)
        generateKey()
    }
}

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
