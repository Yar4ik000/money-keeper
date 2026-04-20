package com.moneykeeper.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneykeeper.core.database.entity.AccountEntity
import com.moneykeeper.core.database.entity.RecurringRuleEntity
import com.moneykeeper.core.database.entity.TransactionEntity
import com.moneykeeper.core.database.repository.AccountRepositoryImpl
import com.moneykeeper.core.database.repository.RecurringRuleRepositoryImpl
import com.moneykeeper.core.database.repository.TransactionRepositoryImpl
import com.moneykeeper.core.domain.model.AccountType
import com.moneykeeper.core.domain.model.Frequency
import com.moneykeeper.core.domain.model.TransactionType
import com.moneykeeper.core.domain.repository.TransactionRunner
import com.moneykeeper.core.domain.usecase.GenerateRecurringTransactionsUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Integration tests for GenerateRecurringTransactionsUseCase against a real in-memory Room DB.
 * Verifies balance adjustments, date expansion, and edge cases (expired/up-to-date rules).
 */
@RunWith(AndroidJUnit4::class)
class GenerateRecurringTransactionsUseCaseTest {

    private lateinit var db: AppDatabase
    private lateinit var useCase: GenerateRecurringTransactionsUseCase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val accountRepo = AccountRepositoryImpl(db.accountDao())
        val txRepo = TransactionRepositoryImpl(db.transactionDao(), db.accountDao(), db.categoryDao())
        val ruleRepo = RecurringRuleRepositoryImpl(
            db.recurringRuleDao(), db.transactionDao(), db.accountDao(), db.categoryDao()
        )
        val txRunner = object : TransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction(block)
        }
        useCase = GenerateRecurringTransactionsUseCase(ruleRepo, txRepo, accountRepo, txRunner)
    }

    @After
    fun tearDown() = db.close()

    private fun assertAmount(expected: BigDecimal, actual: BigDecimal) =
        assertTrue("Expected ${expected.toPlainString()} but got ${actual.toPlainString()}",
            expected.compareTo(actual) == 0)

    private suspend fun insertAccount(name: String, balance: BigDecimal): Long =
        db.accountDao().upsert(
            AccountEntity(
                name = name, type = AccountType.CARD, currency = "RUB",
                colorHex = "#000", iconName = "CreditCard",
                balance = balance, createdAt = LocalDate.of(2026, 1, 1),
            )
        )

    /**
     * Creates a rule and a seed transaction linked to it.
     * The seed is the "template" that RecurringRuleRepositoryImpl.buildAggregate() picks up.
     * lastGeneratedDate = null → use case will start generating from startDate.
     */
    private suspend fun insertRuleWithSeed(
        accountId: Long,
        toAccountId: Long? = null,
        type: TransactionType,
        amount: BigDecimal,
        frequency: Frequency,
        startDate: LocalDate,
        endDate: LocalDate? = null,
        lastGeneratedDate: LocalDate? = null,
    ): Long {
        val ruleId = db.recurringRuleDao().upsert(
            RecurringRuleEntity(
                frequency = frequency, startDate = startDate,
                endDate = endDate, lastGeneratedDate = lastGeneratedDate,
            )
        )
        db.transactionDao().upsert(
            TransactionEntity(
                accountId = accountId, toAccountId = toAccountId,
                amount = amount, type = type,
                categoryId = null, date = startDate,
                note = "seed", recurringRuleId = ruleId,
                createdAt = LocalDateTime.of(startDate.year, startDate.month, startDate.dayOfMonth, 12, 0),
            )
        )
        return ruleId
    }

    // ── Income: balance credited ──────────────────────────────────────────────

    @Test
    fun dailyIncomeRule_creditsBalanceForEachGeneratedDate() = runTest {
        // startDate = 2026-04-19, today = 2026-04-20
        // lastGenerated = startDate - 1 day = 2026-04-18
        // expandDates(from=2026-04-19, to=2026-04-20) → {2026-04-19, 2026-04-20} = 2 dates
        // balance += 100 * 2 = +200
        val accId = insertAccount("Card", BigDecimal("5000.00"))
        insertRuleWithSeed(
            accId, type = TransactionType.INCOME, amount = BigDecimal("100.00"),
            frequency = Frequency.DAILY, startDate = LocalDate.of(2026, 4, 19),
        )

        useCase(today = LocalDate.of(2026, 4, 20))

        assertAmount(BigDecimal("5200.00"), db.accountDao().getById(accId)!!.balance)
    }

    // ── Expense: balance debited ──────────────────────────────────────────────

    @Test
    fun dailyExpenseRule_debitsBalanceForEachGeneratedDate() = runTest {
        // startDate = 2026-04-19, today = 2026-04-20 → 2 dates
        // balance -= 500 * 2 = -1000
        val accId = insertAccount("Card", BigDecimal("10000.00"))
        insertRuleWithSeed(
            accId, type = TransactionType.EXPENSE, amount = BigDecimal("500.00"),
            frequency = Frequency.DAILY, startDate = LocalDate.of(2026, 4, 19),
        )

        useCase(today = LocalDate.of(2026, 4, 20))

        assertAmount(BigDecimal("9000.00"), db.accountDao().getById(accId)!!.balance)
    }

    // ── Transfer: debit source, credit destination ────────────────────────────

    @Test
    fun transferRule_debitsSourceAndCreditsDestination() = runTest {
        // startDate = 2026-04-20 = today → 1 date generated
        val srcId = insertAccount("Source", BigDecimal("10000.00"))
        val dstId = insertAccount("Destination", BigDecimal("0.00"))
        insertRuleWithSeed(
            srcId, toAccountId = dstId,
            type = TransactionType.TRANSFER, amount = BigDecimal("1000.00"),
            frequency = Frequency.DAILY, startDate = LocalDate.of(2026, 4, 20),
        )

        useCase(today = LocalDate.of(2026, 4, 20))

        assertAmount(BigDecimal("9000.00"), db.accountDao().getById(srcId)!!.balance)
        assertAmount(BigDecimal("1000.00"), db.accountDao().getById(dstId)!!.balance)
    }

    // ── Savings: balance debited (same as expense) ────────────────────────────

    @Test
    fun savingsRule_debitsBalance() = runTest {
        val accId = insertAccount("Card", BigDecimal("5000.00"))
        insertRuleWithSeed(
            accId, type = TransactionType.SAVINGS, amount = BigDecimal("200.00"),
            frequency = Frequency.DAILY, startDate = LocalDate.of(2026, 4, 20),
        )

        useCase(today = LocalDate.of(2026, 4, 20))

        assertAmount(BigDecimal("4800.00"), db.accountDao().getById(accId)!!.balance)
    }

    // ── Expired rule: nothing generated ──────────────────────────────────────

    @Test
    fun expiredRule_generatesNoTransactions() = runTest {
        // endDate = 2026-04-10 < today = 2026-04-20 → getAllActive excludes this rule
        val accId = insertAccount("Card", BigDecimal("1000.00"))
        insertRuleWithSeed(
            accId, type = TransactionType.EXPENSE, amount = BigDecimal("100.00"),
            frequency = Frequency.DAILY,
            startDate = LocalDate.of(2026, 4, 1),
            endDate = LocalDate.of(2026, 4, 10),
        )

        useCase(today = LocalDate.of(2026, 4, 20))

        assertAmount(BigDecimal("1000.00"), db.accountDao().getById(accId)!!.balance)
    }

    // ── Already up-to-date rule: nothing generated ────────────────────────────

    @Test
    fun ruleAlreadyUpToDate_generatesNoTransactions() = runTest {
        // lastGeneratedDate = today → lastGenerated.isBefore(today) = false → skip
        val accId = insertAccount("Card", BigDecimal("1000.00"))
        insertRuleWithSeed(
            accId, type = TransactionType.EXPENSE, amount = BigDecimal("100.00"),
            frequency = Frequency.DAILY,
            startDate = LocalDate.of(2026, 4, 1),
            lastGeneratedDate = LocalDate.of(2026, 4, 20),
        )

        useCase(today = LocalDate.of(2026, 4, 20))

        assertAmount(BigDecimal("1000.00"), db.accountDao().getById(accId)!!.balance)
    }

    // ── Monthly rule: generates on correct calendar dates ────────────────────

    @Test
    fun monthlyRule_generatesOnMonthlyBoundaries() = runTest {
        // startDate = 2026-01-15, today = 2026-04-20
        // lastGenerated = 2026-01-14 → from = 2026-01-15
        // monthly dates: 2026-01-15, 2026-02-15, 2026-03-15, 2026-04-15 = 4 dates
        val accId = insertAccount("Card", BigDecimal("0.00"))
        insertRuleWithSeed(
            accId, type = TransactionType.INCOME, amount = BigDecimal("1000.00"),
            frequency = Frequency.MONTHLY, startDate = LocalDate.of(2026, 1, 15),
        )

        useCase(today = LocalDate.of(2026, 4, 20))

        assertAmount(BigDecimal("4000.00"), db.accountDao().getById(accId)!!.balance)
    }

    // ── Rule without seed transaction: skipped by buildAggregate ─────────────

    @Test
    fun ruleWithoutSeedTransaction_isSkipped() = runTest {
        val accId = insertAccount("Card", BigDecimal("1000.00"))
        // Insert rule only, no template transaction linked to it
        db.recurringRuleDao().upsert(
            RecurringRuleEntity(
                frequency = Frequency.DAILY,
                startDate = LocalDate.of(2026, 4, 19),
                endDate = null, lastGeneratedDate = null,
            )
        )

        useCase(today = LocalDate.of(2026, 4, 20))

        // buildAggregate returns null → rule skipped → balance unchanged
        assertAmount(BigDecimal("1000.00"), db.accountDao().getById(accId)!!.balance)
    }

    // ── lastGeneratedDate updated after generation ────────────────────────────

    @Test
    fun afterGeneration_lastGeneratedDateUpdatedToToday() = runTest {
        val accId = insertAccount("Card", BigDecimal("1000.00"))
        val ruleId = insertRuleWithSeed(
            accId, type = TransactionType.INCOME, amount = BigDecimal("100.00"),
            frequency = Frequency.DAILY, startDate = LocalDate.of(2026, 4, 19),
        )

        val today = LocalDate.of(2026, 4, 20)
        useCase(today = today)

        val rule = db.recurringRuleDao().getById(ruleId)!!
        assertEquals(today, rule.lastGeneratedDate)
    }
}
