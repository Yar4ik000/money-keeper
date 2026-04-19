package com.moneykeeper.feature.auth.domain

import de.mkammerer.argon2.Argon2Factory
import javax.inject.Inject

class MasterKeyDerivation @Inject constructor() {

    fun derive(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        memoryKb: Int,
        parallelism: Int,
    ): ByteArray {
        val argon2 = Argon2Factory.createAdvanced(Argon2Factory.Argon2Types.ARGON2id, 16, KEY_SIZE_BYTES)
        return try {
            argon2.rawHash(iterations, memoryKb, parallelism, password, Charsets.UTF_8, salt)
        } catch (e: Throwable) {
            throw MasterKeyDerivationException("Не удалось вычислить master_key", e)
        }
    }

    companion object {
        const val KEY_SIZE_BYTES = 32
    }
}

class MasterKeyDerivationException(message: String, cause: Throwable) : Exception(message, cause)
