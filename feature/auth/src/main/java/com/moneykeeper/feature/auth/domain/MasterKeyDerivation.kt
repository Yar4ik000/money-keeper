package com.moneykeeper.feature.auth.domain

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import javax.inject.Inject

class MasterKeyDerivation @Inject constructor() {

    fun derive(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        memoryKb: Int,
        parallelism: Int,
    ): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withSalt(salt)
            .withParallelism(parallelism)
            .withMemoryAsKB(memoryKb)
            .withIterations(iterations)
            .build()
        return try {
            val generator = Argon2BytesGenerator()
            generator.init(params)
            val output = ByteArray(KEY_SIZE_BYTES)
            generator.generateBytes(password, output)
            output
        } catch (e: Throwable) {
            throw MasterKeyDerivationException("Не удалось вычислить master_key", e)
        }
    }

    companion object {
        const val KEY_SIZE_BYTES = 32
    }
}

class MasterKeyDerivationException(message: String, cause: Throwable) : Exception(message, cause)
