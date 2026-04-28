package com.moneykeeper.feature.analytics.ui.analytics

import com.moneykeeper.core.domain.analytics.CategorySum
import com.moneykeeper.core.domain.model.Category
import com.moneykeeper.core.domain.model.CategoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class RollUpCategorySumsTest {

    private fun cat(id: Long, parentId: Long?) = Category(
        id = id, name = "cat$id", type = CategoryType.EXPENSE,
        colorHex = "#000000", iconName = "other", parentCategoryId = parentId,
    )

    private fun sum(categoryId: Long, total: String, count: Int = 1) =
        CategorySum(categoryId = categoryId, total = BigDecimal(total), count = count)

    // ── basic rollup ──────────────────────────────────────────────────────────

    @Test
    fun `child transactions roll up to root`() {
        val cats = listOf(cat(1, null), cat(2, 1), cat(3, 1))
        val sums = listOf(sum(2, "300", count = 2), sum(3, "200", count = 1))

        val result = rollUpCategorySums(sums, cats)

        assertEquals(1, result.size)
        val root = result.single()
        assertEquals(1L, root.categoryId)
        assertEquals(0, BigDecimal("500").compareTo(root.total))
        assertEquals(3, root.count)
    }

    @Test
    fun `grandchild transactions roll up to root across two levels`() {
        // Транспорт(1) → Авто(2) → Бензин(3)
        val cats = listOf(cat(1, null), cat(2, 1), cat(3, 2))
        val sums = listOf(
            sum(1, "100"),            // directly on root
            sum(2, "200"),            // depth 1
            sum(3, "300", count = 2), // depth 2
        )

        val result = rollUpCategorySums(sums, cats)

        assertEquals(1, result.size)
        val root = result.single()
        assertEquals(1L, root.categoryId)
        assertEquals(0, BigDecimal("600").compareTo(root.total))
        assertEquals(4, root.count)
    }

    @Test
    fun `transactions from different root categories are kept separate`() {
        val cats = listOf(cat(1, null), cat(2, null), cat(3, 1), cat(4, 2))
        val sums = listOf(sum(3, "100"), sum(4, "200"))

        val result = rollUpCategorySums(sums, cats)

        assertEquals(2, result.size)
        val root1 = result.first { it.categoryId == 1L }
        val root2 = result.first { it.categoryId == 2L }
        assertEquals(0, BigDecimal("100").compareTo(root1.total))
        assertEquals(0, BigDecimal("200").compareTo(root2.total))
    }

    @Test
    fun `root-level transactions are not moved`() {
        val cats = listOf(cat(1, null), cat(2, null))
        val sums = listOf(sum(1, "400", count = 3), sum(2, "150"))

        val result = rollUpCategorySums(sums, cats)

        assertEquals(2, result.size)
        assertEquals(0, BigDecimal("400").compareTo(result.first { it.categoryId == 1L }.total))
        assertEquals(0, BigDecimal("150").compareTo(result.first { it.categoryId == 2L }.total))
    }

    @Test
    fun `empty sums returns empty list`() {
        val cats = listOf(cat(1, null), cat(2, 1))
        assertTrue(rollUpCategorySums(emptyList(), cats).isEmpty())
    }

    @Test
    fun `no-category id zero is not moved`() {
        val cats = listOf(cat(1, null))
        val sums = listOf(sum(0, "500", count = 4))

        val result = rollUpCategorySums(sums, cats)

        assertEquals(1, result.size)
        assertEquals(0L, result[0].categoryId)
        assertEquals(0, BigDecimal("500").compareTo(result[0].total))
    }
}
