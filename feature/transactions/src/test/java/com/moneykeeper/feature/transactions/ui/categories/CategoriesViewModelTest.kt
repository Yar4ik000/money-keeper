package com.moneykeeper.feature.transactions.ui.categories

import com.moneykeeper.core.domain.model.Category
import com.moneykeeper.core.domain.model.CategoryType
import com.moneykeeper.core.domain.repository.CategoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun cat(id: Long, name: String, parentId: Long?, sortOrder: Int) = Category(
        id = id,
        name = name,
        type = CategoryType.EXPENSE,
        colorHex = "#000000",
        iconName = "other",
        parentCategoryId = parentId,
        sortOrder = sortOrder,
    )

    private fun repo(list: List<Category>) = object : CategoryRepository {
        override fun observeAll(): Flow<List<Category>> = MutableStateFlow(list)
        override fun observeByType(type: CategoryType) = throw UnsupportedOperationException()
        override fun observeRootCategories() = throw UnsupportedOperationException()
        override suspend fun getById(id: Long): Category? = list.find { it.id == id }
        override suspend fun save(category: Category): Long = 0L
        override suspend fun delete(id: Long) = Unit
    }

    @Test
    fun `children render immediately after their parent even when sortOrder interleaves`() = runTest {
        // Mirrors the real bug: subcategories of "Еда" were created after other root
        // categories, so their raw sortOrder falls between unrelated roots. A naive
        // ORDER BY sortOrder alone (as in CategoryDao.observeAll) places them between
        // "Здоровье" and "ЖКХ" — the screen then visually glues them under "Здоровье".
        val input = listOf(
            cat(id = 1, name = "Еда",          parentId = null, sortOrder = 1),
            cat(id = 2, name = "Транспорт",    parentId = null, sortOrder = 2),
            cat(id = 3, name = "Здоровье",     parentId = null, sortOrder = 3),
            cat(id = 4, name = "Яндекс Лавка", parentId = 1,    sortOrder = 4),
            cat(id = 5, name = "Яндекс Еда",   parentId = 1,    sortOrder = 5),
            cat(id = 6, name = "ЖКХ",          parentId = null, sortOrder = 6),
        )

        val vm = CategoriesViewModel(repo(input))
        val ordered = vm.categories.first { it.isNotEmpty() }.map { it.name }

        assertEquals(
            listOf("Еда", "Яндекс Лавка", "Яндекс Еда", "Транспорт", "Здоровье", "ЖКХ"),
            ordered,
        )
    }

    @Test
    fun `orphan child with dangling parentId is silently dropped from the list`() = runTest {
        // Documents current behavior: the grouping logic emits only roots + their
        // direct children. A child pointing to a missing parent is invisible in the
        // UI even though it still exists in the database. If someone changes this
        // (e.g., to surface orphans at the end), this test must fire loudly — the
        // user would otherwise quietly "lose" data.
        val input = listOf(
            cat(id = 1, name = "Еда",    parentId = null, sortOrder = 1),
            cat(id = 2, name = "Orphan", parentId = 999,  sortOrder = 2),
        )

        val vm = CategoriesViewModel(repo(input))
        val ordered = vm.categories.first { it.isNotEmpty() }.map { it.name }

        assertEquals(listOf("Еда"), ordered)
    }
}
