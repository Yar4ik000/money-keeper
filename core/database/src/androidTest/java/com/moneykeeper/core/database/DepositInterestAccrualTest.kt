package com.moneykeeper.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneykeeper.core.database.dao.AccountDao
import com.moneykeeper.core.database.dao.DepositDao
import com.moneykeeper.core.database.dao.DepositEventDao
import com.moneykeeper.core.database.entity.AccountEntity
import com.moneykeeper.core.database.entity.DepositEntity
import com.moneykeeper.core.database.entity.DepositEventEntity
import com.moneykeeper.core.database.entity.toDomain
import com.moneykeeper.core.domain.calculator.DepositCalculator
import com.moneykeeper.core.domain.model.AccrualBasis
import com.moneykeeper.core.domain.model.AccountType
import com.moneykeeper.core.domain.model.CapPeriod
import com.moneykeeper.core.domain.model.DepositEventType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Integration tests for deposit interest accrual logic against a real in-memory Room DB.
 * Validates DAILY segmentation, PERIOD_START behaviour, EOM roll dates, and bulk event deletion.
 */
@RunWith(AndroidJUnit4::class)
class DepositInterestAccrualTest {

    private lateinit var db: AppDatabase
    private lateinit var depositEventDao: DepositEventDao
    private lateinit var depositDao: DepositDao
    private lateinit var accountDao: AccountDao

    private var accountId = 0L
    private var depositId = 0L

    @Before
    fun setUp() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        depositEventDao = db.depositEventDao()
        depositDao      = db.depositDao()
        accountDao      = db.accountDao()

        // Account balance reflects post-topup state (50k initial + 950k top-up)
        accountId = accountDao.upsert(
            AccountEntity(
                name = "Test Deposit", type = AccountType.DEPOSIT, currency = "RUB",
                colorHex = "#42A5F5", iconName = "AccountBalance",
                balance = BigDecimal("1000000"),
                createdAt = LocalDate.of(2025, 10, 31),
            )
        )
        depositId = depositDao.upsert(
            DepositEntity(
                accountId = accountId,
                initialAmount = BigDecimal("50000"),
                interestRate = BigDecimal("16"),
                startDate = LocalDate.of(2025, 10, 31),
                endDate = LocalDate.of(2026, 10, 31),
                isCapitalized = true,
                capitalizationPeriod = CapPeriod.MONTHLY,
                payoutAccountId = null,
                accrualBasis = AccrualBasis.DAILY,
            )
        )
        // Mid-period top-up event: +950k on Nov 3
        depositEventDao.insert(
            DepositEventEntity(
                depositId = depositId,
                date = LocalDate.of(2025, 11, 3),
                type = DepositEventType.PRINCIPAL_ADD,
                amount = BigDecimal("950000"),
            )
        )
    }

    @After
    fun tearDown() = db.close()

    // ── DAILY basis ───────────────────────────────────────────────────────────

    @Test
    fun dailyBasis_midPeriodTopup_produces_11901_37() = runTest {
        val deposit = depositDao.getByAccountId(accountId)!!.toDomain()
        val events  = depositEventDao.getAll(depositId)
        val from    = deposit.startDate                                         // Oct 31
        val to      = DepositCalculator.nextPeriodEnd(from, CapPeriod.MONTHLY) // Nov 30 (EOM)

        val laterPrincipalDelta = events
            .filter { it.date > from && isPrincipal(it.type) }
            .fold(BigDecimal.ZERO) { acc, e -> acc + e.amount }
        val balanceAtFrom = accountDao.getById(accountId)!!.balance - laterPrincipalDelta // 50k

        val changes = events
            .filter { it.date > from && it.date < to && isPrincipal(it.type) }
            .map { it.date to it.amount }

        val slices = DepositCalculator.accrueByPeriodDaily(balanceAtFrom, deposit, from, to, changes)

        assertEquals(1, slices.size)
        assertEquals(to, slices[0].first)
        // Seg1 Oct31→Nov3  (3d) @ 50k  = 65.75
        // Seg2 Nov3→Nov30  (27d) @ 1000k = 11835.62  → total 11901.37
        assertEquals(0, BigDecimal("11901.37").compareTo(slices[0].second))
    }

    // ── PERIOD_START basis ────────────────────────────────────────────────────

    @Test
    fun periodStartBasis_midPeriodTopup_usesBalanceAtPeriodStart() = runTest {
        // Re-insert the deposit as PERIOD_START.
        // REPLACE strategy deletes the old row (cascading to deposit_events), so re-seed the event.
        depositDao.upsert(
            DepositEntity(
                id = depositId,
                accountId = accountId,
                initialAmount = BigDecimal("50000"),
                interestRate = BigDecimal("16"),
                startDate = LocalDate.of(2025, 10, 31),
                endDate = LocalDate.of(2026, 10, 31),
                isCapitalized = true,
                capitalizationPeriod = CapPeriod.MONTHLY,
                payoutAccountId = null,
                accrualBasis = AccrualBasis.PERIOD_START,
            )
        )
        depositEventDao.insert(
            DepositEventEntity(
                depositId = depositId,
                date = LocalDate.of(2025, 11, 3),
                type = DepositEventType.PRINCIPAL_ADD,
                amount = BigDecimal("950000"),
            )
        )

        val deposit = depositDao.getByAccountId(accountId)!!.toDomain()
        val events  = depositEventDao.getAll(depositId)
        val from    = deposit.startDate
        val to      = DepositCalculator.nextPeriodEnd(from, CapPeriod.MONTHLY)

        val laterPrincipalDelta = events
            .filter { it.date > from && isPrincipal(it.type) }
            .fold(BigDecimal.ZERO) { acc, e -> acc + e.amount }
        val balanceAtFrom = accountDao.getById(accountId)!!.balance - laterPrincipalDelta // 50k

        val slices = DepositCalculator.accrueByPeriod(balanceAtFrom, deposit, from, to)

        assertEquals(1, slices.size)
        assertEquals(to, slices[0].first)
        // 50k * 0.16 * 30d / 365 = 657.53  (top-up ignored until next period)
        assertEquals(0, BigDecimal("657.53").compareTo(slices[0].second))
    }

    // ── EOM roll ──────────────────────────────────────────────────────────────

    @Test
    fun eomRoll_oct31_monthly_periodEndIsNov30() {
        val next = DepositCalculator.nextPeriodEnd(LocalDate.of(2025, 10, 31), CapPeriod.MONTHLY)
        assertEquals(LocalDate.of(2025, 11, 30), next)
    }

    @Test
    fun eomRoll_nov30_monthly_periodEndIsDec31() {
        val next = DepositCalculator.nextPeriodEnd(LocalDate.of(2025, 11, 30), CapPeriod.MONTHLY)
        assertEquals(LocalDate.of(2025, 12, 31), next)
    }

    @Test
    fun eomRoll_feb28_nonLeap_periodEndIsMar31() {
        val next = DepositCalculator.nextPeriodEnd(LocalDate.of(2026, 2, 28), CapPeriod.MONTHLY)
        assertEquals(LocalDate.of(2026, 3, 31), next)
    }

    // ── deleteAll ─────────────────────────────────────────────────────────────

    @Test
    fun deleteAll_removesAllEventsForDeposit() = runTest {
        val before = depositEventDao.getAll(depositId)
        assertEquals(1, before.size)

        depositEventDao.deleteAll(depositId)

        assertTrue(depositEventDao.getAll(depositId).isEmpty())
    }

    @Test
    fun deleteAll_doesNotAffectEventsForOtherDeposits() = runTest {
        val acct2 = accountDao.upsert(
            AccountEntity(
                name = "Other", type = AccountType.DEPOSIT, currency = "RUB",
                colorHex = "#EF9A9A", iconName = "AccountBalance",
                balance = BigDecimal("10000"),
                createdAt = LocalDate.of(2025, 10, 31),
            )
        )
        val dep2 = depositDao.upsert(
            DepositEntity(
                accountId = acct2,
                initialAmount = BigDecimal("10000"),
                interestRate = BigDecimal("10"),
                startDate = LocalDate.of(2025, 10, 31),
                endDate = LocalDate.of(2026, 10, 31),
                isCapitalized = false,
                capitalizationPeriod = null,
                payoutAccountId = null,
            )
        )
        depositEventDao.insert(
            DepositEventEntity(
                depositId = dep2,
                date = LocalDate.of(2025, 11, 30),
                type = DepositEventType.INTEREST_ACCRUAL,
                amount = BigDecimal("100"),
            )
        )

        depositEventDao.deleteAll(depositId)

        assertTrue(depositEventDao.getAll(depositId).isEmpty())
        assertEquals(1, depositEventDao.getAll(dep2).size)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun isPrincipal(type: DepositEventType) =
        type == DepositEventType.PRINCIPAL_ADD || type == DepositEventType.PRINCIPAL_WITHDRAW
}
