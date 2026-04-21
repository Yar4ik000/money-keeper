package com.moneykeeper.feature.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneykeeper.core.database.AppDatabase
import com.moneykeeper.core.database.DatabaseProvider
import com.moneykeeper.core.database.security.DatabaseKeyStorage
import com.moneykeeper.core.database.security.KeystoreMasterKeyWrapper
import com.moneykeeper.feature.auth.domain.BiometricAuthenticator
import com.moneykeeper.feature.auth.domain.MasterKeyDerivation
import com.moneykeeper.feature.auth.domain.MasterKeyHolder
import com.moneykeeper.feature.auth.domain.PinVerifier
import com.moneykeeper.feature.auth.domain.UnlockController
import com.moneykeeper.feature.auth.domain.aesGcmEncrypt
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.SecureRandom

/**
 * End-to-end instrumented tests for the PIN setup → unlock flow.
 *
 * Wires up all domain objects manually (no Hilt) — same classes that
 * SetupPinViewModel / UnlockPinViewModel use in production.
 *
 * Three scenarios:
 *   1. Correct PIN after full setup → Success
 *   2. Wrong PIN → WrongPassword with incrementing failedCount
 *   3. Five consecutive wrong PINs → sixth attempt returns LockedOut
 */
@RunWith(AndroidJUnit4::class)
class PinUnlockFlowTest {

    private lateinit var context: Context
    private lateinit var keyStorage: DatabaseKeyStorage
    private lateinit var keystoreWrapper: KeystoreMasterKeyWrapper
    private lateinit var pinVerifier: PinVerifier
    private lateinit var databaseProvider: DatabaseProvider
    private lateinit var masterKeyHolder: MasterKeyHolder
    private lateinit var unlockController: UnlockController

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        keyStorage = DatabaseKeyStorage(context)
        keyStorage.wipe()

        keystoreWrapper = KeystoreMasterKeyWrapper(keyStorage)
        keystoreWrapper.deleteKey()

        val derivation = MasterKeyDerivation()
        pinVerifier = PinVerifier(derivation, keyStorage)
        masterKeyHolder = MasterKeyHolder()

        deleteDbFiles()
        databaseProvider = DatabaseProvider(context, keyStorage)

        unlockController = UnlockController(
            keyStorage = keyStorage,
            derivation = derivation,
            masterKeyHolder = masterKeyHolder,
            databaseProvider = databaseProvider,
            biometric = BiometricAuthenticator(context, keyStorage),
            pinVerifier = pinVerifier,
            keystoreWrapper = keystoreWrapper,
            postUnlockCallbacks = emptySet(),
        )
    }

    @After
    fun tearDown() {
        runCatching { databaseProvider.close() }
        deleteDbFiles()
        runCatching { keystoreWrapper.deleteKey() }
        keyStorage.wipe()
    }

    // ── Tests ─────────────────────────────────────────────────────────────

    @Test
    fun correctPin_afterFullSetup_returnsSuccess() = runTest {
        fullSetup(pin = "1234")
        simulateRestart()

        val result = unlockController.unlockWithPin("1234".toCharArray())
        assertTrue("Expected Success, got: $result", result is UnlockController.UnlockResult.Success)
    }

    @Test
    fun wrongPin_returnsWrongPasswordWithFailedCount1() = runTest {
        pinVerifier.setPin("1234".toCharArray())

        val result = unlockController.unlockWithPin("9999".toCharArray())
        assertTrue("Expected WrongPassword, got: $result", result is UnlockController.UnlockResult.WrongPassword)
        assertEquals(1, (result as UnlockController.UnlockResult.WrongPassword).failedCount)
    }

    @Test
    fun wrongPin_failedCountIncrementsOnEachAttempt() = runTest {
        pinVerifier.setPin("1234".toCharArray())

        for (i in 1..4) {
            val result = unlockController.unlockWithPin("0000".toCharArray())
            assertTrue(result is UnlockController.UnlockResult.WrongPassword)
            assertEquals("failedCount after attempt $i", i,
                (result as UnlockController.UnlockResult.WrongPassword).failedCount)
        }
    }

    @Test
    fun fiveWrongPins_sixthAttemptIsLockedOut() = runTest {
        pinVerifier.setPin("1234".toCharArray())

        repeat(5) { i ->
            val r = unlockController.unlockWithPin("0000".toCharArray())
            assertTrue("Attempt ${i + 1} should be WrongPassword, got: $r",
                r is UnlockController.UnlockResult.WrongPassword)
        }

        val result = unlockController.unlockWithPin("0000".toCharArray())
        assertTrue("6th attempt should be LockedOut, got: $result",
            result is UnlockController.UnlockResult.LockedOut)
        val lockedOut = result as UnlockController.UnlockResult.LockedOut
        assertTrue("lockoutUntilMs must be in the future",
            lockedOut.lockoutUntilMs > System.currentTimeMillis())
    }

    @Test
    fun correctPinAfterWrongAttempts_resetsCounter() = runTest {
        fullSetup(pin = "1234")
        simulateRestart()

        // Two wrong attempts
        repeat(2) { unlockController.unlockWithPin("0000".toCharArray()) }

        // Correct PIN should still work and reset the counter
        val result = unlockController.unlockWithPin("1234".toCharArray())
        assertTrue("Correct PIN after wrong attempts must succeed, got: $result",
            result is UnlockController.UnlockResult.Success)

        // Counter in storage must be cleared
        assertEquals(0, keyStorage.getFailedAttempts())
    }

    @Test
    fun fiveWrongPins_fifthResultCarriesLockoutTimestamp() = runTest {
        pinVerifier.setPin("1234".toCharArray())

        var lastResult: UnlockController.UnlockResult = UnlockController.UnlockResult.Success
        repeat(5) { lastResult = unlockController.unlockWithPin("0000".toCharArray()) }

        assertTrue(lastResult is UnlockController.UnlockResult.WrongPassword)
        val wrong = lastResult as UnlockController.UnlockResult.WrongPassword
        assertEquals(5, wrong.failedCount)
        assertTrue("5th failure must carry lockoutUntilMs > 0", wrong.lockoutUntilMs > 0L)
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Mirrors the key-setup logic in SetupPinViewModel:
     *   generate master_key + db_key → wrap both → init DB.
     */
    private fun fullSetup(pin: String) {
        val masterKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val dbKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        try {
            pinVerifier.setPin(pin.toCharArray())
            keystoreWrapper.wrap(masterKey)
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            keyStorage.writeEncryptedDbKey(aesGcmEncrypt(dbKey, masterKey, iv), iv)
            databaseProvider.initialize(dbKey.copyOf())
        } finally {
            masterKey.fill(0)
            dbKey.fill(0)
        }
    }

    /** Mirrors process restart: close DB and evict the in-memory master key. */
    private fun simulateRestart() {
        databaseProvider.close()
        masterKeyHolder.clear()
    }

    private fun deleteDbFiles() {
        context.getDatabasePath(AppDatabase.DB_NAME).let { f ->
            f.delete()
            File(f.parent!!, "${f.name}-wal").delete()
            File(f.parent!!, "${f.name}-shm").delete()
        }
    }
}
