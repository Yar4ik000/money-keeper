package com.moneykeeper.feature.transactions.domain

import com.moneykeeper.core.domain.model.Frequency
import com.moneykeeper.core.domain.model.RecurringRule
import com.moneykeeper.core.domain.model.Transaction
import com.moneykeeper.core.domain.model.TransactionType
import com.moneykeeper.core.domain.repository.AccountRepository
import com.moneykeeper.core.domain.repository.RecurringRuleRepository
import com.moneykeeper.core.domain.repository.TransactionRepository
import com.moneykeeper.core.domain.repository.TransactionRunner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class TransactionSaverTest {

    // ── fakes ────────────────────────────────────────────────────────────────

    private val balances = mutableMapOf<Long, BigDecimal>()
    private val savedTransactions = mutableListOf<Transaction>()
    private val deletedIds = mutableSetOf<Long>()
    private var savedRuleId = 0L

    private val fakeAccountRepo = object : AccountRepository {
        override fun observeActiveAccounts() = throw UnsupportedOperationException()
        override fun observeAllAccounts() = throw UnsupportedOperationException()
        override fun observeTotalsByCurrency() = throw UnsupportedOperationException()
        override suspend fun getById(id: Long) = null
        override suspend fun save(account: com.moneykeeper.core.domain.model.Account) = 0L
        override suspend fun archive(id: Long) = Unit
        override suspend fun unarchive(id: Long) = Unit
        override suspend fun updateSortOrders(orderedIds: List<Long>) = Unit
        override suspend fun delete(id: Long) = Unit
        override suspend fun adjustBalance(id: Long, delta: BigDecimal) {
            balances[id] = (balances[id] ?: BigDecimal.ZERO) + delta
        }
    }

    private val fakeTxRepo = object : TransactionRepository {
        override fun observe(
            accountId: Long?,
            categoryId: Long?,
            type: TransactionType?,
            from: LocalDate,
            to: LocalDate,
        ) = throw UnsupportedOperationException()
        override fun observeRecent(limit: Int) = throw UnsupportedOperationException()
        override fun observePeriodSummary(from: LocalDate, to: LocalDate) =
            throw UnsupportedOperationException()
        override fun observeByCategory(currency: String, from: LocalDate, to: LocalDate, type: TransactionType) =
            throw UnsupportedOperationException()
        override fun observeByAccount(currency: String, from: LocalDate, to: LocalDate, type: TransactionType) =
            throw UnsupportedOperationException()
        override fun observeMonthlyTrend(currency: String, from: LocalDate, to: LocalDate) =
            throw UnsupportedOperationException()
        override suspend fun getById(id: Long): Transaction? =
            savedTransactions.find { it.id == id }
        override suspend fun getByIds(ids: Set<Long>): List<Transaction> =
            savedTransactions.filter { it.id in ids }
        override suspend fun save(transaction: Transaction): Long {
            savedTransactions.removeIf { it.id == transaction.id }
            savedTransactions.add(transaction)
            return transaction.id
        }
        override suspend fun delete(id: Long) {
            savedTransactions.removeIf { it.id == id }
            deletedIds.add(id)
        }
        override suspend fun deleteByIds(ids: Set<Long>) {
            savedTransactions.removeIf { it.id in ids }
            deletedIds.addAll(ids)
        }
        override suspend fun getAll() = throw UnsupportedOperationException()
    }

    private var prunedCount = 0

    private val fakeRuleRepo = object : RecurringRuleRepository {
        private val rules = mutableMapOf<Long, RecurringRule>()

        override fun observeAll() = throw UnsupportedOperationException()
        override fun observeAllWithTemplates() = throw UnsupportedOperationException()
        override suspend fun getAllWithTemplates(today: LocalDate) =
            throw UnsupportedOperationException()
        override suspend fun getById(id: Long): RecurringRule? = rules[id]
        override suspend fun getByIdWithTemplate(id: Long) = throw UnsupportedOperationException()
        override suspend fun save(rule: RecurringRule): Long {
            savedRuleId++
            rules[savedRuleId] = rule.copy(id = savedRuleId)
            return savedRuleId
        }
        override suspend fun updateLastGeneratedDate(id: Long, date: LocalDate) = Unit
        override suspend fun delete(id: Long) { rules.remove(id) }
        override suspend fun pruneOrphaned(): Int {
            val referencedIds = savedTransactions.mapNotNull { it.recurringRuleId }.toSet()
            val orphans = rules.keys.filter { it !in referencedIds }
            orphans.forEach { rules.remove(it) }
            prunedCount += orphans.size
            return orphans.size
        }
        fun ruleExists(id: Long) = rules.containsKey(id)
    }

    private val fakeTxRunner = object : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }

    private lateinit var saver: TransactionSaver

    private val now = LocalDateTime.now()
    private val today = LocalDate.now()

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun tx(
        id: Long = 1L,
        accountId: Long = 10L,
        toAccountId: Long? = null,
        amount: BigDecimal = BigDecimal("100"),
        type: TransactionType = TransactionType.EXPENSE,
    ) = Transaction(
        id = id,
        accountId = accountId,
        toAccountId = toAccountId,
        amount = amount,
        type = type,
        categoryId = null,
        date = today,
        note = "",
        createdAt = now,
    )

    @Before
    fun setUp() {
        balances.clear()
        savedTransactions.clear()
        deletedIds.clear()
        savedRuleId = 0L
        prunedCount = 0
        saver = TransactionSaver(fakeTxRepo, fakeAccountRepo, fakeRuleRepo, fakeTxRunner)
    }

    // ── tests ────────────────────────────────────────────────────────────────

    @Test
    fun `save EXPENSE decreases account balance`() = runTest {
        val expense = tx(type = TransactionType.EXPENSE, amount = BigDecimal("50"))
        saver.save(expense)
        assertEquals(BigDecimal("-50"), balances[10L])
    }

    @Test
    fun `save INCOME increases account balance`() = runTest {
        val income = tx(type = TransactionType.INCOME, amount = BigDecimal("200"))
        saver.save(income)
        assertEquals(BigDecimal("200"), balances[10L])
    }

    @Test
    fun `save SAVINGS decreases account balance`() = runTest {
        val savings = tx(type = TransactionType.SAVINGS, amount = BigDecimal("75"))
        saver.save(savings)
        assertEquals(BigDecimal("-75"), balances[10L])
    }

    @Test
    fun `save TRANSFER debits source and credits destination`() = runTest {
        val transfer = tx(
            type = TransactionType.TRANSFER,
            accountId = 10L,
            toAccountId = 20L,
            amount = BigDecimal("300"),
        )
        saver.save(transfer)
        assertEquals(BigDecimal("-300"), balances[10L])
        assertEquals(BigDecimal("300"), balances[20L])
    }

    @Test
    fun `delete reverses EXPENSE balance`() = runTest {
        val expense = tx(type = TransactionType.EXPENSE, amount = BigDecimal("40"))
        fakeTxRepo.save(expense)
        balances[10L] = BigDecimal("-40")   // simulate prior state
        saver.delete(expense)
        assertEquals(BigDecimal("0"), balances[10L])
    }

    @Test
    fun `replace reverses old and applies new`() = runTest {
        val old = tx(
            id = 1L,
            type = TransactionType.EXPENSE,
            amount = BigDecimal("100"),
            accountId = 10L,
        )
        fakeTxRepo.save(old)
        balances[10L] = BigDecimal("-100")

        val new = old.copy(amount = BigDecimal("60"))
        saver.replace(old, new)

        // Reverse old (-100 -> +100 -> 0), then apply new (0 - 60 = -60)
        assertEquals(BigDecimal("-60"), balances[10L])
    }

    @Test
    fun `deleteMany aggregates deltas correctly`() = runTest {
        val e1 = tx(id = 1L, type = TransactionType.EXPENSE, amount = BigDecimal("30"), accountId = 10L)
        val e2 = tx(id = 2L, type = TransactionType.INCOME, amount = BigDecimal("50"), accountId = 10L)
        fakeTxRepo.save(e1)
        fakeTxRepo.save(e2)
        balances[10L] = BigDecimal("20") // net state: +50 -30

        saver.deleteMany(setOf(1L, 2L))

        // reverse expense (+30) + reverse income (-50) = net -20 applied to balance 20 => 0
        assertEquals(BigDecimal("0"), balances[10L])
    }

    @Test
    fun `deleteMany with empty set is no-op`() = runTest {
        saver.deleteMany(emptySet())
        assertEquals(0, deletedIds.size)
    }

    @Test
    fun `save with recurringRule stores ruleId on transaction`() = runTest {
        val rule = RecurringRule(
            id = 0L,
            frequency = Frequency.MONTHLY,
            interval = 1,
            startDate = today,
            endDate = null,
        )
        val expense = tx(type = TransactionType.EXPENSE)
        saver.save(expense, recurringRule = rule)

        val saved = savedTransactions.first()
        assertEquals(1L, saved.recurringRuleId)
    }

    @Test
    fun `replace with Clear detaches rule and prunes orphan`() = runTest {
        val rule = RecurringRule(id = 0L, frequency = Frequency.MONTHLY, interval = 1, startDate = today, endDate = null)
        val expense = tx()
        saver.save(expense, recurringRule = rule)

        val savedWithRule = savedTransactions.first()
        val ruleId = savedWithRule.recurringRuleId!!
        assertTrue("Rule should exist after save", fakeRuleRepo.ruleExists(ruleId))

        saver.replace(savedWithRule, savedWithRule.copy(note = "edited"), RecurringUpdate.Clear)

        val afterReplace = savedTransactions.find { it.id == savedWithRule.id }!!
        assertNull("recurringRuleId must be null after Clear", afterReplace.recurringRuleId)
        assertFalse("Orphaned rule must be pruned", fakeRuleRepo.ruleExists(ruleId))
    }

    @Test
    fun `replace with StopSeries deletes rule immediately`() = runTest {
        val rule = RecurringRule(id = 0L, frequency = Frequency.MONTHLY, interval = 1, startDate = today, endDate = null)
        val expense = tx()
        saver.save(expense, recurringRule = rule)

        val savedWithRule = savedTransactions.first()
        val ruleId = savedWithRule.recurringRuleId!!

        saver.replace(savedWithRule, savedWithRule.copy(note = "stop"), RecurringUpdate.StopSeries(ruleId))

        assertFalse("Rule must be deleted by StopSeries", fakeRuleRepo.ruleExists(ruleId))
    }

    @Test
    fun `replace with Clear does NOT prune rule still referenced by another transaction`() = runTest {
        val rule = RecurringRule(id = 0L, frequency = Frequency.MONTHLY, interval = 1, startDate = today, endDate = null)
        saver.save(tx(id = 1L), recurringRule = rule)
        val ruleId = savedTransactions.first().recurringRuleId!!

        val sister = tx(id = 2L).copy(recurringRuleId = ruleId)
        fakeTxRepo.save(sister)

        val first = savedTransactions.find { it.id == 1L }!!
        saver.replace(first, first.copy(note = "edited"), RecurringUpdate.Clear)

        assertTrue("Rule must survive if still referenced by sister", fakeRuleRepo.ruleExists(ruleId))
    }

    @Test
    fun `delete prunes orphaned rule`() = runTest {
        val rule = RecurringRule(id = 0L, frequency = Frequency.MONTHLY, interval = 1, startDate = today, endDate = null)
        val expense = tx()
        saver.save(expense, recurringRule = rule)

        val savedWithRule = savedTransactions.first()
        saver.delete(savedWithRule)

        assertFalse("Rule should be pruned after deleting last referencing transaction", fakeRuleRepo.ruleExists(savedWithRule.recurringRuleId!!))
    }

    // ── non-obvious: regression + edge cases ─────────────────────────────────

    @Test
    fun `save with recurringRule seeds rule lastGeneratedDate to transaction date`() = runTest {
        // Regression: without seeding lastGeneratedDate, GenerateRecurringTransactionsUseCase
        // re-generates a transaction for startDate on the next run, duplicating what
        // the user just saved. RecurringDates.expandDates starts at startDate and the
        // use case treats null lastGeneratedDate as startDate.minusDays(1).
        val rule = RecurringRule(
            id = 0L, frequency = Frequency.MONTHLY, interval = 1,
            startDate = today, endDate = null,
        )
        val expense = tx(type = TransactionType.EXPENSE, amount = BigDecimal("100"))

        saver.save(expense, recurringRule = rule)

        val savedTx = savedTransactions.first()
        val savedRule = fakeRuleRepo.getById(savedTx.recurringRuleId!!)!!
        assertEquals(
            "Rule's lastGeneratedDate must be seeded to the transaction's date so the generator does not re-create it",
            expense.date,
            savedRule.lastGeneratedDate,
        )
    }

    @Test
    fun `replace INCOME to EXPENSE correctly flips sign of balance impact`() = runTest {
        // Type change is a classic sign-flip bug: if reverse/apply is not strictly
        // "reverse old THEN apply new", the balance drifts by ±amount or ±2×amount.
        val old = tx(id = 1L, type = TransactionType.INCOME, amount = BigDecimal("100"), accountId = 10L)
        fakeTxRepo.save(old)
        balances[10L] = BigDecimal("100") // income was previously applied

        val new = old.copy(type = TransactionType.EXPENSE)
        saver.replace(old, new)

        // reverse INCOME (100 → 0), apply EXPENSE (0 → -100). Net: -200 from original.
        assertEquals(BigDecimal("-100"), balances[10L])
    }

    @Test
    fun `deleteMany with TRANSFER reverses both source and destination`() = runTest {
        val transfer = tx(
            id = 1L, type = TransactionType.TRANSFER,
            accountId = 10L, toAccountId = 20L, amount = BigDecimal("300"),
        )
        fakeTxRepo.save(transfer)
        balances[10L] = BigDecimal("-300") // source was debited
        balances[20L] = BigDecimal("300")  // destination was credited

        saver.deleteMany(setOf(1L))

        assertEquals("Source must be credited back", BigDecimal("0"), balances[10L])
        assertEquals("Destination must be debited back", BigDecimal("0"), balances[20L])
    }

    @Test
    fun `replace TRANSFER changing destination moves money to new dest and restores old`() = runTest {
        val old = tx(
            id = 1L, type = TransactionType.TRANSFER,
            accountId = 10L, toAccountId = 20L, amount = BigDecimal("500"),
        )
        fakeTxRepo.save(old)
        balances[10L] = BigDecimal("-500")
        balances[20L] = BigDecimal("500")

        val new = old.copy(toAccountId = 30L)
        saver.replace(old, new)

        // Reverse old: +500 to 10, -500 to 20. Apply new: -500 to 10, +500 to 30.
        assertEquals("Source net unchanged (still -500 from new transfer)", BigDecimal("-500"), balances[10L])
        assertEquals("Old destination must be restored to zero", BigDecimal("0"), balances[20L])
        assertEquals("New destination must receive the transfer", BigDecimal("500"), balances[30L])
    }
}
