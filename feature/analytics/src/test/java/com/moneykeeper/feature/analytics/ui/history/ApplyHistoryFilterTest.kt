package com.moneykeeper.feature.analytics.ui.history

import com.moneykeeper.core.domain.model.Transaction
import com.moneykeeper.core.domain.model.TransactionType
import com.moneykeeper.core.domain.model.TransactionWithMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class ApplyHistoryFilterTest {

    private fun tx(
        id: Long = 1L,
        type: TransactionType = TransactionType.EXPENSE,
        accountId: Long = 10L,
        toAccountId: Long? = null,
        categoryId: Long? = 20L,
        note: String = "",
    ) = Transaction(
        id = id,
        accountId = accountId,
        toAccountId = toAccountId,
        amount = BigDecimal("100"),
        type = type,
        categoryId = categoryId,
        date = LocalDate.now(),
        note = note,
        createdAt = LocalDateTime.now(),
    )

    private fun meta(
        transaction: Transaction,
        accountName: String = "Карта",
        categoryName: String = "Еда",
        toAccountName: String? = null,
    ) = TransactionWithMeta(
        transaction = transaction,
        accountName = accountName,
        accountCurrency = "RUB",
        categoryName = categoryName,
        categoryColor = "#000000",
        categoryIcon = "other",
        toAccountName = toAccountName,
    )

    private val baseFilter = HistoryFilter(
        from = LocalDate.now().minusYears(1),
        to = LocalDate.now().plusYears(1),
    )

    // ── accountIds ───────────────────────────────────────────────────────────

    @Test
    fun `empty accountIds passes all transactions`() {
        val items = listOf(
            meta(tx(accountId = 1L)),
            meta(tx(accountId = 2L)),
        )
        assertEquals(2, applyHistoryFilter(items, baseFilter).size)
    }

    @Test
    fun `accountId filter keeps matching source account`() {
        val items = listOf(
            meta(tx(id = 1L, accountId = 10L)),
            meta(tx(id = 2L, accountId = 20L)),
        )
        val result = applyHistoryFilter(items, baseFilter.copy(accountIds = setOf(10L)))
        assertEquals(listOf(1L), result.map { it.transaction.id })
    }

    @Test
    fun `transfer is included when filter matches recipient (toAccountId)`() {
        val transfer = tx(
            id = 1L,
            type = TransactionType.TRANSFER,
            accountId = 10L,
            toAccountId = 20L,
        )
        val items = listOf(meta(transfer, toAccountName = "Накопления"))
        val result = applyHistoryFilter(items, baseFilter.copy(accountIds = setOf(20L)))
        assertEquals(1, result.size)
    }

    @Test
    fun `transfer is included when filter matches source account`() {
        val transfer = tx(
            id = 1L,
            type = TransactionType.TRANSFER,
            accountId = 10L,
            toAccountId = 20L,
        )
        val items = listOf(meta(transfer, toAccountName = "Накопления"))
        val result = applyHistoryFilter(items, baseFilter.copy(accountIds = setOf(10L)))
        assertEquals(1, result.size)
    }

    @Test
    fun `non-transfer is excluded when filtered by a different accountId`() {
        val expense = tx(id = 1L, type = TransactionType.EXPENSE, accountId = 10L)
        val items = listOf(meta(expense))
        val result = applyHistoryFilter(items, baseFilter.copy(accountIds = setOf(99L)))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `transfer excluded when neither source nor recipient matches filter`() {
        val transfer = tx(
            id = 1L,
            type = TransactionType.TRANSFER,
            accountId = 10L,
            toAccountId = 20L,
        )
        val items = listOf(meta(transfer))
        val result = applyHistoryFilter(items, baseFilter.copy(accountIds = setOf(99L)))
        assertTrue(result.isEmpty())
    }

    // ── types ────────────────────────────────────────────────────────────────

    @Test
    fun `empty types set passes all transaction types`() {
        val items = listOf(
            meta(tx(type = TransactionType.INCOME)),
            meta(tx(type = TransactionType.EXPENSE)),
            meta(tx(type = TransactionType.TRANSFER)),
            meta(tx(type = TransactionType.SAVINGS)),
        )
        assertEquals(4, applyHistoryFilter(items, baseFilter).size)
    }

    @Test
    fun `types filter keeps only matching type`() {
        val items = listOf(
            meta(tx(id = 1L, type = TransactionType.INCOME)),
            meta(tx(id = 2L, type = TransactionType.EXPENSE)),
        )
        val result = applyHistoryFilter(items, baseFilter.copy(types = setOf(TransactionType.INCOME)))
        assertEquals(listOf(1L), result.map { it.transaction.id })
    }

    // ── categoryIds ──────────────────────────────────────────────────────────

    @Test
    fun `empty categoryIds passes all`() {
        val items = listOf(meta(tx(categoryId = 1L)), meta(tx(categoryId = 2L)))
        assertEquals(2, applyHistoryFilter(items, baseFilter).size)
    }

    @Test
    fun `categoryIds filter keeps only matching category`() {
        val items = listOf(
            meta(tx(id = 1L, categoryId = 5L)),
            meta(tx(id = 2L, categoryId = 6L)),
        )
        val result = applyHistoryFilter(items, baseFilter.copy(categoryIds = setOf(5L)))
        assertEquals(listOf(1L), result.map { it.transaction.id })
    }

    // ── search query ─────────────────────────────────────────────────────────

    @Test
    fun `blank query passes all`() {
        val items = listOf(meta(tx(note = "кофе")), meta(tx(note = "такси")))
        assertEquals(2, applyHistoryFilter(items, baseFilter.copy(query = "")).size)
    }

    @Test
    fun `query matches note case-insensitively`() {
        val items = listOf(
            meta(tx(id = 1L, note = "Кофе")),
            meta(tx(id = 2L, note = "Такси")),
        )
        val result = applyHistoryFilter(items, baseFilter.copy(query = "кофе"))
        assertEquals(listOf(1L), result.map { it.transaction.id })
    }

    @Test
    fun `query matches categoryName`() {
        val items = listOf(
            meta(tx(id = 1L), categoryName = "Рестораны"),
            meta(tx(id = 2L), categoryName = "Транспорт"),
        )
        val result = applyHistoryFilter(items, baseFilter.copy(query = "ресторан"))
        assertEquals(listOf(1L), result.map { it.transaction.id })
    }

    @Test
    fun `query matches accountName`() {
        val items = listOf(
            meta(tx(id = 1L), accountName = "Тинькофф"),
            meta(tx(id = 2L), accountName = "Сбер"),
        )
        val result = applyHistoryFilter(items, baseFilter.copy(query = "тинь"))
        assertEquals(listOf(1L), result.map { it.transaction.id })
    }

    @Test
    fun `query matches toAccountName for transfers`() {
        val transfer = tx(id = 1L, type = TransactionType.TRANSFER, accountId = 1L, toAccountId = 2L)
        val items = listOf(
            meta(transfer, toAccountName = "Накопления"),
            meta(tx(id = 2L), toAccountName = null),
        )
        val result = applyHistoryFilter(items, baseFilter.copy(query = "накоп"))
        assertEquals(listOf(1L), result.map { it.transaction.id })
    }

    // ── amount range ─────────────────────────────────────────────────────────

    @Test
    fun `minAmount filters out transactions below threshold`() {
        val items = listOf(
            meta(tx(id = 1L).copy(amount = BigDecimal("50"))),
            meta(tx(id = 2L).copy(amount = BigDecimal("150"))),
            meta(tx(id = 3L).copy(amount = BigDecimal("200"))),
        )
        val result = applyHistoryFilter(items, baseFilter.copy(minAmount = BigDecimal("100")))
        assertEquals(listOf(2L, 3L), result.map { it.transaction.id })
    }

    @Test
    fun `maxAmount filters out transactions above threshold`() {
        val items = listOf(
            meta(tx(id = 1L).copy(amount = BigDecimal("50"))),
            meta(tx(id = 2L).copy(amount = BigDecimal("150"))),
            meta(tx(id = 3L).copy(amount = BigDecimal("300"))),
        )
        val result = applyHistoryFilter(items, baseFilter.copy(maxAmount = BigDecimal("200")))
        assertEquals(listOf(1L, 2L), result.map { it.transaction.id })
    }

    @Test
    fun `min and max together form a closed range`() {
        val items = listOf(
            meta(tx(id = 1L).copy(amount = BigDecimal("99"))),
            meta(tx(id = 2L).copy(amount = BigDecimal("100"))),
            meta(tx(id = 3L).copy(amount = BigDecimal("500"))),
            meta(tx(id = 4L).copy(amount = BigDecimal("501"))),
        )
        val result = applyHistoryFilter(items, baseFilter.copy(minAmount = BigDecimal("100"), maxAmount = BigDecimal("500")))
        assertEquals(listOf(2L, 3L), result.map { it.transaction.id })
    }
}
