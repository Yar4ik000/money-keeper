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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Integration scenarios for transaction CRUD → analytics consistency,
 * transfer type isolation, and hierarchical category (subcategory) analytics.
 *
 * Verifies invariants that span multiple DAOs and confirm analytics reflect
 * real-time state after mutations.
 */
@RunWith(AndroidJUnit4::class)
class TransactionLifecycleTest {

    private lateinit var db: AppDatabase
    private lateinit var accountDao: AccountDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var txDao: TransactionDao

    private var accountId = 0L
    private var foodCatId = 0L     // parent category
    private var fruitsCatId = 0L   // subcategory of food
    private var dairyCatId = 0L    // another subcategory of food
    private var transportCatId = 0L

    private val period = "2026-04-01" to "2026-04-30"
    private val txDate = LocalDate.of(2026, 4, 15)

    @Before
    fun setUp() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accountDao = db.accountDao()
        categoryDao = db.categoryDao()
        txDao = db.transactionDao()

        accountId = accountDao.upsert(
            AccountEntity(name = "Карта", type = AccountType.CARD, currency = "RUB",
                colorHex = "#000", iconName = "CreditCard",
                balance = BigDecimal("20000"), createdAt = LocalDate.of(2026, 1, 1))
        )
        foodCatId = categoryDao.upsert(
            CategoryEntity(name = "Продукты", type = CategoryType.EXPENSE,
                colorHex = "#4CAF50", iconName = "Cart", sortOrder = 0)
        )
        fruitsCatId = categoryDao.upsert(
            CategoryEntity(name = "Фрукты", type = CategoryType.EXPENSE,
                colorHex = "#FFC107", iconName = "Nutrition",
                sortOrder = 1, parentCategoryId = foodCatId)
        )
        dairyCatId = categoryDao.upsert(
            CategoryEntity(name = "Молочные", type = CategoryType.EXPENSE,
                colorHex = "#FFFFFF", iconName = "Water",
                sortOrder = 2, parentCategoryId = foodCatId)
        )
        transportCatId = categoryDao.upsert(
            CategoryEntity(name = "Транспорт", type = CategoryType.EXPENSE,
                colorHex = "#2196F3", iconName = "Bus", sortOrder = 3)
        )
    }

    @After
    fun tearDown() = db.close()

    // ── helpers ───────────────────────────────────────────────────────────────

    private suspend fun insertTx(
        amount: BigDecimal,
        type: TransactionType = TransactionType.EXPENSE,
        categoryId: Long? = null,
        toAccountId: Long? = null,
    ) = txDao.upsert(
        TransactionEntity(
            accountId = accountId, toAccountId = toAccountId,
            amount = amount, type = type,
            categoryId = categoryId, date = txDate,
            note = "", recurringRuleId = null,
            createdAt = LocalDateTime.of(txDate.year, txDate.month, txDate.dayOfMonth, 12, 0),
        )
    )

    private suspend fun categoryTotal(catId: Long): BigDecimal =
        txDao.observeByCategory(
            currency = "RUB",
            from = period.first, to = period.second,
            type = "EXPENSE",
        ).first().firstOrNull { it.categoryId == catId }?.total ?: BigDecimal.ZERO

    private fun assertAmount(expected: BigDecimal, actual: BigDecimal) =
        assertTrue(
            "Expected ${expected.toPlainString()} but got ${actual.toPlainString()}",
            expected.compareTo(actual) == 0,
        )

    // ── transaction CRUD → analytics consistency ──────────────────────────────

    @Test
    fun deleteTransaction_immediatelyUpdatesAggregates() = runTest {
        val id1 = insertTx(BigDecimal("1000"), categoryId = foodCatId)
        val id2 = insertTx(BigDecimal("600"),  categoryId = foodCatId)
        val id3 = insertTx(BigDecimal("400"),  categoryId = foodCatId)

        var rows = txDao.observeByCategory("RUB", period.first, period.second, "EXPENSE").first()
        assertAmount(BigDecimal("2000"), rows.single { it.categoryId == foodCatId }.total)
        assertEquals(3, rows.single { it.categoryId == foodCatId }.count)

        txDao.deleteByIds(listOf(id2, id3))

        rows = txDao.observeByCategory("RUB", period.first, period.second, "EXPENSE").first()
        assertAmount(BigDecimal("1000"), rows.single { it.categoryId == foodCatId }.total)
        assertEquals(1, rows.single { it.categoryId == foodCatId }.count)
    }

    @Test
    fun updateTransaction_changesAggregatesImmediately() = runTest {
        val txId = insertTx(BigDecimal("1500"), categoryId = foodCatId)
        assertAmount(BigDecimal("1500"), categoryTotal(foodCatId))

        // Upsert same id with updated amount
        txDao.upsert(
            TransactionEntity(
                id = txId, accountId = accountId, toAccountId = null,
                amount = BigDecimal("900"), type = TransactionType.EXPENSE,
                categoryId = foodCatId, date = txDate,
                note = "обновлено", recurringRuleId = null,
                createdAt = LocalDateTime.of(txDate.year, txDate.month, txDate.dayOfMonth, 12, 0),
            )
        )

        assertAmount(BigDecimal("900"), categoryTotal(foodCatId))
    }

    @Test
    fun movingTransactionCategory_reflectsInBothCategories() = runTest {
        val txId = insertTx(BigDecimal("800"), categoryId = foodCatId)

        assertAmount(BigDecimal("800"), categoryTotal(foodCatId))
        assertAmount(BigDecimal("0"),   categoryTotal(transportCatId))

        // Move transaction to transport category
        txDao.upsert(
            TransactionEntity(
                id = txId, accountId = accountId, toAccountId = null,
                amount = BigDecimal("800"), type = TransactionType.EXPENSE,
                categoryId = transportCatId, date = txDate,
                note = "", recurringRuleId = null,
                createdAt = LocalDateTime.of(txDate.year, txDate.month, txDate.dayOfMonth, 12, 0),
            )
        )

        assertAmount(BigDecimal("0"),   categoryTotal(foodCatId))
        assertAmount(BigDecimal("800"), categoryTotal(transportCatId))
    }

    @Test
    fun deleteAllTransactionsInCategory_categoryDisappearsFromAggregates() = runTest {
        val id1 = insertTx(BigDecimal("200"), categoryId = foodCatId)
        val id2 = insertTx(BigDecimal("300"), categoryId = foodCatId)

        txDao.deleteByIds(listOf(id1, id2))

        val rows = txDao.observeByCategory("RUB", period.first, period.second, "EXPENSE").first()
        assertTrue(rows.none { it.categoryId == foodCatId })
    }

    // ── transfer type isolation ───────────────────────────────────────────────

    @Test
    fun transfer_notCountedInPeriodSummaryIncomeOrExpense() = runTest {
        val dstId = accountDao.upsert(
            AccountEntity(name = "Наличные", type = AccountType.CASH, currency = "RUB",
                colorHex = "#111", iconName = "Payments",
                balance = BigDecimal("0"), createdAt = LocalDate.of(2026, 1, 1))
        )

        insertTx(BigDecimal("10000"), type = TransactionType.INCOME)
        insertTx(BigDecimal("3000"), type = TransactionType.TRANSFER, toAccountId = dstId)

        val summary = txDao.observePeriodSummary(period.first, period.second).first()
        val rubRow = summary.single { it.currency == "RUB" }

        assertAmount(BigDecimal("10000"), rubRow.income)
        assertAmount(BigDecimal("0"), rubRow.expense)   // transfer must NOT appear as expense
    }

    @Test
    fun transfer_visibleWithTransferFilter_invisibleWithExpenseFilter() = runTest {
        insertTx(BigDecimal("500"), type = TransactionType.EXPENSE, categoryId = foodCatId)
        insertTx(BigDecimal("2000"), type = TransactionType.TRANSFER)

        val transfers = txDao.observe(
            accountId = null, categoryId = null, type = "TRANSFER",
            from = period.first, to = period.second,
        ).first()
        assertEquals(1, transfers.size)
        assertAmount(BigDecimal("2000"), transfers[0].amount)

        val expenses = txDao.observe(
            accountId = null, categoryId = null, type = "EXPENSE",
            from = period.first, to = period.second,
        ).first()
        assertEquals(1, expenses.size)
        assertAmount(BigDecimal("500"), expenses[0].amount)
    }

    @Test
    fun savings_notCountedInExpenseSummary() = runTest {
        insertTx(BigDecimal("5000"), type = TransactionType.SAVINGS)
        insertTx(BigDecimal("1000"), type = TransactionType.EXPENSE, categoryId = foodCatId)

        val summary = txDao.observePeriodSummary(period.first, period.second).first()
        val rubRow = summary.single { it.currency == "RUB" }

        assertAmount(BigDecimal("1000"), rubRow.expense)
        assertAmount(BigDecimal("0"), rubRow.income)
    }

    // ── subcategory analytics ─────────────────────────────────────────────────

    @Test
    fun subcategoryTransactions_groupedSeparatelyFromParent() = runTest {
        insertTx(BigDecimal("1000"), categoryId = foodCatId)    // direct parent
        insertTx(BigDecimal("500"),  categoryId = fruitsCatId)  // subcategory
        insertTx(BigDecimal("300"),  categoryId = dairyCatId)   // subcategory

        val rows = txDao.observeByCategory("RUB", period.first, period.second, "EXPENSE").first()

        // Each category tracked independently — subcategories are NOT rolled up into parent
        assertAmount(BigDecimal("1000"), rows.single { it.categoryId == foodCatId }.total)
        assertAmount(BigDecimal("500"),  rows.single { it.categoryId == fruitsCatId }.total)
        assertAmount(BigDecimal("300"),  rows.single { it.categoryId == dairyCatId }.total)
    }

    @Test
    fun filterByCategoryId_returnsOnlyThatCategorysTransactions() = runTest {
        insertTx(BigDecimal("1000"), categoryId = foodCatId)
        insertTx(BigDecimal("500"),  categoryId = fruitsCatId)
        insertTx(BigDecimal("300"),  categoryId = transportCatId)

        val foodOnly = txDao.observe(
            accountId = null, categoryId = foodCatId, type = null,
            from = period.first, to = period.second,
        ).first()
        assertEquals(1, foodOnly.size)
        assertAmount(BigDecimal("1000"), foodOnly[0].amount)

        val fruitsOnly = txDao.observe(
            accountId = null, categoryId = fruitsCatId, type = null,
            from = period.first, to = period.second,
        ).first()
        assertEquals(1, fruitsOnly.size)
        assertAmount(BigDecimal("500"), fruitsOnly[0].amount)
    }

    @Test
    fun categoryDao_returnsCorrectSubcategoryHierarchy() = runTest {
        val children = categoryDao.observeChildren(foodCatId).first()
        assertEquals(2, children.size)
        assertTrue(children.all { it.parentCategoryId == foodCatId })
        assertTrue(children.any { it.name == "Фрукты" })
        assertTrue(children.any { it.name == "Молочные" })

        val roots = categoryDao.observeRootCategories().first()
        assertTrue(roots.none { it.parentCategoryId != null })
        assertTrue(roots.any { it.id == foodCatId })
        assertTrue(roots.none { it.id == fruitsCatId })
        assertTrue(roots.none { it.id == dairyCatId })
    }

    @Test
    fun subcategoryExpenses_withExplicitCategorySet_matchCorrectly() = runTest {
        // Scenario: budget would cover Фрукты + Молочные explicitly (not via parent rollup).
        // Verify that summing their totals gives the right amount.
        insertTx(BigDecimal("1000"), categoryId = foodCatId)   // NOT in budget
        insertTx(BigDecimal("500"),  categoryId = fruitsCatId) // in budget
        insertTx(BigDecimal("300"),  categoryId = dairyCatId)  // in budget

        val rows = txDao.observeByCategory("RUB", period.first, period.second, "EXPENSE").first()
        val budgetCategoryIds = setOf(fruitsCatId, dairyCatId)

        val budgetSpending = rows
            .filter { it.categoryId in budgetCategoryIds }
            .fold(BigDecimal.ZERO) { acc, row -> acc + row.total }

        assertAmount(BigDecimal("800"), budgetSpending)
    }
}
