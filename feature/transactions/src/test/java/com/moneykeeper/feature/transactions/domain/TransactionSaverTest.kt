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
    }

    private val fakeRuleRepo = object : RecurringRuleRepository {
        override fun observeAll() = throw UnsupportedOperationException()
        override fun observeAllWithTemplates() = throw UnsupportedOperationException()
        override suspend fun getAllWithTemplates(today: LocalDate) =
            throw UnsupportedOperationException()
        override suspend fun getById(id: Long): RecurringRule? = null
        override suspend fun save(rule: RecurringRule): Long {
            savedRuleId++
            return savedRuleId
        }
        override suspend fun updateLastGeneratedDate(id: Long, date: LocalDate) = Unit
        override suspend fun delete(id: Long) = Unit
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
}
