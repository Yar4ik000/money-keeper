package com.moneykeeper.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneykeeper.core.database.dao.AccountDao
import com.moneykeeper.core.database.dao.CategoryDao
import com.moneykeeper.core.database.dao.TransactionDao
import com.moneykeeper.core.database.entity.AccountEntity
import com.moneykeeper.core.database.entity.CategoryEntity
import com.moneykeeper.core.database.entity.TransactionEntity
import com.moneykeeper.core.domain.model.AccountType
import com.moneykeeper.core.domain.model.CategoryType
import com.moneykeeper.core.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class AnalyticsQueryTest {

    private lateinit var db: AppDatabase
    private lateinit var txDao: TransactionDao
    private lateinit var accountDao: AccountDao
    private lateinit var categoryDao: CategoryDao

    private var rubAccountId = 0L
    private var usdAccountId = 0L
    private var foodCategoryId = 0L
    private var transportCategoryId = 0L
    private var salaryId = 0L

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        txDao = db.transactionDao()
        accountDao = db.accountDao()
        categoryDao = db.categoryDao()

        rubAccountId = accountDao.upsert(
            AccountEntity(name = "Card RUB", type = AccountType.CARD, currency = "RUB",
                colorHex = "#000", iconName = "CreditCard",
                balance = BigDecimal("10000"), createdAt = LocalDate.of(2026, 1, 1))
        )
        usdAccountId = accountDao.upsert(
            AccountEntity(name = "Card USD", type = AccountType.CARD, currency = "USD",
                colorHex = "#111", iconName = "CreditCard",
                balance = BigDecimal("500"), createdAt = LocalDate.of(2026, 1, 1))
        )
        foodCategoryId = categoryDao.upsert(
            CategoryEntity(name = "Еда", type = CategoryType.EXPENSE,
                colorHex = "#FF7043", iconName = "Restaurant", sortOrder = 0)
        )
        transportCategoryId = categoryDao.upsert(
            CategoryEntity(name = "Транспорт", type = CategoryType.EXPENSE,
                colorHex = "#42A5F5", iconName = "DirectionsBus", sortOrder = 1)
        )
        salaryId = categoryDao.upsert(
            CategoryEntity(name = "Зарплата", type = CategoryType.INCOME,
                colorHex = "#66BB6A", iconName = "Work", sortOrder = 2)
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insertTx(
        accountId: Long = rubAccountId,
        amount: BigDecimal,
        type: TransactionType,
        categoryId: Long? = null,
        date: LocalDate,
    ) = txDao.upsert(
        TransactionEntity(
            accountId = accountId, toAccountId = null,
            amount = amount, type = type,
            categoryId = categoryId, date = date,
            createdAt = LocalDateTime.of(date.year, date.month, date.dayOfMonth, 12, 0),
        )
    )

    // ─── observeByCategory ────────────────────────────────────────────────────

    @Test
    fun observeByCategory_groupsExpensesByCategory() = runTest {
        insertTx(amount = BigDecimal("500"), type = TransactionType.EXPENSE,
            categoryId = foodCategoryId, date = LocalDate.of(2026, 4, 5))
        insertTx(amount = BigDecimal("300"), type = TransactionType.EXPENSE,
            categoryId = foodCategoryId, date = LocalDate.of(2026, 4, 10))
        insertTx(amount = BigDecimal("200"), type = TransactionType.EXPENSE,
            categoryId = transportCategoryId, date = LocalDate.of(2026, 4, 7))

        val result = txDao.observeByCategory(
            currency = "RUB", from = "2026-04-01", to = "2026-04-30", type = "EXPENSE",
        ).first()

        assertEquals(2, result.size)
        val food = result.first { it.categoryId == foodCategoryId }
        assertEquals(BigDecimal("800.00"), food.total)
        assertEquals(2, food.count)
        val transport = result.first { it.categoryId == transportCategoryId }
        assertEquals(BigDecimal("200.00"), transport.total)
    }

    @Test
    fun observeByCategory_ignoresOtherCurrency() = runTest {
        insertTx(accountId = rubAccountId, amount = BigDecimal("1000"), type = TransactionType.EXPENSE,
            categoryId = foodCategoryId, date = LocalDate.of(2026, 4, 5))
        insertTx(accountId = usdAccountId, amount = BigDecimal("50"), type = TransactionType.EXPENSE,
            categoryId = foodCategoryId, date = LocalDate.of(2026, 4, 5))

        val rub = txDao.observeByCategory(
            currency = "RUB", from = "2026-04-01", to = "2026-04-30", type = "EXPENSE",
        ).first()
        assertEquals(1, rub.size)
        assertEquals(BigDecimal("1000.00"), rub[0].total)

        val usd = txDao.observeByCategory(
            currency = "USD", from = "2026-04-01", to = "2026-04-30", type = "EXPENSE",
        ).first()
        assertEquals(1, usd.size)
        assertEquals(BigDecimal("50.00"), usd[0].total)
    }

    @Test
    fun observeByCategory_groupsNullCategoryUnderZero() = runTest {
        insertTx(amount = BigDecimal("400"), type = TransactionType.EXPENSE,
            categoryId = null, date = LocalDate.of(2026, 4, 3))
        insertTx(amount = BigDecimal("100"), type = TransactionType.EXPENSE,
            categoryId = null, date = LocalDate.of(2026, 4, 15))

        val result = txDao.observeByCategory(
            currency = "RUB", from = "2026-04-01", to = "2026-04-30", type = "EXPENSE",
        ).first()

        assertEquals(1, result.size)
        assertEquals(0L, result[0].categoryId)
        assertEquals(BigDecimal("500.00"), result[0].total)
        assertEquals(2, result[0].count)
    }

    @Test
    fun observeByCategory_separatesIncomeFromExpense() = runTest {
        insertTx(amount = BigDecimal("1000"), type = TransactionType.INCOME,
            categoryId = salaryId, date = LocalDate.of(2026, 4, 1))
        insertTx(amount = BigDecimal("500"), type = TransactionType.EXPENSE,
            categoryId = foodCategoryId, date = LocalDate.of(2026, 4, 5))

        val incomes = txDao.observeByCategory(
            currency = "RUB", from = "2026-04-01", to = "2026-04-30", type = "INCOME",
        ).first()
        assertEquals(1, incomes.size)
        assertEquals(salaryId, incomes[0].categoryId)

        val expenses = txDao.observeByCategory(
            currency = "RUB", from = "2026-04-01", to = "2026-04-30", type = "EXPENSE",
        ).first()
        assertEquals(1, expenses.size)
        assertEquals(foodCategoryId, expenses[0].categoryId)
    }

    // ─── observeByAccount ─────────────────────────────────────────────────────

    @Test
    fun observeByAccount_groupsExpensesByAccount() = runTest {
        val secondRubId = accountDao.upsert(
            AccountEntity(name = "Cash", type = AccountType.CASH, currency = "RUB",
                colorHex = "#222", iconName = "Payments",
                balance = BigDecimal("5000"), createdAt = LocalDate.of(2026, 1, 1))
        )
        insertTx(accountId = rubAccountId, amount = BigDecimal("700"), type = TransactionType.EXPENSE,
            date = LocalDate.of(2026, 4, 5))
        insertTx(accountId = rubAccountId, amount = BigDecimal("300"), type = TransactionType.EXPENSE,
            date = LocalDate.of(2026, 4, 10))
        insertTx(accountId = secondRubId, amount = BigDecimal("200"), type = TransactionType.EXPENSE,
            date = LocalDate.of(2026, 4, 7))

        val result = txDao.observeByAccount(
            currency = "RUB", from = "2026-04-01", to = "2026-04-30", type = "EXPENSE",
        ).first()

        assertEquals(2, result.size)
        val card = result.first { it.accountId == rubAccountId }
        assertEquals(BigDecimal("1000.00"), card.total)
        assertEquals(2, card.count)
        val cash = result.first { it.accountId == secondRubId }
        assertEquals(BigDecimal("200.00"), cash.total)
    }

    @Test
    fun observeByAccount_ignoresOtherCurrency() = runTest {
        insertTx(accountId = rubAccountId, amount = BigDecimal("500"), type = TransactionType.EXPENSE,
            date = LocalDate.of(2026, 4, 1))
        insertTx(accountId = usdAccountId, amount = BigDecimal("30"), type = TransactionType.EXPENSE,
            date = LocalDate.of(2026, 4, 1))

        val rub = txDao.observeByAccount(
            currency = "RUB", from = "2026-04-01", to = "2026-04-30", type = "EXPENSE",
        ).first()
        assertEquals(1, rub.size)
        assertEquals(rubAccountId, rub[0].accountId)
    }

    // ─── observeMonthlyTrend ──────────────────────────────────────────────────

    @Test
    fun observeMonthlyTrend_groupsByYearMonth() = runTest {
        // Feb: 1000 income, 400 expense
        insertTx(amount = BigDecimal("1000"), type = TransactionType.INCOME,
            date = LocalDate.of(2026, 2, 15))
        insertTx(amount = BigDecimal("400"), type = TransactionType.EXPENSE,
            date = LocalDate.of(2026, 2, 20))
        // Mar: 600 expense only
        insertTx(amount = BigDecimal("600"), type = TransactionType.EXPENSE,
            date = LocalDate.of(2026, 3, 10))
        // Apr: 300 expense
        insertTx(amount = BigDecimal("300"), type = TransactionType.EXPENSE,
            date = LocalDate.of(2026, 4, 5))

        val result = txDao.observeMonthlyTrend(
            currency = "RUB", from = "2026-02-01", to = "2026-04-30",
        ).first()

        assertEquals(3, result.size)
        val feb = result.first { it.yearMonth == "2026-02" }
        assertEquals(BigDecimal("1000.00"), feb.income)
        assertEquals(BigDecimal("400.00"), feb.expense)
        val mar = result.first { it.yearMonth == "2026-03" }
        assertEquals(BigDecimal("0"), mar.income)
        assertEquals(BigDecimal("600.00"), mar.expense)
        val apr = result.first { it.yearMonth == "2026-04" }
        assertEquals(BigDecimal("0"), apr.income)
        assertEquals(BigDecimal("300.00"), apr.expense)
    }

    @Test
    fun observeMonthlyTrend_excludesTransactionsOutsideRange() = runTest {
        insertTx(amount = BigDecimal("999"), type = TransactionType.EXPENSE,
            date = LocalDate.of(2026, 1, 31))
        insertTx(amount = BigDecimal("500"), type = TransactionType.EXPENSE,
            date = LocalDate.of(2026, 3, 1))
        insertTx(amount = BigDecimal("999"), type = TransactionType.EXPENSE,
            date = LocalDate.of(2026, 5, 1))

        val result = txDao.observeMonthlyTrend(
            currency = "RUB", from = "2026-02-01", to = "2026-04-30",
        ).first()

        assertEquals(1, result.size)
        assertEquals("2026-03", result[0].yearMonth)
        assertEquals(BigDecimal("500.00"), result[0].expense)
    }

    // ─── observePeriodSummary ─────────────────────────────────────────────────

    @Test
    fun observePeriodSummary_sumsIncomeAndExpensePerCurrency() = runTest {
        insertTx(accountId = rubAccountId, amount = BigDecimal("5000"), type = TransactionType.INCOME,
            date = LocalDate.of(2026, 4, 1))
        insertTx(accountId = rubAccountId, amount = BigDecimal("1500"), type = TransactionType.EXPENSE,
            date = LocalDate.of(2026, 4, 10))
        insertTx(accountId = usdAccountId, amount = BigDecimal("200"), type = TransactionType.INCOME,
            date = LocalDate.of(2026, 4, 5))

        val result = txDao.observePeriodSummary(from = "2026-04-01", to = "2026-04-30").first()

        assertEquals(2, result.size)
        val rub = result.first { it.currency == "RUB" }
        assertEquals(BigDecimal("5000.00"), rub.income)
        assertEquals(BigDecimal("1500.00"), rub.expense)
        val usd = result.first { it.currency == "USD" }
        assertEquals(BigDecimal("200.00"), usd.income)
        assertEquals(BigDecimal("0"), usd.expense)
    }

    @Test
    fun observePeriodSummary_returnsEmptyForPeriodWithNoTransactions() = runTest {
        insertTx(amount = BigDecimal("1000"), type = TransactionType.INCOME,
            date = LocalDate.of(2026, 3, 15))

        val result = txDao.observePeriodSummary(from = "2026-04-01", to = "2026-04-30").first()
        assertTrue(result.isEmpty())
    }
}
