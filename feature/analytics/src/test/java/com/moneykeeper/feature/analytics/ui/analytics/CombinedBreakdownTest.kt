package com.moneykeeper.feature.analytics.ui.analytics

import com.moneykeeper.core.domain.analytics.AccountCategorySum
import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.model.AccountType
import com.moneykeeper.core.domain.model.Category
import com.moneykeeper.core.domain.model.CategoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class CombinedBreakdownTest {

    private fun account(id: Long, name: String) = Account(
        id = id, name = name, type = AccountType.CARD, currency = "RUB",
        balance = BigDecimal.ZERO, colorHex = "#000000", iconName = "CreditCard",
        createdAt = LocalDate.of(2026, 1, 1),
    )

    private fun category(id: Long, name: String, parentId: Long? = null) = Category(
        id = id, name = name, type = CategoryType.EXPENSE,
        colorHex = "#FF0000", iconName = "Star", parentCategoryId = parentId,
    )

    private fun sum(accId: Long, catId: Long, total: String, count: Int = 1) =
        AccountCategorySum(accountId = accId, categoryId = catId,
            total = BigDecimal(total), count = count)

    private val acc1 = account(1L, "Card")
    private val acc2 = account(2L, "Cash")
    private val catFood = category(10L, "Еда")
    private val catTransport = category(20L, "Транспорт")
    private val catParent = category(30L, "Авто")
    private val catChild = category(31L, "Бензин", parentId = 30L)

    private val accMap = mapOf(1L to acc1, 2L to acc2)
    private val catMap = mapOf(10L to catFood, 20L to catTransport, 30L to catParent, 31L to catChild)
    private val allCats = listOf(catFood, catTransport, catParent, catChild)

    @Test
    fun `each account gets its own category list`() {
        val sums = listOf(
            sum(1L, 10L, "500"),
            sum(1L, 20L, "200"),
            sum(2L, 10L, "300"),
        )
        val result = buildCombinedBreakdown(sums, allCats, catMap, accMap,
            BigDecimal("1000"), CategoryType.EXPENSE, rollUp = false)

        assertEquals(2, result.size)
        val acc1Result = result.first { it.accountId == 1L }
        assertEquals(2, acc1Result.categories.size)
        val acc2Result = result.first { it.accountId == 2L }
        assertEquals(1, acc2Result.categories.size)
    }

    @Test
    fun `account total is sum of its category totals`() {
        val sums = listOf(
            sum(1L, 10L, "500"),
            sum(1L, 20L, "200"),
        )
        val result = buildCombinedBreakdown(sums, allCats, catMap, accMap,
            BigDecimal("700"), CategoryType.EXPENSE, rollUp = false)

        assertEquals(1, result.size)
        assertEquals(0, BigDecimal("700").compareTo(result[0].total))
    }

    @Test
    fun `account percentage is relative to allTotal`() {
        val sums = listOf(
            sum(1L, 10L, "300"),
            sum(2L, 10L, "700"),
        )
        val result = buildCombinedBreakdown(sums, allCats, catMap, accMap,
            BigDecimal("1000"), CategoryType.EXPENSE, rollUp = false)

        val acc1Result = result.first { it.accountId == 1L }
        assertEquals(30f, acc1Result.percentage, 0.01f)
        val acc2Result = result.first { it.accountId == 2L }
        assertEquals(70f, acc2Result.percentage, 0.01f)
    }

    @Test
    fun `categories rolled up to roots within each account when rollUp is true`() {
        val sums = listOf(
            sum(1L, 31L, "400"),
            sum(1L, 30L, "100"),
        )
        val result = buildCombinedBreakdown(sums, allCats, catMap, accMap,
            BigDecimal("500"), CategoryType.EXPENSE, rollUp = true)

        assertEquals(1, result.size)
        assertEquals(1, result[0].categories.size)
        assertEquals(30L, result[0].categories[0].category.id)
        assertEquals(0, BigDecimal("500").compareTo(result[0].categories[0].total))
    }

    @Test
    fun `categories not rolled up when rollUp is false`() {
        val sums = listOf(
            sum(1L, 31L, "400"),
            sum(1L, 30L, "100"),
        )
        val result = buildCombinedBreakdown(sums, allCats, catMap, accMap,
            BigDecimal("500"), CategoryType.EXPENSE, rollUp = false)

        assertEquals(1, result.size)
        assertEquals(2, result[0].categories.size)
    }

    @Test
    fun `rollup in account 1 does not affect account 2 categories`() {
        val sums = listOf(
            sum(1L, 31L, "200"),
            sum(2L, 30L, "100"),
            sum(2L, 31L, "50"),
        )
        val result = buildCombinedBreakdown(sums, allCats, catMap, accMap,
            BigDecimal("350"), CategoryType.EXPENSE, rollUp = true)

        val acc2Result = result.first { it.accountId == 2L }
        assertEquals(1, acc2Result.categories.size)
        assertEquals(0, BigDecimal("150").compareTo(acc2Result.categories[0].total))
    }

    @Test
    fun `unknown accounts are excluded`() {
        val sums = listOf(
            sum(999L, 10L, "500"),
            sum(1L, 10L, "200"),
        )
        val result = buildCombinedBreakdown(sums, allCats, catMap, accMap,
            BigDecimal("700"), CategoryType.EXPENSE, rollUp = false)

        assertEquals(1, result.size)
        assertEquals(1L, result[0].accountId)
    }

    @Test
    fun `empty input returns empty list`() {
        val result = buildCombinedBreakdown(emptyList(), allCats, catMap, accMap,
            BigDecimal.ZERO, CategoryType.EXPENSE, rollUp = false)
        assertTrue(result.isEmpty())
    }
}
