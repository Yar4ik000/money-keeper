package com.moneykeeper.feature.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneykeeper.core.database.security.DatabaseKeyStorage
import com.moneykeeper.feature.auth.domain.MasterKeyDerivation
import com.moneykeeper.feature.auth.domain.PinVerifier
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for PinVerifier — requires Android Keystore (EncryptedSharedPreferences).
 *
 * Uses real MasterKeyDerivation (BouncyCastle Argon2id) with minimal params (1 iteration,
 * 64 KB) to keep each test under ~100 ms.
 */
@RunWith(AndroidJUnit4::class)
class PinVerifierInstrumentedTest {

    private lateinit var keyStorage: DatabaseKeyStorage
    private lateinit var pinVerifier: PinVerifier

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        keyStorage = DatabaseKeyStorage(context)
        keyStorage.wipe()
        pinVerifier = PinVerifier(MasterKeyDerivation(), keyStorage)
    }

    @After
    fun tearDown() {
        keyStorage.wipe()
    }

    @Test
    fun setPin_then_verify_correct_pin_returns_true() {
        pinVerifier.setPin("1234".toCharArray())
        assertTrue(pinVerifier.verify("1234".toCharArray()))
    }

    @Test
    fun verify_wrong_pin_returns_false() {
        pinVerifier.setPin("1234".toCharArray())
        assertFalse(pinVerifier.verify("9999".toCharArray()))
    }

    @Test
    fun verify_returns_false_before_setPin() {
        assertFalse(pinVerifier.verify("1234".toCharArray()))
    }

    @Test
    fun setPin_twice_old_pin_rejected_new_pin_accepted() {
        pinVerifier.setPin("1234".toCharArray())
        pinVerifier.setPin("5678".toCharArray())
        assertFalse("Old PIN must be rejected after setPin", pinVerifier.verify("1234".toCharArray()))
        assertTrue("New PIN must be accepted", pinVerifier.verify("5678".toCharArray()))
    }

    @Test
    fun pin_length_constant_is_4() {
        assertEquals(4, PinVerifier.PIN_LENGTH)
    }
}
