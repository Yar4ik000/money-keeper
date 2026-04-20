package com.moneykeeper.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneykeeper.core.database.dao.CategoryDao
import com.moneykeeper.core.database.entity.CategoryEntity
import com.moneykeeper.core.domain.model.CategoryType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: CategoryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.categoryDao()
    }

    @After
    fun tearDown() = db.close()

    private fun category(
        name: String,
        type: CategoryType = CategoryType.EXPENSE,
        parentId: Long? = null,
        sortOrder: Int = 0,
    ) = CategoryEntity(
        name = name, type = type, colorHex = "#FF7043", iconName = "Tag",
        parentCategoryId = parentId, sortOrder = sortOrder,
    )

    @Test
    fun upsert_and_getById_returnsCorrectCategory() = runTest {
        val id = dao.upsert(category("Transport"))
        val result = dao.getById(id)
        assertNotNull(result)
        assertEquals("Transport", result!!.name)
        assertEquals(CategoryType.EXPENSE, result.type)
    }

    @Test
    fun observeRootCategories_excludesChildren() = runTest {
        val parentId = dao.upsert(category("Food"))
        dao.upsert(category("Restaurants", parentId = parentId))
        dao.upsert(category("Cafes", parentId = parentId))

        val roots = dao.observeRootCategories().first()
        assertEquals(1, roots.size)
        assertEquals("Food", roots[0].name)
    }

    @Test
    fun observeChildren_returnsSubcategoriesOfGivenParent() = runTest {
        val foodId = dao.upsert(category("Food"))
        val transportId = dao.upsert(category("Transport"))
        dao.upsert(category("Restaurant", parentId = foodId))
        dao.upsert(category("Cafe", parentId = foodId))
        dao.upsert(category("Bus", parentId = transportId))

        val foodChildren = dao.observeChildren(foodId).first()
        assertEquals(2, foodChildren.size)
        assertTrue(foodChildren.all { it.parentCategoryId == foodId })

        val transportChildren = dao.observeChildren(transportId).first()
        assertEquals(1, transportChildren.size)
        assertEquals("Bus", transportChildren[0].name)
    }

    @Test
    fun observeChildren_returnsEmptyForLeafCategory() = runTest {
        val id = dao.upsert(category("Leaf"))
        val children = dao.observeChildren(id).first()
        assertTrue(children.isEmpty())
    }

    @Test
    fun observeByType_filtersIncomeCorrectly() = runTest {
        dao.upsert(category("Salary", type = CategoryType.INCOME))
        dao.upsert(category("Bonus", type = CategoryType.INCOME))
        dao.upsert(category("Food", type = CategoryType.EXPENSE))
        dao.upsert(category("Transfer", type = CategoryType.TRANSFER))

        val income = dao.observeByType("INCOME").first()
        assertEquals(2, income.size)
        assertTrue(income.all { it.type == CategoryType.INCOME })
    }

    @Test
    fun observeByType_filtersExpenseCorrectly() = runTest {
        dao.upsert(category("Food", type = CategoryType.EXPENSE))
        dao.upsert(category("Transport", type = CategoryType.EXPENSE))
        dao.upsert(category("Salary", type = CategoryType.INCOME))

        val expense = dao.observeByType("EXPENSE").first()
        assertEquals(2, expense.size)
        assertTrue(expense.all { it.type == CategoryType.EXPENSE })
    }

    @Test
    fun sortOrder_respected_in_observeAll() = runTest {
        dao.upsert(category("C", sortOrder = 3))
        dao.upsert(category("A", sortOrder = 1))
        dao.upsert(category("B", sortOrder = 2))

        val all = dao.observeAll().first()
        assertEquals(listOf("A", "B", "C"), all.map { it.name })
    }

    @Test
    fun delete_removesCategory() = runTest {
        val id = dao.upsert(category("Temp"))
        val entity = dao.getById(id)!!
        dao.delete(entity)
        assertNull(dao.getById(id))
    }

    @Test
    fun isDefault_persistedCorrectly() = runTest {
        val id = dao.upsert(category("Default").copy(isDefault = true))
        val result = dao.getById(id)!!
        assertTrue(result.isDefault)
    }
}
