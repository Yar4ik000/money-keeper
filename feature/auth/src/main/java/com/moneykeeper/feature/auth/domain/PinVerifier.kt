package com.moneykeeper.feature.auth.domain

import com.moneykeeper.core.database.security.DatabaseKeyStorage
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores and verifies the app PIN (4-6 digits).
 *
 * Uses Argon2id with lightweight params — the actual security comes from the hardware-backed
 * Keystore key that guards master_key; the rate-limiter prevents brute force on the hash.
 */
@Singleton
class PinVerifier @Inject constructor(
    private val derivation: MasterKeyDerivation,
    private val keyStorage: DatabaseKeyStorage,
) {
    companion object {
        private const val ITERATIONS   = 1
        private const val MEMORY_KB    = 1024
        private const val PARALLELISM  = 1
        private const val SALT_SIZE    = 16
        const val MIN_LENGTH           = 4
        const val MAX_LENGTH           = 6
    }

    fun setPin(pin: CharArray) {
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt)
        keyStorage.writePinHash(hash, salt)
    }

    fun verify(pin: CharArray): Boolean {
        val data = keyStorage.readPinData() ?: return false
        val hash = derive(pin, data.salt)
        return try {
            MessageDigest.isEqual(hash, data.hash)
        } finally {
            hash.fill(0)
        }
    }

    private fun derive(pin: CharArray, salt: ByteArray): ByteArray =
        derivation.derive(pin, salt, ITERATIONS, MEMORY_KB, PARALLELISM)
}
