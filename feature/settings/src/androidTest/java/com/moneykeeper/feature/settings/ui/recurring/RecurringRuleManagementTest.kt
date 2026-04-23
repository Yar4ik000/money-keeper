package com.moneykeeper.feature.settings.ui.recurring

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneykeeper.core.database.AppDatabase
import com.moneykeeper.core.database.repository.AccountRepositoryImpl
import com.moneykeeper.core.database.repository.RecurringRuleRepositoryImpl
import com.moneykeeper.core.database.repository.TransactionRepositoryImpl
import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.model.AccountType
import com.moneykeeper.core.domain.model.Frequency
import com.moneykeeper.core.domain.model.RecurringRule
import com.moneykeeper.core.domain.model.Transaction
import com.moneykeeper.core.domain.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Integration tests for recurring rule management via a real in-memory Room database.
 *
 * Each test simulates the user flow described in the settings screens:
 *   - RecurringRulesScreen: stop (long-press → delete)
 *   - RecurringRuleDetailScreen: change frequency / interval / endDate → save
 *
 * Tests call the same repository methods that [RecurringRuleDetailViewModel] delegates to,
 * then verify the resulting DB state that HistoryScreen would observe.
 */
@RunWith(AndroidJUnit4::class)
class RecurringRuleManagementTest {

    private lateinit var db: AppDatabase
    private lateinit var txRepo: TransactionRepositoryImpl
    private lateinit var ruleRepo: RecurringRuleRepositoryImpl
    private var testAccountId: Long = 0L

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val accountRepo = AccountRepositoryImpl(db.accountDao())
        txRepo = TransactionRepositoryImpl(db.transactionDao(), db.accountDao(), db.categoryDao())
        ruleRepo = RecurringRuleRepositoryImpl(
            db.recurringRuleDao(), db.transactionDao(), db.accountDao(), db.categoryDao(),
        )
        testAccountId = accountRepo.save(
            Account(
                name = "Test", type = AccountType.CARD, currency = "RUB",
                colorHex = "#FF0000", iconName = "card",
                balance = BigDecimal.ZERO, createdAt = LocalDate.now(),
            )
        )
    }

    @After
    fun tearDown() = db.close()

    // ── helpers ───────────────────────────────────────────────────────────────

    private suspend fun insertRuleWithTx(
        frequency: Frequency = Frequency.MONTHLY,
        interval: Int = 1,
        endDate: LocalDate? = null,
    ): Pair<Long, Long> {
        val ruleId = ruleRepo.save(
            RecurringRule(
                frequency = frequency, interval = interval,
                startDate = LocalDate.now(), endDate = endDate,
            )
        )
        val txId = txRepo.save(
            Transaction(
                accountId = testAccountId, toAccountId = null,
                amount = BigDecimal("500"), type = TransactionType.EXPENSE,
                categoryId = null, date = LocalDate.now(), note = "Аренда",
                recurringRuleId = ruleId, createdAt = LocalDateTime.now(),
            )
        )
        return ruleId to txId
    }

    // ── stop series ───────────────────────────────────────────────────────────

    /**
     * User: creates recurring op → opens RecurringRulesScreen → stops series.
     * Expected: transaction in history shows as non-recurring (recurringRuleId = null).
     *
     * RecurringRulesViewModel.stopSelected() and RecurringRuleDetailViewModel.stop()
     * both call recurringRuleRepo.delete(ruleId).
     * Room FK ON DELETE SET NULL then clears recurringRuleId on the transaction.
     */
    @Test
    fun stopSeries_clearsRecurringRuleId_onTransaction() = runTest {
        val (ruleId, txId) = insertRuleWithTx()

        ruleRepo.delete(ruleId)   // RecurringRuleDetailViewModel.stop()

        assertNull("Rule must be gone after stop", ruleRepo.getById(ruleId))
        assertNull(
            "Transaction in history must have null recurringRuleId (FK SET NULL)",
            txRepo.getById(txId)?.recurringRuleId,
        )
    }

    /**
     * Stopping one series must not affect transactions that belong to a different series.
     */
    @Test
    fun stopSeries_onlyAffectsTargetSeries() = runTest {
        val (ruleId1, txId1) = insertRuleWithTx()
        val (ruleId2, txId2) = insertRuleWithTx()

        ruleRepo.delete(ruleId1)

        assertNull("Rule 1 must be deleted", ruleRepo.getById(ruleId1))
        assertNull("Tx 1 must have null ruleId", txRepo.getById(txId1)?.recurringRuleId)
        assertNotNull("Rule 2 must be untouched", ruleRepo.getById(ruleId2))
        assertEquals(
            "Tx 2 must still reference rule 2",
            ruleId2, txRepo.getById(txId2)?.recurringRuleId,
        )
    }

    /**
     * Bulk stop (RecurringRulesViewModel.stopSelected with two rules) deletes both rules
     * and clears ruleIds on all their transactions.
     */
    @Test
    fun stopSeries_bulk_clearsAllSelectedRules() = runTest {
        val (ruleId1, txId1) = insertRuleWithTx()
        val (ruleId2, txId2) = insertRuleWithTx()

        // RecurringRulesViewModel.stopSelected() iterates and deletes each
        listOf(ruleId1, ruleId2).forEach { ruleRepo.delete(it) }

        assertNull(ruleRepo.getById(ruleId1))
        assertNull(ruleRepo.getById(ruleId2))
        assertNull(txRepo.getById(txId1)?.recurringRuleId)
        assertNull(txRepo.getById(txId2)?.recurringRuleId)
    }

    // ── change frequency ──────────────────────────────────────────────────────

    /**
     * User: opens settings for recurring op → changes frequency to weekly → saves.
     * Expected: rule in DB has WEEKLY frequency.
     *
     * RecurringRuleDetailViewModel.save() calls
     * recurringRuleRepo.save(rule.copy(frequency = vm.frequency, ...))
     */
    @Test
    fun changeFrequency_toWeekly_updatesRule() = runTest {
        val (ruleId, txId) = insertRuleWithTx(frequency = Frequency.MONTHLY)

        val rule = ruleRepo.getById(ruleId)!!
        ruleRepo.save(rule.copy(frequency = Frequency.WEEKLY))  // RecurringRuleDetailViewModel.save()

        assertEquals(Frequency.WEEKLY, ruleRepo.getById(ruleId)?.frequency)
        // The transaction itself is unchanged — only the rule schedule shifts
        assertEquals(ruleId, txRepo.getById(txId)?.recurringRuleId)
    }

    @Test
    fun changeFrequency_toYearly_updatesRule() = runTest {
        val (ruleId, _) = insertRuleWithTx(frequency = Frequency.MONTHLY)

        val rule = ruleRepo.getById(ruleId)!!
        ruleRepo.save(rule.copy(frequency = Frequency.YEARLY))

        assertEquals(Frequency.YEARLY, ruleRepo.getById(ruleId)?.frequency)
    }

    @Test
    fun changeFrequency_toDaily_updatesRule() = runTest {
        val (ruleId, _) = insertRuleWithTx(frequency = Frequency.MONTHLY)

        val rule = ruleRepo.getById(ruleId)!!
        ruleRepo.save(rule.copy(frequency = Frequency.DAILY))

        assertEquals(Frequency.DAILY, ruleRepo.getById(ruleId)?.frequency)
    }

    // ── change interval ───────────────────────────────────────────────────────

    /**
     * User: changes interval to 3 (every 3 months instead of every month) → saves.
     */
    @Test
    fun changeInterval_to3_updatesRule() = runTest {
        val (ruleId, _) = insertRuleWithTx(interval = 1)

        val rule = ruleRepo.getById(ruleId)!!
        ruleRepo.save(rule.copy(interval = 3))

        assertEquals(3, ruleRepo.getById(ruleId)?.interval)
    }

    /**
     * Frequency and interval changed together in the same save — both must persist.
     */
    @Test
    fun changeFrequencyAndInterval_bothPersisted() = runTest {
        val (ruleId, _) = insertRuleWithTx(frequency = Frequency.MONTHLY, interval = 1)

        val rule = ruleRepo.getById(ruleId)!!
        ruleRepo.save(rule.copy(frequency = Frequency.WEEKLY, interval = 2))

        val saved = ruleRepo.getById(ruleId)
        assertEquals(Frequency.WEEKLY, saved?.frequency)
        assertEquals(2, saved?.interval)
    }

    // ── set / clear end date ──────────────────────────────────────────────────

    /**
     * User sets an end date — generation must stop after that date.
     */
    @Test
    fun setEndDate_updatesRule() = runTest {
        val (ruleId, _) = insertRuleWithTx()
        val endDate = LocalDate.now().plusMonths(6)

        val rule = ruleRepo.getById(ruleId)!!
        ruleRepo.save(rule.copy(endDate = endDate))

        assertEquals(endDate, ruleRepo.getById(ruleId)?.endDate)
    }

    /**
     * User clears the end date — series becomes open-ended again.
     */
    @Test
    fun clearEndDate_removesEndDate() = runTest {
        val endDate = LocalDate.now().plusMonths(3)
        val (ruleId, _) = insertRuleWithTx(endDate = endDate)

        val rule = ruleRepo.getById(ruleId)!!
        ruleRepo.save(rule.copy(endDate = null))

        assertNull(ruleRepo.getById(ruleId)?.endDate)
    }

    /**
     * Setting endDate to yesterday effectively stops future generation without
     * deleting the rule or touching existing transactions.
     */
    @Test
    fun setEndDateToYesterday_keepsRuleAndTransactions() = runTest {
        val (ruleId, txId) = insertRuleWithTx()
        val yesterday = LocalDate.now().minusDays(1)

        val rule = ruleRepo.getById(ruleId)!!
        ruleRepo.save(rule.copy(endDate = yesterday))

        assertNotNull("Rule must still exist (not deleted)", ruleRepo.getById(ruleId))
        assertEquals("Transaction must still reference the rule", ruleId, txRepo.getById(txId)?.recurringRuleId)
        assertEquals(yesterday, ruleRepo.getById(ruleId)?.endDate)
    }
}
