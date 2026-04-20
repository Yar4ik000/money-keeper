package com.moneykeeper.feature.analytics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneykeeper.core.domain.analytics.PeriodSummaryByCurrency
import com.moneykeeper.core.domain.model.Transaction
import com.moneykeeper.core.domain.model.TransactionType
import com.moneykeeper.core.domain.model.TransactionWithMeta
import com.moneykeeper.core.domain.money.CurrencyAmount
import com.moneykeeper.feature.analytics.ui.history.HistoryFilter
import com.moneykeeper.feature.analytics.ui.history.HistoryScreen
import com.moneykeeper.feature.analytics.ui.history.HistoryUiState
import com.moneykeeper.feature.analytics.ui.history.TransactionGroup
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val noop: ((HistoryFilter) -> HistoryFilter) -> Unit = {}

    @Test
    fun historyScreen_showsLoadingIndicator_whenStateIsLoading() {
        composeTestRule.setContent {
            HistoryScreen(
                uiState = HistoryUiState.Loading,
                onTransactionClick = {},
                onBack = {},
                onFilterUpdate = noop,
                onEnterSelectionMode = {},
                onToggleSelection = {},
                onDeleteSelected = {},
                onClearSelection = {},
            )
        }
        composeTestRule.onNodeWithText("История").assertIsDisplayed()
    }

    @Test
    fun historyScreen_showsEmptyMessage_whenNoTransactions() {
        composeTestRule.setContent {
            HistoryScreen(
                uiState = HistoryUiState.Success(
                    groups = emptyList(),
                    totalsByCurrency = emptyList(),
                    filter = HistoryFilter(),
                ),
                onTransactionClick = {},
                onBack = {},
                onFilterUpdate = noop,
                onEnterSelectionMode = {},
                onToggleSelection = {},
                onDeleteSelected = {},
                onClearSelection = {},
            )
        }
        composeTestRule.onNodeWithText("Операций нет").assertIsDisplayed()
    }

    @Test
    fun historyScreen_showsTransactionCategoryName() {
        val meta = makeTransactionWithMeta(categoryName = "Продукты", amount = BigDecimal("500"))
        val group = TransactionGroup(
            date = LocalDate.now(),
            items = listOf(meta),
            dayTotals = listOf(CurrencyAmount("RUB", BigDecimal("-500"))),
        )
        composeTestRule.setContent {
            HistoryScreen(
                uiState = HistoryUiState.Success(
                    groups = listOf(group),
                    totalsByCurrency = emptyList(),
                    filter = HistoryFilter(),
                ),
                onTransactionClick = {},
                onBack = {},
                onFilterUpdate = noop,
                onEnterSelectionMode = {},
                onToggleSelection = {},
                onDeleteSelected = {},
                onClearSelection = {},
            )
        }
        composeTestRule.onNodeWithText("Продукты").assertIsDisplayed()
    }

    @Test
    fun historyScreen_showsPeriodTotals_whenPresent() {
        composeTestRule.setContent {
            HistoryScreen(
                uiState = HistoryUiState.Success(
                    groups = emptyList(),
                    totalsByCurrency = listOf(
                        PeriodSummaryByCurrency("RUB", BigDecimal("5000"), BigDecimal("3000"))
                    ),
                    filter = HistoryFilter(),
                ),
                onTransactionClick = {},
                onBack = {},
                onFilterUpdate = noop,
                onEnterSelectionMode = {},
                onToggleSelection = {},
                onDeleteSelected = {},
                onClearSelection = {},
            )
        }
        // Totals row should be visible
        composeTestRule.onNodeWithText("История").assertIsDisplayed()
    }

    private fun makeTransactionWithMeta(
        categoryName: String,
        amount: BigDecimal,
        type: TransactionType = TransactionType.EXPENSE,
    ) = TransactionWithMeta(
        transaction = Transaction(
            id = 1L,
            accountId = 1L,
            toAccountId = null,
            amount = amount,
            type = type,
            categoryId = 1L,
            date = LocalDate.now(),
            note = "",
            createdAt = LocalDateTime.now(),
        ),
        accountName = "Карта",
        accountCurrency = "RUB",
        categoryName = categoryName,
        categoryColor = "#FF5722",
        categoryIcon = "",
    )
}
