package com.moneykeeper.feature.auth.domain

import com.moneykeeper.core.database.DatabaseProvider
import com.moneykeeper.core.database.security.DatabaseKeyStorage
import com.moneykeeper.core.database.security.KeystoreMasterKeyWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.crypto.AEADBadTagException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot migration: v1.2 (master-password model) → v1.3 (PIN + Keystore model).
 *
 * Called when the app detects v1 credentials (encrypted_db_key) but no v2 Keystore wrap.
 * Asks the user for the old master-password once, then stores master_key in the Keystore.
 * After this, the PIN gate replaces the password for daily use.
 */
@Singleton
class MigrationToPin @Inject constructor(
    private val keyStorage: DatabaseKeyStorage,
    private val derivation: MasterKeyDerivation,
    private val masterKeyHolder: MasterKeyHolder,
    private val keystoreWrapper: KeystoreMasterKeyWrapper,
    private val databaseProvider: DatabaseProvider,
    private val postUnlockCallbacks: @JvmSuppressWildcards Set<PostUnlockCallback>,
) {
    suspend fun migrate(password: CharArray): Result = withContext(Dispatchers.Default) {
        val salt = keyStorage.readOrCreateKdfSalt()
        val params = keyStorage.readKdfParams()
        val masterKey = try {
            derivation.derive(password, salt, params.iterations, params.memoryKb, params.parallelism)
        } finally {
            password.fill(0.toChar())
        }

        val enc = keyStorage.readEncryptedDbKey()
            ?: run { masterKey.fill(0); return@withContext Result.DataCorrupted("Ключ БД отсутствует") }

        val dbKey = try {
            aesGcmDecrypt(enc.ciphertext, masterKey, enc.iv)
        } catch (_: AEADBadTagException) {
            masterKey.fill(0)
            return@withContext Result.WrongPassword
        }

        try {
            keystoreWrapper.wrap(masterKey)
            databaseProvider.initialize(dbKey)
            masterKeyHolder.set(masterKey)
        } catch (e: Exception) {
            return@withContext Result.Error(e.message ?: "Ошибка миграции")
        } finally {
            masterKey.fill(0)
            dbKey.fill(0)
        }

        postUnlockCallbacks.forEach { it.onUnlocked() }
        Result.ReadyForPinSetup
    }

    sealed interface Result {
        data object WrongPassword : Result
        data object ReadyForPinSetup : Result
        data class DataCorrupted(val message: String) : Result
        data class Error(val message: String) : Result
    }
}
