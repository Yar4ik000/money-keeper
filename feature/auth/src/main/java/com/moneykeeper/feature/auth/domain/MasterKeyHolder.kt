package com.moneykeeper.feature.auth.domain

import com.moneykeeper.core.domain.repository.MasterKeyProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MasterKeyHolder @Inject constructor() : MasterKeyProvider {

    @Volatile private var key: ByteArray? = null

    @Synchronized
    fun set(freshKey: ByteArray) {
        key?.fill(0)
        key = freshKey.copyOf()
    }

    @Synchronized
    fun clear() {
        key?.fill(0)
        key = null
    }

    fun require(): ByteArray = requireKey()

    override fun requireKey(): ByteArray =
        key?.copyOf() ?: error("MasterKeyHolder: no master_key. App must be Unlocked.")

    fun isSet(): Boolean = key != null
}
