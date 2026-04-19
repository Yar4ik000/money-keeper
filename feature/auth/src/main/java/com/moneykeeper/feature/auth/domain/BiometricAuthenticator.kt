package com.moneykeeper.feature.auth.domain

import android.content.Context
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.moneykeeper.core.database.security.DatabaseKeyStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val KEY_ALIAS = "moneykeeper_biometric_wrap_key"
private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val TAG_LEN = 128

@Singleton
class BiometricAuthenticator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyStorage: DatabaseKeyStorage,
) {
    fun isAvailable(): Boolean =
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Показывает BiometricPrompt и возвращает расшифрованный master_key, или null если отменено.
     * Бросает [KeyPermanentlyInvalidatedException] если ключ Keystore инвалидирован.
     */
    suspend fun unwrapMasterKey(activity: FragmentActivity): ByteArray? {
        val wrap = keyStorage.readBiometricWrap() ?: return null
        val cipher = createDecryptCipher(wrap.iv) ?: return null
        val resultCipher = showPromptForUnlock(activity, cipher) ?: return null
        return resultCipher.doFinal(wrap.ciphertext)
    }

    private fun createDecryptCipher(iv: ByteArray): Cipher? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).also { it.load(null) }
            val key = keyStore.getKey(KEY_ALIAS, null) as? javax.crypto.SecretKey ?: return null
            Cipher.getInstance(TRANSFORMATION).also {
                it.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LEN, iv))
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun showPromptForUnlock(activity: FragmentActivity, cipher: Cipher): Cipher? =
        suspendCancellableCoroutine { cont ->
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        cont.resume(result.cryptoObject?.cipher)
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        cont.resume(null)
                    }
                    override fun onAuthenticationFailed() {}
                }
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(com.moneykeeper.feature.auth.R.string.biometric_unlock_title))
                .setNegativeButtonText(context.getString(com.moneykeeper.feature.auth.R.string.biometric_use_password))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
            prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        }
}
