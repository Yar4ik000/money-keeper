package com.moneykeeper.core.domain.repository

interface MasterKeyProvider {
    fun requireKey(): ByteArray
}
