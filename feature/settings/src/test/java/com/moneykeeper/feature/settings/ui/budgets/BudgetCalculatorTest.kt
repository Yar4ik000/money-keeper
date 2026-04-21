package com.moneykeeper.feature.settings.ui.budgets

import com.moneykeeper.core.domain.model.Budget
import com.moneykeeper.core.domain.model.BudgetPeriod
import com.moneykeeper.core.domain.model.Transaction
import com.moneykeeper.core.domain.model.TransactionType
import com.moneykeeper.core.domain.model.TransactionWithMeta
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class BudgetCalculatorTest {

    private val today = LocalDate.of(2026, 4, 21)
    private val now = LocalDateTime.of(2026, 4, 21, 12, 0)

    private fun tx(
        id: Long,
        accountId: Long,
        amount: String,
        type: TransactionType = TransactionType.EXPENSE,
        categoryId: Long? = 1L,
    ) = Transaction(
        id = id,
        accountId = accountId,
        toAccountId = null,
        amount = BigDecimal(amount),
        type = type,
        categoryId = categoryId,
        date = today,
        createdAt = now,
    )

    private fun meta(transaction: Transaction, currency: String) = TransactionWithMeta(
        transaction = transaction,
        accountName = "Acc ${transaction.accountId}",
        accountCurrency = currency,
        categoryName = "Cat",
        categoryColor = "#000000",
        categoryIcon = "",
    )

    private fun budget(
        amount: String = "10000",
        currency: String = "RUB",
        categoryIds: Set<Long> = emptySet(),
        accountIds: Set<Long> = emptySet(),
    ) = Budget(
        id = 1L,
        categoryIds = categoryIds,
        amount = BigDecimal(amount),
        period = BudgetPeriod.MONTHLY,
        currency = currency,
        accountIds = accountIds,
    )

    private fun assertAmount(expected: String, actual: BigDecimal) =
        assertEquals(0, BigDecimal(expected).compareTo(actual))

    @Test
    fun includesOnlyExpenses() {
        val transactions = listOf(
            meta(tx(1, 1, "100"), "RUB"),
            meta(tx(2, 1, "200", type = TransactionType.INCOME), "RUB"),
            meta(tx(3, 1, "50", type = TransactionType.TRANSFER), "RUB"),
        )
        val spent = calculateBudgetSpent(budget(), transactions)
        assertAmount("100", spent)
    }

    @Test
    fun excludesTransactionsInOtherCurrencies() {
        val transactions = listOf(
            meta(tx(1, 1, "100"), "RUB"),
            meta(tx(2, 2, "50"), "USD"),
            meta(tx(3, 3, "70"), "EUR"),
        )
        val spent = calculateBudgetSpent(budget(currency = "RUB"), transactions)
        assertAmount("100", spent)
    }

    @Test
    fun separateBudgetsForEachCurrency_produceSeparateTotals() {
        val transactions = listOf(
            meta(tx(1, 1, "100"), "RUB"),
            meta(tx(2, 1, "200"), "RUB"),
            meta(tx(3, 2, "30"), "USD"),
            meta(tx(4, 2, "20"), "USD"),
        )
        val spentRub = calculateBudgetSpent(budget(currency = "RUB"), transactions)
        val spentUsd = calculateBudgetSpent(budget(currency = "USD"), transactions)
        assertAmount("300", spentRub)
        assertAmount("50", spentUsd)
    }

    @Test
    fun respectsCategoryFilter() {
        val transactions = listOf(
            meta(tx(1, 1, "100", categoryId = 10L), "RUB"),
            meta(tx(2, 1, "200", categoryId = 20L), "RUB"),
            meta(tx(3, 1, "300", categoryId = 30L), "RUB"),
        )
        val spent = calculateBudgetSpent(
            budget(categoryIds = setOf(10L, 30L)),
            transactions,
        )
        assertAmount("400", spent)
    }

    @Test
    fun respectsAccountFilter() {
        val transactions = listOf(
            meta(tx(1, 1, "100"), "RUB"),
            meta(tx(2, 2, "200"), "RUB"),
            meta(tx(3, 3, "300"), "RUB"),
        )
        val spent = calculateBudgetSpent(
            budget(accountIds = setOf(1L, 3L)),
            transactions,
        )
        assertAmount("400", spent)
    }

    @Test
    fun emptyCategoryAndAccountIds_meanAllWithinCurrency() {
        val transactions = listOf(
            meta(tx(1, 1, "100", categoryId = 10L), "RUB"),
            meta(tx(2, 2, "200", categoryId = 20L), "RUB"),
            meta(tx(3, 3, "50"), "USD"),
        )
        val spent = calculateBudgetSpent(budget(currency = "RUB"), transactions)
        assertAmount("300", spent)
    }

    @Test
    fun emptyTransactionList_returnsZero() {
        val spent = calculateBudgetSpent(budget(), emptyList())
        assertAmount("0", spent)
    }

    @Test
    fun accountFilterAndCurrencyFilter_bothApplied() {
        // A budget that targets account #1 in RUB must exclude both
        // (a) account #2 even if it's RUB, and (b) account #1 if its currency diverges.
        val transactions = listOf(
            meta(tx(1, 1, "100"), "RUB"), // include
            meta(tx(2, 2, "200"), "RUB"), // exclude — wrong account
            meta(tx(3, 1, "300"), "USD"), // exclude — wrong currency (shouldn't happen in practice,
                                          // but defends against stale account metadata)
        )
        val spent = calculateBudgetSpent(
            budget(currency = "RUB", accountIds = setOf(1L)),
            transactions,
        )
        assertAmount("100", spent)
    }
}
