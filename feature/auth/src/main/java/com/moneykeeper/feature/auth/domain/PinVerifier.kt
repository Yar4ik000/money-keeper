package com.moneykeeper.feature.auth.domain

import com.moneykeeper.core.database.security.DatabaseKeyStorage
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores and verifies the app PIN (exactly 4 digits).
 *
 * Argon2id params are stored alongside each hash so they can be bumped in future releases
 * without invalidating existing PINs. Backward-compat: hashes written before param storage
 * was introduced (app < v1.3.1) default to the old lightweight values (1 / 1024 / 1) at
 * verify time, but any subsequent setPin() call will upgrade to the current stronger params.
 *
 * Defense-in-depth note: the 4-digit PIN space is small (10 000 combinations). Security
 * here relies on: (1) the Argon2id cost making each guess expensive, (2) the app-side
 * rate-limiter enforcing exponential back-off after 5 failures, and (3) the actual db_key
 * being guarded by a hardware-backed Keystore key that never leaves the device.
 */
@Singleton
class PinVerifier @Inject constructor(
    private val derivation: MasterKeyDerivation,
    private val keyStorage: DatabaseKeyStorage,
) {
    companion object {
        private const val ITERATIONS  = 3
        private const val MEMORY_KB   = 16_384   // 16 MB — ~250 ms on mid-range device
        private const val PARALLELISM = 1
        private const val SALT_SIZE   = 16
        const val PIN_LENGTH          = 4
    }

    private val rng by lazy { SecureRandom() }

    fun setPin(pin: CharArray) {
        val salt = ByteArray(SALT_SIZE).also { rng.nextBytes(it) }
        val hash = derive(pin, salt, ITERATIONS, MEMORY_KB, PARALLELISM)
        keyStorage.writePinHash(hash, salt, ITERATIONS, MEMORY_KB, PARALLELISM)
    }

    fun verify(pin: CharArray): Boolean {
        val data = keyStorage.readPinData() ?: return false
        val hash = derive(pin, data.salt, data.iterations, data.memoryKb, data.parallelism)
        return try {
            MessageDigest.isEqual(hash, data.hash)
        } finally {
            hash.fill(0)
        }
    }

    private fun derive(pin: CharArray, salt: ByteArray, iterations: Int, memoryKb: Int, parallelism: Int): ByteArray =
        derivation.derive(pin, salt, iterations, memoryKb, parallelism)
}
