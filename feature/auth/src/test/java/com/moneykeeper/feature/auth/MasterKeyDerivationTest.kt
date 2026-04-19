package com.moneykeeper.feature.auth

import com.moneykeeper.feature.auth.domain.MasterKeyDerivation
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MasterKeyDerivationTest {

    private val derivation = MasterKeyDerivation()

    @Test
    fun derive_returns_32_bytes() {
        val result = derivation.derive(
            password    = "test123".toCharArray(),
            salt        = ByteArray(16) { it.toByte() },
            iterations  = 1,
            memoryKb    = 8192,
            parallelism = 1,
        )
        assertEquals(32, result.size)
    }

    @Test
    fun derive_is_deterministic() {
        val salt = ByteArray(16) { (it * 3).toByte() }
        val pw = "secret".toCharArray()
        val a = derivation.derive(pw, salt, 1, 8192, 1)
        val b = derivation.derive(pw.copyOf(), salt.copyOf(), 1, 8192, 1)
        assertArrayEquals(a, b)
    }

    @Test
    fun derive_differs_for_different_passwords() {
        val salt = ByteArray(16)
        val a = derivation.derive("password1".toCharArray(), salt, 1, 8192, 1)
        val b = derivation.derive("password2".toCharArray(), salt, 1, 8192, 1)
        assert(!a.contentEquals(b)) { "Different passwords should produce different keys" }
    }

    @Test
    fun derive_differs_for_different_salts() {
        val pw = "same".toCharArray()
        val a = derivation.derive(pw, ByteArray(16) { 0 }, 1, 8192, 1)
        val b = derivation.derive(pw.copyOf(), ByteArray(16) { 1 }, 1, 8192, 1)
        assert(!a.contentEquals(b)) { "Different salts should produce different keys" }
    }
}
