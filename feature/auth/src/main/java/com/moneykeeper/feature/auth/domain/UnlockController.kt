package com.moneykeeper.feature.auth.domain

import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.fragment.app.FragmentActivity
import com.moneykeeper.core.database.DatabaseProvider
import com.moneykeeper.core.database.security.DatabaseKeyStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.crypto.AEADBadTagException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnlockController @Inject constructor(
    private val keyStorage: DatabaseKeyStorage,
    private val derivation: MasterKeyDerivation,
    private val masterKeyHolder: MasterKeyHolder,
    private val databaseProvider: DatabaseProvider,
    private val biometric: BiometricAuthenticator,
    private val postUnlockCallbacks: @JvmSuppressWildcards Set<PostUnlockCallback>,
) {
    private fun notifyUnlocked() = postUnlockCallbacks.forEach { it.onUnlocked() }

    suspend fun unlockWithPassword(password: CharArray): UnlockResult = withContext(Dispatchers.Default) {
        val lockoutUntil = keyStorage.getLockoutUntilMs()
        val now = System.currentTimeMillis()
        if (lockoutUntil > now) {
            password.fill(0.toChar())
            val secondsLeft = (lockoutUntil - now + 999) / 1000
            return@withContext UnlockResult.LockedOut(secondsLeft)
        }

        val derivedKey = try {
            val salt = keyStorage.readOrCreateKdfSalt()
            val params = keyStorage.readKdfParams()
            derivation.derive(password, salt, params.iterations, params.memoryKb, params.parallelism)
        } finally {
            password.fill(0.toChar())
        }

        val enc = keyStorage.readEncryptedDbKey()
            ?: return@withContext UnlockResult.DataCorrupted("Ключ БД отсутствует, хотя пароль задан")

        val dbKey = try {
            aesGcmDecrypt(enc.ciphertext, derivedKey, enc.iv)
        } catch (e: AEADBadTagException) {
            derivedKey.fill(0)
            keyStorage.recordFailedAttempt()
            return@withContext UnlockResult.WrongPassword
        }

        keyStorage.resetFailedAttempts()
        masterKeyHolder.set(derivedKey)
        derivedKey.fill(0)

        try {
            databaseProvider.initialize(dbKey)
        } catch (e: Exception) {
            return@withContext UnlockResult.DataCorrupted(e.message ?: "Ошибка открытия БД")
        } finally {
            dbKey.fill(0)
        }
        notifyUnlocked()
        UnlockResult.Success
    }

    suspend fun unlockWithBiometric(activity: FragmentActivity): UnlockResult {
        val masterKey = try {
            biometric.unwrapMasterKey(activity)
                ?: return UnlockResult.BiometricCancelled
        } catch (e: KeyPermanentlyInvalidatedException) {
            return UnlockResult.BiometricStale
        }

        val enc = keyStorage.readEncryptedDbKey()
            ?: return UnlockResult.DataCorrupted("Ключ БД отсутствует")

        val dbKey = try {
            aesGcmDecrypt(enc.ciphertext, masterKey, enc.iv)
        } catch (e: AEADBadTagException) {
            masterKey.fill(0)
            return UnlockResult.BiometricStale
        }

        masterKeyHolder.set(masterKey)
        masterKey.fill(0)

        try {
            databaseProvider.initialize(dbKey)
        } catch (e: Exception) {
            return UnlockResult.DataCorrupted(e.message ?: "Ошибка открытия БД")
        } finally {
            dbKey.fill(0)
        }
        notifyUnlocked()
        return UnlockResult.Success
    }

    sealed interface UnlockResult {
        data object Success : UnlockResult
        data object WrongPassword : UnlockResult
        data object BiometricCancelled : UnlockResult
        data object BiometricStale : UnlockResult
        data class DataCorrupted(val message: String) : UnlockResult
        /** Too many failed attempts — [secondsRemaining] until retry is allowed. */
        data class LockedOut(val secondsRemaining: Long) : UnlockResult
    }
}
