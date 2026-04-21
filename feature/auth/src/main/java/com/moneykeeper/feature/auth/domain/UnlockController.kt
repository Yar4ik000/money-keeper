package com.moneykeeper.feature.auth.domain

import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.fragment.app.FragmentActivity
import com.moneykeeper.core.database.DatabaseProvider
import com.moneykeeper.core.database.security.DatabaseKeyStorage
import com.moneykeeper.core.database.security.KeystoreMasterKeyWrapper
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
    private val pinVerifier: PinVerifier,
    private val keystoreWrapper: KeystoreMasterKeyWrapper,
    private val postUnlockCallbacks: @JvmSuppressWildcards Set<PostUnlockCallback>,
) {
    private fun notifyUnlocked() = postUnlockCallbacks.forEach { it.onUnlocked() }

    suspend fun unlockWithPassword(password: CharArray): UnlockResult = withContext(Dispatchers.Default) {
        val lockoutUntil = keyStorage.getEffectiveLockoutUntilMs()
        if (lockoutUntil > System.currentTimeMillis()) {
            password.fill(0.toChar())
            return@withContext UnlockResult.LockedOut(lockoutUntil)
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
            val count = keyStorage.recordFailedAttempt()
            val lockoutUntilMs = keyStorage.getLockoutUntilMs()
            return@withContext UnlockResult.WrongPassword(count, if (lockoutUntilMs > System.currentTimeMillis()) lockoutUntilMs else 0L)
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

    /**
     * v1.3+ unlock path: verify app PIN app-side, then unwrap master_key from Keystore.
     * Falls back gracefully if v2 isn't initialized (shouldn't happen post-migration).
     */
    suspend fun unlockWithPin(pin: CharArray): UnlockResult = withContext(Dispatchers.Default) {
        val lockoutUntil = keyStorage.getEffectiveLockoutUntilMs()
        if (lockoutUntil > System.currentTimeMillis()) {
            pin.fill(0.toChar())
            return@withContext UnlockResult.LockedOut(lockoutUntil)
        }

        if (!pinVerifier.verify(pin)) {
            pin.fill(0.toChar())
            val count = keyStorage.recordFailedAttempt()
            val newLockoutUntil = keyStorage.getLockoutUntilMs()
            return@withContext UnlockResult.WrongPassword(count, if (newLockoutUntil > System.currentTimeMillis()) newLockoutUntil else 0L)
        }
        pin.fill(0.toChar())
        keyStorage.resetFailedAttempts()

        val masterKey = try {
            keystoreWrapper.unwrap()
        } catch (e: Exception) {
            return@withContext UnlockResult.DataCorrupted("Ошибка Keystore: ${e.message}")
        }

        val enc = keyStorage.readEncryptedDbKey()
            ?: return@withContext UnlockResult.DataCorrupted("Ключ БД отсутствует")

        val dbKey = try {
            aesGcmDecrypt(enc.ciphertext, masterKey, enc.iv)
        } catch (_: AEADBadTagException) {
            masterKey.fill(0)
            return@withContext UnlockResult.DataCorrupted("Ключ БД повреждён")
        }

        masterKeyHolder.set(masterKey)
        masterKey.fill(0)

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

    sealed interface UnlockResult {
        data object Success : UnlockResult
        /** [failedCount] total consecutive failures; [lockoutUntilMs] > 0 if this failure triggered lockout. */
        data class WrongPassword(val failedCount: Int, val lockoutUntilMs: Long = 0L) : UnlockResult
        data object BiometricCancelled : UnlockResult
        data object BiometricStale : UnlockResult
        data class DataCorrupted(val message: String) : UnlockResult
        /** Too many failed attempts — retry allowed after [lockoutUntilMs]. */
        data class LockedOut(val lockoutUntilMs: Long) : UnlockResult
    }
}
