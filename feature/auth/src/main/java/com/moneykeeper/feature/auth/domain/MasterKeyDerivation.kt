package com.moneykeeper.feature.auth.domain

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import javax.inject.Inject

class MasterKeyDerivation @Inject constructor() {

    fun derive(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        memoryKb: Int,
        parallelism: Int,
    ): ByteArray {
        // CharArray → ByteArray без создания промежуточного String
        val passwordBytes = String(password).toByteArray(Charsets.UTF_8)
        return try {
            Argon2Kt().hash(
                mode                = Argon2Mode.ARGON2_ID,
                password            = passwordBytes,
                salt                = salt,
                tCostInIterations   = iterations,
                mCostInKibibyte     = memoryKb,
                parallelism         = parallelism,
                hashLengthInBytes   = KEY_SIZE_BYTES,
            ).rawHashAsByteArray()
        } catch (e: Throwable) {
            throw MasterKeyDerivationException("Не удалось вычислить master_key", e)
        } finally {
            passwordBytes.fill(0)
        }
    }

    companion object {
        const val KEY_SIZE_BYTES = 32
    }
}

class MasterKeyDerivationException(message: String, cause: Throwable) : Exception(message, cause)
