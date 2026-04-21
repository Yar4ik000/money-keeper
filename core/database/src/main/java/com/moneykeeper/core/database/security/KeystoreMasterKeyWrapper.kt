package com.moneykeeper.core.database.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps / unwraps the app's master_key via a hardware-backed Android Keystore AES key.
 *
 * The Keystore key has setUserAuthenticationRequired(false) — access is controlled by
 * the app's PIN rate-limiter, not the system credential gate. This lets the app have its
 * own 4-digit PIN separate from the device screen-lock PIN.
 *
 * On devices with a Secure Element (Titan M / similar), the key is placed in StrongBox
 * for maximum hardware isolation. Devices without StrongBox fall back to the TEE silently.
 *
 * The wrapped master_key is stored in EncryptedSharedPreferences (via DatabaseKeyStorage).
 */
@Singleton
class KeystoreMasterKeyWrapper @Inject constructor(
    private val keyStorage: DatabaseKeyStorage,
) {
    companion object {
        private const val KEYSTORE  = "AndroidKeyStore"
        private const val KEY_ALIAS = "moneykeeper_mk_wrap_v2"
        private const val CIPHER    = "AES/GCM/NoPadding"
        private const val GCM_TAG   = 128
    }

    fun isInitialized(): Boolean = keyStorage.hasWrappedMkV2()

    fun wrap(masterKey: ByteArray) {
        ensureKeyExists()
        val cipher = encryptCipher()
        val ciphertext = cipher.doFinal(masterKey)
        keyStorage.writeWrappedMkV2(ciphertext, cipher.iv)
    }

    fun unwrap(): ByteArray {
        val wrapped = keyStorage.readWrappedMkV2()
            ?: error("Keystore key '$KEY_ALIAS' not found — app must complete v1.3 migration before unlock.")
        val cipher = decryptCipher(wrapped.iv)
        return cipher.doFinal(wrapped.ciphertext)
    }

    fun deleteKey() {
        runCatching {
            val ks = KeyStore.getInstance(KEYSTORE).also { it.load(null) }
            if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS)
        }
    }

    private fun encryptCipher(): Cipher {
        val key = keystoreKey()
        return Cipher.getInstance(CIPHER).also { it.init(Cipher.ENCRYPT_MODE, key) }
    }

    private fun decryptCipher(iv: ByteArray): Cipher {
        val key = keystoreKey()
        return Cipher.getInstance(CIPHER).also {
            it.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG, iv))
        }
    }

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).also { it.load(null) }
        return ks.getKey(KEY_ALIAS, null) as SecretKey
    }

    private fun ensureKeyExists() {
        val ks = KeyStore.getInstance(KEYSTORE).also { it.load(null) }
        if (ks.containsAlias(KEY_ALIAS)) return

        fun baseSpec() = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)

        // Prefer StrongBox (Secure Element) on API 28+ devices that support it.
        // Fall back to TEE silently on older devices or those without a Secure Element.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val spec = baseSpec().setIsStrongBoxBacked(true).build()
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
                    .also { it.init(spec) }
                    .generateKey()
                return
            } catch (_: android.security.keystore.StrongBoxUnavailableException) {
                // No Secure Element on this device — fall through to TEE.
            } catch (_: Exception) {
                // Unexpected error (e.g., StrongBox busy) — fall through to TEE.
            }
        }

        val spec = baseSpec().build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            .also { it.init(spec) }
            .generateKey()
    }
}
