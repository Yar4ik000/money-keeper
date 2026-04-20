package com.moneykeeper.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneykeeper.core.database.dao.BudgetDao
import com.moneykeeper.core.database.dao.CategoryDao
import com.moneykeeper.core.database.entity.BudgetEntity
import com.moneykeeper.core.database.entity.CategoryEntity
import com.moneykeeper.core.domain.model.BudgetPeriod
import com.moneykeeper.core.domain.model.CategoryType
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
    private lateinit var categoryDao: CategoryDao
    private var foodId = 0L
    private var transportId = 0L

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        budgetDao = db.budgetDao()
        categoryDao = db.categoryDao()
        foodId = categoryDao.upsert(
            CategoryEntity(name = "Food", type = CategoryType.EXPENSE,
                colorHex = "#FF7043", iconName = "Restaurant")
        )
        transportId = categoryDao.upsert(
            CategoryEntity(name = "Transport", type = CategoryType.EXPENSE,
                colorHex = "#42A5F5", iconName = "DirectionsBus")
        )
    }

    @After
    fun tearDown() = db.close()

    private fun budget(
        catId: Long = foodId,
        amount: BigDecimal = BigDecimal("5000.00"),
        period: BudgetPeriod = BudgetPeriod.MONTHLY,
        currency: String = "RUB",
        accountIds: String? = null,
    ) = BudgetEntity(categoryId = catId, amount = amount, period = period,
        currency = currency, accountIds = accountIds)

    @Test
    fun upsert_and_observeAll_returnsBudget() = runTest {
        budgetDao.upsert(budget(amount = BigDecimal("5000.00")))
        val budgets = budgetDao.observeAll().first()
        assertEquals(1, budgets.size)
        assertEquals(BigDecimal("5000.00"), budgets[0].amount)
    }

    @Test
    fun multipleBudgets_allReturned() = runTest {
        budgetDao.upsert(budget(catId = foodId, amount = BigDecimal("3000.00")))
        budgetDao.upsert(budget(catId = transportId, amount = BigDecimal("1000.00")))
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
        val id1 = budgetDao.upsert(budget(catId = foodId))
        val id2 = budgetDao.upsert(budget(catId = transportId))
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
        val id = budgetDao.upsert(budget(period = BudgetPeriod.WEEKLY))
        assertEquals(BudgetPeriod.WEEKLY, budgetDao.observeAll().first()[0].period)
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
}
