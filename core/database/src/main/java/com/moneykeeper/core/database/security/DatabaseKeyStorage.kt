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
    private val rng by lazy { SecureRandom() }

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
        val fresh = ByteArray(SALT_SIZE).also { rng.nextBytes(it) }
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

    /**
     * Like [getLockoutUntilMs] but also guards against the user winding the system clock back.
     * If the current time is behind the timestamp recorded when the lockout was imposed,
     * the clock was manipulated — return [Long.MAX_VALUE] to keep the lockout indefinitely
     * until the clock is corrected or the lockout naturally expires.
     */
    fun getEffectiveLockoutUntilMs(): Long {
        val setAt = prefs.getLong(KEY_LOCKOUT_SET_AT_MS, 0L)
        val until = prefs.getLong(KEY_LOCKOUT_UNTIL_MS, 0L)
        if (setAt > 0L && System.currentTimeMillis() < setAt) return Long.MAX_VALUE
        return until
    }

    fun recordFailedAttempt(): Int {
        val count = getFailedAttempts() + 1
        val now = System.currentTimeMillis()
        val lockoutMs = if (count >= LOCKOUT_THRESHOLD) now + lockoutDurationMs(count) else 0L
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, count)
            .putLong(KEY_LOCKOUT_UNTIL_MS, lockoutMs)
            .putLong(KEY_LOCKOUT_SET_AT_MS, if (count >= LOCKOUT_THRESHOLD) now else 0L)
            .apply()
        return count
    }

    fun resetFailedAttempts() {
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_MS, 0L)
            .putLong(KEY_LOCKOUT_SET_AT_MS, 0L)
            .apply()
    }

    private fun lockoutDurationMs(failedCount: Int): Long {
        // Exponential backoff: 30s, 60s, 120s, …, capped at 24 h
        val exponent = (failedCount - LOCKOUT_THRESHOLD).coerceAtLeast(0)
        val seconds = minOf(30L * (1L shl exponent), 86_400L)
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

    // --- v2: Keystore-wrapped master_key (hardware-backed, no password required) ---

    fun hasWrappedMkV2(): Boolean = prefs.contains(KEY_WRAPPED_MK_V2)

    fun writeWrappedMkV2(ciphertext: ByteArray, iv: ByteArray) {
        prefs.edit()
            .putString(KEY_WRAPPED_MK_V2,    Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(KEY_WRAPPED_MK_IV_V2, Base64.encodeToString(iv, Base64.NO_WRAP))
            .apply()
    }

    fun readWrappedMkV2(): EncryptedDbKey? {
        val ct = prefs.getString(KEY_WRAPPED_MK_V2,    null) ?: return null
        val iv = prefs.getString(KEY_WRAPPED_MK_IV_V2, null) ?: return null
        return EncryptedDbKey(Base64.decode(ct, Base64.NO_WRAP), Base64.decode(iv, Base64.NO_WRAP))
    }

    // --- v2: app PIN (4-6 digits, verified app-side, Argon2id hash) ---

    fun hasPinHash(): Boolean = prefs.contains(KEY_PIN_HASH)

    fun writePinHash(hash: ByteArray, salt: ByteArray, iterations: Int, memoryKb: Int, parallelism: Int) {
        prefs.edit()
            .putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putInt(KEY_PIN_KDF_ITERATIONS,  iterations)
            .putInt(KEY_PIN_KDF_MEMORY,      memoryKb)
            .putInt(KEY_PIN_KDF_PARALLELISM, parallelism)
            .apply()
    }

    fun readPinData(): PinData? {
        val h = prefs.getString(KEY_PIN_HASH, null) ?: return null
        val s = prefs.getString(KEY_PIN_SALT, null) ?: return null
        return PinData(
            hash        = Base64.decode(h, Base64.NO_WRAP),
            salt        = Base64.decode(s, Base64.NO_WRAP),
            // Backward-compat: devices that set PIN before params were stored use old values.
            iterations  = prefs.getInt(KEY_PIN_KDF_ITERATIONS,  1),
            memoryKb    = prefs.getInt(KEY_PIN_KDF_MEMORY,      1024),
            parallelism = prefs.getInt(KEY_PIN_KDF_PARALLELISM, 1),
        )
    }

    /** v2 is fully initialised when both Keystore-wrapped master key AND pin hash are present. */
    fun isV2Initialized(): Boolean = hasWrappedMkV2() && hasPinHash()

    data class PinData(
        val hash: ByteArray,
        val salt: ByteArray,
        val iterations: Int,
        val memoryKb: Int,
        val parallelism: Int,
    )

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
        const val KEY_FAILED_ATTEMPTS    = "failed_unlock_attempts_v1"
        const val KEY_LOCKOUT_UNTIL_MS   = "lockout_until_ms_v1"
        const val KEY_LOCKOUT_SET_AT_MS  = "lockout_set_at_ms_v1"
        // v2 keys — added in v1.3.0
        const val KEY_WRAPPED_MK_V2      = "wrapped_mk_v2"
        const val KEY_WRAPPED_MK_IV_V2   = "wrapped_mk_iv_v2"
        const val KEY_PIN_HASH           = "pin_hash_v1"
        const val KEY_PIN_SALT           = "pin_salt_v1"
        const val KEY_PIN_KDF_ITERATIONS = "pin_kdf_iter_v1"
        const val KEY_PIN_KDF_MEMORY     = "pin_kdf_mem_v1"
        const val KEY_PIN_KDF_PARALLELISM= "pin_kdf_parallel_v1"
        const val LOCKOUT_THRESHOLD      = 5   // start blocking after 5 failures
    }
}
