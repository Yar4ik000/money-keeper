package com.moneykeeper.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneykeeper.core.database.dao.BudgetDao
import com.moneykeeper.core.database.entity.BudgetEntity
import com.moneykeeper.core.domain.model.BudgetPeriod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class BudgetDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var budgetDao: BudgetDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        budgetDao = db.budgetDao()
    }

    @After
    fun tearDown() = db.close()

    private fun budget(
        categoryIds: String? = null,
        amount: BigDecimal = BigDecimal("5000.00"),
        period: BudgetPeriod = BudgetPeriod.MONTHLY,
        currency: String = "RUB",
        accountIds: String? = null,
    ) = BudgetEntity(
        categoryIds = categoryIds, amount = amount, period = period,
        currency = currency, accountIds = accountIds,
    )

    @Test
    fun upsert_and_observeAll_returnsBudget() = runTest {
        budgetDao.upsert(budget(amount = BigDecimal("5000.00")))
        val budgets = budgetDao.observeAll().first()
        assertEquals(1, budgets.size)
        assertEquals(BigDecimal("5000.00"), budgets[0].amount)
    }

    @Test
    fun multipleBudgets_allReturned() = runTest {
        budgetDao.upsert(budget(amount = BigDecimal("3000.00")))
        budgetDao.upsert(budget(amount = BigDecimal("1000.00")))
        assertEquals(2, budgetDao.observeAll().first().size)
    }

    @Test
    fun deleteById_removesBudget() = runTest {
        val id = budgetDao.upsert(budget())
        budgetDao.deleteById(id)
        assertTrue(budgetDao.observeAll().first().isEmpty())
    }

    @Test
    fun deleteById_removesOnlyTargetBudget() = runTest {
        val id1 = budgetDao.upsert(budget(amount = BigDecimal("3000.00")))
        val id2 = budgetDao.upsert(budget(amount = BigDecimal("1000.00")))
        budgetDao.deleteById(id1)
        val remaining = budgetDao.observeAll().first()
        assertEquals(1, remaining.size)
        assertEquals(id2, remaining[0].id)
    }

    @Test
    fun upsert_updatesExistingBudgetAmount() = runTest {
        val id = budgetDao.upsert(budget(amount = BigDecimal("3000.00")))
        budgetDao.upsert(budget(amount = BigDecimal("6000.00")).copy(id = id))
        val budgets = budgetDao.observeAll().first()
        assertEquals(1, budgets.size)
        assertEquals(BigDecimal("6000.00"), budgets[0].amount)
    }

    @Test
    fun weeklyPeriod_persistedCorrectly() = runTest {
        budgetDao.upsert(budget(period = BudgetPeriod.WEEKLY))
        assertEquals(BudgetPeriod.WEEKLY, budgetDao.observeAll().first()[0].period)
    }

    @Test
    fun categoryIds_nullByDefault() = runTest {
        budgetDao.upsert(budget(categoryIds = null))
        assertNull(budgetDao.observeAll().first()[0].categoryIds)
    }

    @Test
    fun categoryIds_singleId_persistedCorrectly() = runTest {
        budgetDao.upsert(budget(categoryIds = "42"))
        assertEquals("42", budgetDao.observeAll().first()[0].categoryIds)
    }

    @Test
    fun categoryIds_multipleIds_persistedCorrectly() = runTest {
        budgetDao.upsert(budget(categoryIds = "1,2,3"))
        assertEquals("1,2,3", budgetDao.observeAll().first()[0].categoryIds)
    }

    @Test
    fun accountIds_nullByDefault() = runTest {
        budgetDao.upsert(budget(accountIds = null))
        assertNull(budgetDao.observeAll().first()[0].accountIds)
    }

    @Test
    fun accountIds_persistedWhenSet() = runTest {
        budgetDao.upsert(budget(accountIds = "1,2,3"))
        assertEquals("1,2,3", budgetDao.observeAll().first()[0].accountIds)
    }

    @Test
    fun budgetWithBothCategoryAndAccountFilters_persistedCorrectly() = runTest {
        budgetDao.upsert(budget(categoryIds = "5,6", accountIds = "10,11"))
        val b = budgetDao.observeAll().first()[0]
        assertEquals("5,6", b.categoryIds)
        assertEquals("10,11", b.accountIds)
    }
}
