package com.moneykeeper.feature.auth.domain

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.moneykeeper.core.database.security.DatabaseKeyStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val KEY_ALIAS = "moneykeeper_biometric_wrap_key"
private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val TAG_LEN = 128

@Singleton
class BiometricEnrollment @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyStorage: DatabaseKeyStorage,
    private val masterKeyHolder: MasterKeyHolder,
) {
    fun isEnrolled(): Boolean = keyStorage.hasBiometricWrap()

    fun isAvailable(): Boolean = isBiometricStrongAvailable()

    suspend fun enroll(activity: FragmentActivity): EnrollResult {
        if (!isBiometricStrongAvailable()) return EnrollResult.Unavailable
        if (!isEnrolledInSystem()) return EnrollResult.NotEnrolledInSystem

        val masterKey = try {
            masterKeyHolder.require()
        } catch (e: IllegalStateException) {
            return EnrollResult.NotUnlocked
        }

        return try {
            val cipher = createEncryptCipher() ?: return EnrollResult.Unavailable
            val result = showPromptForEnroll(activity, cipher)
            if (result == null) {
                masterKey.fill(0)
                return EnrollResult.Cancelled
            }
            val wrapped = result.doFinal(masterKey)
            val iv = result.iv
            masterKey.fill(0)
            keyStorage.writeBiometricWrap(wrapped, iv)
            EnrollResult.Success
        } catch (e: KeyPermanentlyInvalidatedException) {
            masterKey.fill(0)
            deleteKey()
            EnrollResult.Error("Биометрический ключ инвалидирован, попробуйте снова")
        } catch (e: Exception) {
            masterKey.fill(0)
            EnrollResult.Error(e.message ?: "Неизвестная ошибка")
        }
    }

    fun disable() {
        keyStorage.deleteBiometricWrap()
        deleteKey()
    }

    private fun isBiometricStrongAvailable(): Boolean =
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS

    private fun isEnrolledInSystem(): Boolean =
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) !=
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED

    private fun createEncryptCipher(): Cipher? {
        ensureKeyExists()
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).also { it.load(null) }
            val key = keyStore.getKey(KEY_ALIAS, null) as? javax.crypto.SecretKey ?: return null
            Cipher.getInstance(TRANSFORMATION).also {
                it.init(Cipher.ENCRYPT_MODE, key)
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            deleteKey()
            null
        }
    }

    private fun ensureKeyExists() {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).also { it.load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) return
        val builder = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        }
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(builder.build())
            generateKey()
        }
    }

    private fun deleteKey() {
        try {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).also { it.load(null) }
            if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS)
        } catch (_: Exception) {}
    }

    private suspend fun showPromptForEnroll(activity: FragmentActivity, cipher: Cipher): Cipher? =
        suspendCancellableCoroutine { cont ->
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (cont.isActive) cont.resume(result.cryptoObject?.cipher)
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (cont.isActive) cont.resume(null)
                    }
                    override fun onAuthenticationFailed() { /* retry, не завершаем */ }
                }
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(com.moneykeeper.feature.auth.R.string.biometric_enroll_title))
                .setNegativeButtonText(context.getString(com.moneykeeper.feature.auth.R.string.biometric_cancel))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
            prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        }

    sealed interface EnrollResult {
        data object Success : EnrollResult
        data object Cancelled : EnrollResult
        data object Unavailable : EnrollResult
        data object NotEnrolledInSystem : EnrollResult
        data object NotUnlocked : EnrollResult
        data class Error(val message: String) : EnrollResult
    }
}
