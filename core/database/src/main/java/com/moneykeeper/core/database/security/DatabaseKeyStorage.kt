// security-crypto в maintenance-режиме (§2.10). Для v1 приемлемо; план миграции на
// ручной Keystore-wrap описан в §2.10 и помечен TODO в теле класса.
@file:Suppress("DEPRECATION")

package com.moneykeeper.core.database.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Хранит зашифрованный db_key и KDF-параметры в EncryptedSharedPreferences.
 *
 * Архитектура ключей (детали — §2.10):
 *   мастер-пароль → Argon2id → master_key (в памяти)
 *   master_key + AES-GCM → encrypted_db_key (здесь, в prefs)
 *   db_key → SQLCipher открывает AppDatabase
 *
 * Этот класс знает только о хранении — Argon2 и работа с паролем в MasterKeyProvider (§3).
 */
@Singleton
class DatabaseKeyStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // TODO(§2.10): заменить на ручной AES-GCM-wrap через Keystore когда security-crypto
    //  окончательно перестанет получать обновления. API внешнего класса при этом не меняется.
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun isInitialized(): Boolean = prefs.contains(KEY_ENCRYPTED_DB_KEY)

    fun readOrCreateKdfSalt(): ByteArray {
        prefs.getString(KEY_KDF_SALT, null)
            ?.let { return Base64.decode(it, Base64.NO_WRAP) }
        val fresh = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_KDF_SALT, Base64.encodeToString(fresh, Base64.NO_WRAP)).apply()
        return fresh
    }

    fun readKdfParams(): KdfParams = KdfParams(
        iterations  = prefs.getInt(KEY_KDF_ITERATIONS,  KdfParams.DEFAULT_ITERATIONS),
        memoryKb    = prefs.getInt(KEY_KDF_MEMORY,      KdfParams.DEFAULT_MEMORY_KB),
        parallelism = prefs.getInt(KEY_KDF_PARALLELISM, KdfParams.DEFAULT_PARALLELISM),
    )

    fun writeKdfParams(p: KdfParams) {
        prefs.edit()
            .putInt(KEY_KDF_ITERATIONS,  p.iterations)
            .putInt(KEY_KDF_MEMORY,      p.memoryKb)
            .putInt(KEY_KDF_PARALLELISM, p.parallelism)
            .apply()
    }

    fun writeEncryptedDbKey(ciphertext: ByteArray, iv: ByteArray) {
        prefs.edit()
            .putString(KEY_ENCRYPTED_DB_KEY, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(KEY_DB_KEY_IV,        Base64.encodeToString(iv, Base64.NO_WRAP))
            .apply()
    }

    fun readEncryptedDbKey(): EncryptedDbKey? {
        val ct = prefs.getString(KEY_ENCRYPTED_DB_KEY, null) ?: return null
        val iv = prefs.getString(KEY_DB_KEY_IV, null) ?: return null
        return EncryptedDbKey(
            ciphertext = Base64.decode(ct, Base64.NO_WRAP),
            iv         = Base64.decode(iv, Base64.NO_WRAP),
        )
    }

    fun wipe() = prefs.edit().clear().apply()

    // --- Rate-limiting for password unlock ---

    fun getFailedAttempts(): Int = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)

    fun getLockoutUntilMs(): Long = prefs.getLong(KEY_LOCKOUT_UNTIL_MS, 0L)

    fun recordFailedAttempt(): Int {
        val count = getFailedAttempts() + 1
        val lockoutMs = when {
            count >= WIPE_THRESHOLD -> {
                wipe()
                return count
            }
            count >= LOCKOUT_THRESHOLD -> System.currentTimeMillis() + lockoutDurationMs(count)
            else -> 0L
        }
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, count)
            .putLong(KEY_LOCKOUT_UNTIL_MS, lockoutMs)
            .apply()
        return count
    }

    fun resetFailedAttempts() {
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_MS, 0L)
            .apply()
    }

    private fun lockoutDurationMs(failedCount: Int): Long {
        // Exponential backoff starting at 30 s, capped at 1 h
        val exponent = (failedCount - LOCKOUT_THRESHOLD).coerceAtLeast(0)
        val seconds = minOf(30L * (1L shl exponent), 3_600L)
        return seconds * 1_000L
    }

    // --- Biometric wrap (§3.7) ---

    fun writeBiometricWrap(ciphertext: ByteArray, iv: ByteArray) {
        prefs.edit()
            .putString(KEY_BIO_WRAPPED_KEY, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(KEY_BIO_WRAP_IV,     Base64.encodeToString(iv, Base64.NO_WRAP))
            .apply()
    }

    fun readBiometricWrap(): EncryptedDbKey? {
        val ct = prefs.getString(KEY_BIO_WRAPPED_KEY, null) ?: return null
        val iv = prefs.getString(KEY_BIO_WRAP_IV, null) ?: return null
        return EncryptedDbKey(
            ciphertext = Base64.decode(ct, Base64.NO_WRAP),
            iv         = Base64.decode(iv, Base64.NO_WRAP),
        )
    }

    fun deleteBiometricWrap() {
        prefs.edit()
            .remove(KEY_BIO_WRAPPED_KEY)
            .remove(KEY_BIO_WRAP_IV)
            .apply()
    }

    fun hasBiometricWrap(): Boolean = prefs.contains(KEY_BIO_WRAPPED_KEY)

    data class EncryptedDbKey(val ciphertext: ByteArray, val iv: ByteArray)

    data class KdfParams(val iterations: Int, val memoryKb: Int, val parallelism: Int) {
        companion object {
            // 32 MB — консервативный старт: ~250-400 ms на mid-range Android, не вызывает OOM
            // на устройствах с 1-2 GB RAM (minSdk 26). При необходимости повышается с bump'ом
            // (старые параметры сохранены в prefs — совместимость не ломается).
            const val DEFAULT_ITERATIONS  = 3
            const val DEFAULT_MEMORY_KB   = 32_768
            const val DEFAULT_PARALLELISM = 2
        }
    }

    private companion object {
        const val PREFS_FILE           = "money_keeper_secure_prefs"
        const val KEY_ENCRYPTED_DB_KEY = "encrypted_db_key_v1"
        const val KEY_DB_KEY_IV        = "db_key_iv_v1"
        const val KEY_KDF_SALT         = "kdf_salt_v1"
        const val KEY_KDF_ITERATIONS   = "kdf_iter_v1"
        const val KEY_KDF_MEMORY       = "kdf_mem_v1"
        const val KEY_KDF_PARALLELISM  = "kdf_parallel_v1"
        const val SALT_SIZE            = 16
        const val KEY_BIO_WRAPPED_KEY  = "bio_wrapped_master_key_v1"
        const val KEY_BIO_WRAP_IV      = "bio_wrap_iv_v1"
        const val KEY_FAILED_ATTEMPTS  = "failed_unlock_attempts_v1"
        const val KEY_LOCKOUT_UNTIL_MS = "lockout_until_ms_v1"
        const val LOCKOUT_THRESHOLD    = 5   // start blocking after 5 failures
        const val WIPE_THRESHOLD       = 10  // wipe all data after 10 consecutive failures
    }
}
