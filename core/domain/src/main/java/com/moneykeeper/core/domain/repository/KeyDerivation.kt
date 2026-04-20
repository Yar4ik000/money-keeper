package com.moneykeeper.core.domain.repository

interface KeyDerivation {
    fun derive(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        memoryKb: Int,
        parallelism: Int,
    ): ByteArray
}
