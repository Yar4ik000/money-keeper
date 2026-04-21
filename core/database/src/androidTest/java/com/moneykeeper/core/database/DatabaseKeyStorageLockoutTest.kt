package com.moneykeeper.core.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneykeeper.core.database.security.DatabaseKeyStorage
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the exponential-backoff rate-limiting in DatabaseKeyStorage:
 *   - First 4 failures: no lockout
 *   - 5th failure: ~30 s lockout
 *   - 6th failure: ~60 s lockout
 *   - 7th failure: ~120 s lockout
 *   - 17th failure: capped at exactly 24 h
 *   - resetFailedAttempts() wipes counter and lockout
 *
 * Each test starts fresh via @Before reset so tests are fully independent.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseKeyStorageLockoutTest {

    private lateinit var keyStorage: DatabaseKeyStorage

    @Before
    fun setUp() {
        keyStorage = DatabaseKeyStorage(ApplicationProvider.getApplicationContext())
        keyStorage.resetFailedAttempts()
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun failTimes(n: Int) = repeat(n) { keyStorage.recordFailedAttempt() }

    /** Remaining lockout in ms, or 0 if not locked out. */
    private fun lockoutRemainingMs(): Long =
        (keyStorage.getLockoutUntilMs() - System.currentTimeMillis()).coerceAtLeast(0L)

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    fun beforeThreshold_noLockoutSet() {
        failTimes(4)
        assertEquals(0L, keyStorage.getLockoutUntilMs())
        assertEquals(4, keyStorage.getFailedAttempts())
    }

    @Test
    fun fifthFailure_triggersLockout_approx30s() {
        failTimes(5)
        val remaining = lockoutRemainingMs()
        // Expected: 30 s ± 5 s tolerance
        assertTrue("Expected ~30 s, got ${remaining}ms", remaining in 25_000L..35_000L)
    }

    @Test
    fun sixthFailure_doublesLockout_approx60s() {
        failTimes(6)
        val remaining = lockoutRemainingMs()
        // Expected: 60 s ± 5 s tolerance
        assertTrue("Expected ~60 s, got ${remaining}ms", remaining in 55_000L..65_000L)
    }

    @Test
    fun seventhFailure_quadruplesLockout_approx120s() {
        failTimes(7)
        val remaining = lockoutRemainingMs()
        assertTrue("Expected ~120 s, got ${remaining}ms", remaining in 115_000L..125_000L)
    }

    @Test
    fun at17Failures_lockoutCapsAt24h() {
        // 30 * 2^(17-5) = 30 * 4096 = 122 880 s > 86 400 s cap
        failTimes(17)
        val remaining = lockoutRemainingMs()
        val twentyFourHours = 86_400_000L
        // Should be exactly 24 h (±5 s for test execution time)
        assertTrue(
            "Expected ~24 h, got ${remaining}ms",
            remaining in (twentyFourHours - 5_000L)..(twentyFourHours + 1_000L),
        )
    }

    @Test
    fun at100Failures_lockoutStillCappedAt24h() {
        failTimes(100)
        val remaining = lockoutRemainingMs()
        val twentyFourHours = 86_400_000L
        assertTrue(
            "Expected lockout ≤ 24 h, got ${remaining}ms",
            remaining <= twentyFourHours + 1_000L,
        )
    }

    @Test
    fun reset_clearsCounterAndLockout() {
        failTimes(7)
        assertTrue(lockoutRemainingMs() > 0L)

        keyStorage.resetFailedAttempts()

        assertEquals(0, keyStorage.getFailedAttempts())
        assertEquals(0L, keyStorage.getLockoutUntilMs())
    }

    @Test
    fun reset_thenFail4Times_stillNoLockout() {
        failTimes(10)
        keyStorage.resetFailedAttempts()

        failTimes(4)
        assertEquals(0L, keyStorage.getLockoutUntilMs())
    }

    @Test
    fun reset_thenFail5Times_triggersLockoutAgain() {
        failTimes(10)
        keyStorage.resetFailedAttempts()

        failTimes(5)
        assertTrue(lockoutRemainingMs() > 0L)
    }

    @Test
    fun recordFailedAttempt_returnsIncrementedCount() {
        assertEquals(1, keyStorage.recordFailedAttempt())
        assertEquals(2, keyStorage.recordFailedAttempt())
        assertEquals(3, keyStorage.recordFailedAttempt())
    }
}
