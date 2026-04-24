package com.moneykeeper.feature.transactions.domain

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
import com.moneykeeper.core.domain.repository.TransactionRunner
import com.moneykeeper.core.domain.usecase.GenerateRecurringTransactionsUseCase
import kotlinx.coroutines.runBlocking
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
 * Integration tests for [TransactionSaver] with a real in-memory Room database.
 * Covers recurring rule lifecycle: pruning, StopSeries, Clear, and balance correctness.
 */
@RunWith(AndroidJUnit4::class)
class TransactionSaverRuntimeTest {

    private lateinit var db: AppDatabase
    private lateinit var saver: TransactionSaver
    private lateinit var txRepo: TransactionRepositoryImpl
    private lateinit var ruleRepo: RecurringRuleRepositoryImpl
    private var testAccountId: Long = 0L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val accountRepo = AccountRepositoryImpl(db.accountDao())
        txRepo = TransactionRepositoryImpl(db.transactionDao(), db.accountDao(), db.categoryDao())
        ruleRepo = RecurringRuleRepositoryImpl(
            db.recurringRuleDao(), db.transactionDao(), db.accountDao(), db.categoryDao(),
        )
        val directRunner = object : TransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T = block()
        }
        saver = TransactionSaver(txRepo, accountRepo, ruleRepo, directRunner)
        runBlocking {
            testAccountId = accountRepo.save(
                Account(
                    name = "Test", type = AccountType.CARD, currency = "RUB",
                    colorHex = "#FF0000", iconName = "card",
                    balance = BigDecimal.ZERO, createdAt = LocalDate.now(),
                )
            )
        }
    }

    @After
    fun tearDown() = db.close()

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun tx() = Transaction(
        accountId = testAccountId, toAccountId = null,
        amount = BigDecimal("100"), type = TransactionType.EXPENSE,
        categoryId = null, date = LocalDate.now(), createdAt = LocalDateTime.now(),
    )

    private fun monthlyRule() = RecurringRule(
        id = 0L, frequency = Frequency.MONTHLY, interval = 1,
        startDate = LocalDate.now(), endDate = null,
    )

    /** Saves transaction + rule via repos directly (no balance effect needed for rule tests). */
    private suspend fun saveTxWithRule(): Pair<Transaction, Long> {
        val ruleId = ruleRepo.save(monthlyRule())
        val txId = txRepo.save(tx().copy(recurringRuleId = ruleId))
        return txRepo.getById(txId)!! to ruleId
    }

    // ── deleteMany: orphan pruning ────────────────────────────────────────────

    @Test
    fun deleteMany_prunesRule_whenAllTransactionsInSeriesDeleted() = runTest {
        val (saved, ruleId) = saveTxWithRule()

        saver.deleteMany(setOf(saved.id))

        assertNull("Rule must be pruned when last referencing transaction is deleted", ruleRepo.getById(ruleId))
    }

    @Test
    fun deleteMany_keepsRule_whenSisterTransactionStillExists() = runTest {
        val (tx1, ruleId) = saveTxWithRule()
        val tx2Id = txRepo.save(tx().copy(recurringRuleId = ruleId))

        saver.deleteMany(setOf(tx1.id))

        assertNotNull("Rule must survive while a sister transaction still references it", ruleRepo.getById(ruleId))

        // Delete the sister too; rule must now be pruned
        saver.deleteMany(setOf(tx2Id))
        assertNull("Rule must be pruned after all referencing transactions are deleted", ruleRepo.getById(ruleId))
    }

    @Test
    fun deleteMany_withNoRecurringTransactions_isNoop_forRules() = runTest {
        val ruleId = ruleRepo.save(monthlyRule())
        val txId = txRepo.save(tx().copy(recurringRuleId = ruleId))
        val plainTxId = txRepo.save(tx())  // no rule

        saver.deleteMany(setOf(plainTxId))

        assertNotNull("Rule must not be touched when deleting non-recurring transactions", ruleRepo.getById(ruleId))
    }

    // ── deleteManyStopSeries ──────────────────────────────────────────────────

    @Test
    fun deleteManyStopSeries_deletesRuleAndSelectedTransaction() = runTest {
        val (saved, ruleId) = saveTxWithRule()

        saver.deleteManyStopSeries(setOf(saved.id))

        assertNull("Rule must be deleted by StopSeries", ruleRepo.getById(ruleId))
        assertNull("Selected transaction must be deleted", txRepo.getById(saved.id))
    }

    @Test
    fun deleteManyStopSeries_deletesRule_evenWhenSisterTransactionSurvives() = runTest {
        val (tx1, ruleId) = saveTxWithRule()
        val tx2Id = txRepo.save(tx().copy(recurringRuleId = ruleId))

        // Stop the series by deleting only tx1; sister (tx2) is NOT selected
        saver.deleteManyStopSeries(setOf(tx1.id))

        assertNull("Rule must be deleted immediately by StopSeries", ruleRepo.getById(ruleId))
        assertNull("tx1 must be deleted", txRepo.getById(tx1.id))
        assertNotNull("Sister transaction must survive", txRepo.getById(tx2Id))
    }

    @Test
    fun deleteManyStopSeries_deletesMultipleRulesFromDifferentSeries() = runTest {
        val (tx1, ruleId1) = saveTxWithRule()
        val (tx2, ruleId2) = saveTxWithRule()

        saver.deleteManyStopSeries(setOf(tx1.id, tx2.id))

        assertNull("Rule 1 must be deleted", ruleRepo.getById(ruleId1))
        assertNull("Rule 2 must be deleted", ruleRepo.getById(ruleId2))
    }

    // ── replace with StopSeries ───────────────────────────────────────────────

    @Test
    fun replace_withStopSeries_deletesRule_andClearsRuleIdOnTransaction() = runTest {
        val (saved, ruleId) = saveTxWithRule()

        saver.replace(saved, saved.copy(note = "edited"), RecurringUpdate.StopSeries(ruleId))

        assertNull("Rule must be deleted", ruleRepo.getById(ruleId))
        assertNull("recurringRuleId must be null after StopSeries", txRepo.getById(saved.id)?.recurringRuleId)
    }

    @Test
    fun replace_withStopSeries_preservesTransactionInHistory() = runTest {
        val (saved, ruleId) = saveTxWithRule()

        saver.replace(saved, saved.copy(note = "keep history"), RecurringUpdate.StopSeries(ruleId))

        assertNotNull("Transaction must still exist in history after StopSeries", txRepo.getById(saved.id))
    }

    // ── replace with Clear ────────────────────────────────────────────────────

    @Test
    fun replace_withClear_prunesOrphanedRule() = runTest {
        val (saved, ruleId) = saveTxWithRule()

        saver.replace(saved, saved.copy(note = "detach"), RecurringUpdate.Clear)

        assertNull("Orphaned rule must be pruned after Clear", ruleRepo.getById(ruleId))
        assertNull("recurringRuleId must be null after Clear", txRepo.getById(saved.id)?.recurringRuleId)
    }

    @Test
    fun replace_withClear_keepsRule_whenSisterStillReferences() = runTest {
        val (tx1, ruleId) = saveTxWithRule()
        txRepo.save(tx().copy(recurringRuleId = ruleId))  // sister

        saver.replace(tx1, tx1.copy(note = "detach"), RecurringUpdate.Clear)

        assertNotNull("Rule must survive when a sister transaction still references it", ruleRepo.getById(ruleId))
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    fun delete_prunesRule_whenLastReferenceGone() = runTest {
        val (saved, ruleId) = saveTxWithRule()

        saver.delete(saved)

        assertNull("Rule must be pruned after deleting the last referencing transaction", ruleRepo.getById(ruleId))
    }

    @Test
    fun delete_keepsRule_whenSisterTransactionSurvives() = runTest {
        val (tx1, ruleId) = saveTxWithRule()
        txRepo.save(tx().copy(recurringRuleId = ruleId))  // sister

        saver.delete(tx1)

        assertNotNull("Rule must survive while a sister transaction still references it", ruleRepo.getById(ruleId))
    }

    // ── balance correctness ───────────────────────────────────────────────────

    // ── regression: no duplicate on next-day generation after saving with rule ──

    @Test
    fun saveWithRecurringRule_thenGenerateNextDay_doesNotDuplicateTransactionOnStartDate() = runTest {
        // Regression: TransactionSaver.save persists the user-entered transaction AND
        // a rule whose startDate equals that transaction's date. On the next run of
        // GenerateRecurringTransactionsUseCase (worker or post-unlock catch-up), the
        // generator must not produce a second transaction for startDate.
        val startDate = LocalDate.of(2026, 4, 20)
        val expense = Transaction(
            accountId = testAccountId, toAccountId = null,
            amount = BigDecimal("100"), type = TransactionType.EXPENSE,
            categoryId = null, date = startDate, createdAt = LocalDateTime.now(),
        )
        val rule = RecurringRule(
            id = 0L, frequency = Frequency.MONTHLY, interval = 1,
            startDate = startDate, endDate = null,
        )
        saver.save(expense, recurringRule = rule)
        val balanceAfterSave = db.accountDao().getById(testAccountId)!!.balance

        val accountRepo = AccountRepositoryImpl(db.accountDao())
        val directRunner = object : TransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T = block()
        }
        val generator = GenerateRecurringTransactionsUseCase(ruleRepo, txRepo, accountRepo, directRunner)
        generator(today = startDate.plusDays(1))

        val txCount = txRepo.getAll().size
        assertEquals(
            "Generator must not re-create the user-entered transaction for startDate",
            1, txCount,
        )
        val balanceAfter = db.accountDao().getById(testAccountId)!!.balance
        assertEquals(
            "Balance must not double-count the initial transaction",
            balanceAfterSave, balanceAfter,
        )
    }

    @Test
    fun save_expense_decreasesBalance_andDelete_restoresIt() = runTest {
        val expense = tx()
        saver.save(expense)

        val balanceAfterSave = db.accountDao().getById(testAccountId)!!.balance
        assertEquals("Balance must decrease by expense amount", BigDecimal("-100"), balanceAfterSave)

        val savedTx = txRepo.getAll().first().transaction
        saver.delete(savedTx)

        val balanceAfterDelete = db.accountDao().getById(testAccountId)!!.balance
        assertEquals("Balance must be restored to zero after deleting the expense", BigDecimal.ZERO, balanceAfterDelete)
    }
}
