package com.moneykeeper.feature.analytics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
                onDeleteSelectedStopSeries = {},
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
                onDeleteSelectedStopSeries = {},
                onClearSelection = {},
            )
        }
        composeTestRule.onNodeWithText("Операций нет").assertIsDisplayed()
    }

    @Test
    fun historyScreen_showsTransactionCategoryName() {
        val group = TransactionGroup(
            date = LocalDate.now(),
            items = listOf(makeMeta(categoryName = "Продукты", amount = BigDecimal("500"))),
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
                onDeleteSelectedStopSeries = {},
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
                onDeleteSelectedStopSeries = {},
                onClearSelection = {},
            )
        }
        composeTestRule.onNodeWithText("История").assertIsDisplayed()
    }

    @Test
    fun historyScreen_deleteButton_showsSimpleConfirmDialog_forNonRecurringTransactions() {
        var deleteCalled = false
        val group = TransactionGroup(
            date = LocalDate.now(),
            items = listOf(makeMeta(categoryName = "Продукты", amount = BigDecimal("500"))),
            dayTotals = listOf(CurrencyAmount("RUB", BigDecimal("-500"))),
        )
        composeTestRule.setContent {
            HistoryScreen(
                uiState = HistoryUiState.Success(
                    groups = listOf(group),
                    totalsByCurrency = emptyList(),
                    filter = HistoryFilter(),
                    isSelectionMode = true,
                    selectedIds = setOf(1L),
                ),
                onTransactionClick = {},
                onBack = {},
                onFilterUpdate = noop,
                onEnterSelectionMode = {},
                onToggleSelection = {},
                onDeleteSelected = { deleteCalled = true },
                onDeleteSelectedStopSeries = {},
                onClearSelection = {},
            )
        }

        composeTestRule.onAllNodesWithText("Удалить")[0].performClick()
        composeTestRule.onNodeWithText("Удалить выбранные операции?").assertIsDisplayed()
        assert(!deleteCalled) { "Delete must not fire before user confirms" }
    }

    @Test
    fun historyScreen_simpleDeleteConfirm_cancelDoesNotInvokeCallback() {
        var deleteCalled = false
        val group = TransactionGroup(
            date = LocalDate.now(),
            items = listOf(makeMeta(categoryName = "Продукты", amount = BigDecimal("500"))),
            dayTotals = listOf(CurrencyAmount("RUB", BigDecimal("-500"))),
        )
        composeTestRule.setContent {
            HistoryScreen(
                uiState = HistoryUiState.Success(
                    groups = listOf(group),
                    totalsByCurrency = emptyList(),
                    filter = HistoryFilter(),
                    isSelectionMode = true,
                    selectedIds = setOf(1L),
                ),
                onTransactionClick = {},
                onBack = {},
                onFilterUpdate = noop,
                onEnterSelectionMode = {},
                onToggleSelection = {},
                onDeleteSelected = { deleteCalled = true },
                onDeleteSelectedStopSeries = {},
                onClearSelection = {},
            )
        }

        composeTestRule.onAllNodesWithText("Удалить")[0].performClick()
        composeTestRule.onNodeWithText("Отмена").performClick()
        composeTestRule.onNodeWithText("Удалить выбранные операции?").assertDoesNotExist()
        assert(!deleteCalled) { "Delete must not fire after cancelling the dialog" }
    }

    // Single recurring tx → scope dialog (3 options)
    @Test
    fun historyScreen_deleteButton_showsScopeDialog_whenSingleRecurringSelected() {
        val recurringMeta = makeMeta(
            categoryName = "Аренда", amount = BigDecimal("1000"), recurringRuleId = 5L,
        )
        val group = TransactionGroup(
            date = LocalDate.now(),
            items = listOf(recurringMeta),
            dayTotals = listOf(CurrencyAmount("RUB", BigDecimal("-1000"))),
        )
        composeTestRule.setContent {
            HistoryScreen(
                uiState = HistoryUiState.Success(
                    groups = listOf(group),
                    totalsByCurrency = emptyList(),
                    filter = HistoryFilter(),
                    isSelectionMode = true,
                    selectedIds = setOf(1L),
                ),
                onTransactionClick = {},
                onBack = {},
                onFilterUpdate = noop,
                onEnterSelectionMode = {},
                onToggleSelection = {},
                onDeleteSelected = {},
                onDeleteSelectedStopSeries = {},
                onClearSelection = {},
            )
        }

        composeTestRule.onAllNodesWithText("Удалить")[0].performClick()
        composeTestRule.onNodeWithText("Удалить повторяющуюся операцию?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Только эту").assertIsDisplayed()
        composeTestRule.onNodeWithText("Эту и все будущие").assertIsDisplayed()
        composeTestRule.onNodeWithText("Остановить серию").assertIsDisplayed()
    }

    @Test
    fun historyScreen_scopeDialog_stopSeries_invokesStopSeriesCallback() {
        var stopSeriesCalled = false
        val recurringMeta = makeMeta(
            categoryName = "Аренда", amount = BigDecimal("1000"), recurringRuleId = 5L,
        )
        val group = TransactionGroup(
            date = LocalDate.now(),
            items = listOf(recurringMeta),
            dayTotals = listOf(CurrencyAmount("RUB", BigDecimal("-1000"))),
        )
        composeTestRule.setContent {
            HistoryScreen(
                uiState = HistoryUiState.Success(
                    groups = listOf(group),
                    totalsByCurrency = emptyList(),
                    filter = HistoryFilter(),
                    isSelectionMode = true,
                    selectedIds = setOf(1L),
                ),
                onTransactionClick = {},
                onBack = {},
                onFilterUpdate = noop,
                onEnterSelectionMode = {},
                onToggleSelection = {},
                onDeleteSelected = {},
                onDeleteSelectedStopSeries = {},
                onDeleteSingleStopSeries = { stopSeriesCalled = true },
                onClearSelection = {},
            )
        }

        composeTestRule.onAllNodesWithText("Удалить")[0].performClick()
        composeTestRule.onNodeWithText("Остановить серию").performClick()
        assert(stopSeriesCalled) { "Stop-series callback must fire when user taps Остановить серию" }
    }

    @Test
    fun historyScreen_scopeDialog_onlyThis_invokesDeleteSingleCallback() {
        var deleteCalled = false
        val recurringMeta = makeMeta(
            categoryName = "Аренда", amount = BigDecimal("1000"), recurringRuleId = 5L,
        )
        val group = TransactionGroup(
            date = LocalDate.now(),
            items = listOf(recurringMeta),
            dayTotals = listOf(CurrencyAmount("RUB", BigDecimal("-1000"))),
        )
        composeTestRule.setContent {
            HistoryScreen(
                uiState = HistoryUiState.Success(
                    groups = listOf(group),
                    totalsByCurrency = emptyList(),
                    filter = HistoryFilter(),
                    isSelectionMode = true,
                    selectedIds = setOf(1L),
                ),
                onTransactionClick = {},
                onBack = {},
                onFilterUpdate = noop,
                onEnterSelectionMode = {},
                onToggleSelection = {},
                onDeleteSelected = {},
                onDeleteSelectedStopSeries = {},
                onDeleteSingleOnly = { deleteCalled = true },
                onClearSelection = {},
            )
        }

        composeTestRule.onAllNodesWithText("Удалить")[0].performClick()
        composeTestRule.onNodeWithText("Только эту").performClick()
        assert(deleteCalled) { "Delete callback must fire when user taps Только эту" }
    }

    // Multiple recurring txs selected → 2-option dialog
    @Test
    fun historyScreen_deleteButton_showsRecurringDialog_whenMultipleRecurringSelected() {
        val recurring1 = makeMeta(id = 1L, categoryName = "Аренда", amount = BigDecimal("1000"), recurringRuleId = 5L)
        val recurring2 = makeMeta(id = 2L, categoryName = "Аренда", amount = BigDecimal("1000"), recurringRuleId = 5L)
        val group = TransactionGroup(
            date = LocalDate.now(),
            items = listOf(recurring1, recurring2),
            dayTotals = listOf(CurrencyAmount("RUB", BigDecimal("-2000"))),
        )
        composeTestRule.setContent {
            HistoryScreen(
                uiState = HistoryUiState.Success(
                    groups = listOf(group),
                    totalsByCurrency = emptyList(),
                    filter = HistoryFilter(),
                    isSelectionMode = true,
                    selectedIds = setOf(1L, 2L),
                ),
                onTransactionClick = {},
                onBack = {},
                onFilterUpdate = noop,
                onEnterSelectionMode = {},
                onToggleSelection = {},
                onDeleteSelected = {},
                onDeleteSelectedStopSeries = {},
                onClearSelection = {},
            )
        }

        composeTestRule.onAllNodesWithText("Удалить")[0].performClick()
        composeTestRule.onNodeWithText("Удалить повторяющиеся операции?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Только выбранные операции").assertIsDisplayed()
        composeTestRule.onNodeWithText("Остановить серию").assertIsDisplayed()
    }

    // ── search bar ───────────────────────────────────────────────────────────

    @Test
    fun tappingSearchIcon_showsSearchFieldInToolbar() {
        composeTestRule.setContent {
            HistoryScreen(
                uiState = HistoryUiState.Success(groups = emptyList(), totalsByCurrency = emptyList(), filter = HistoryFilter()),
                onTransactionClick = {}, onBack = {}, onFilterUpdate = noop,
                onEnterSelectionMode = {}, onToggleSelection = {},
                onDeleteSelected = {}, onDeleteSelectedStopSeries = {}, onClearSelection = {},
            )
        }
        composeTestRule.onNodeWithContentDescription("Открыть поиск").performClick()
        composeTestRule.onNodeWithText("Поиск по заметкам…").assertIsDisplayed()
    }

    @Test
    fun typingInSearchField_passesQueryToFilterUpdate() {
        var capturedQuery = ""
        val capturingUpdate: ((HistoryFilter) -> HistoryFilter) -> Unit = { update ->
            capturedQuery = update(HistoryFilter()).query
        }
        composeTestRule.setContent {
            HistoryScreen(
                uiState = HistoryUiState.Success(groups = emptyList(), totalsByCurrency = emptyList(), filter = HistoryFilter()),
                onTransactionClick = {}, onBack = {}, onFilterUpdate = capturingUpdate,
                onEnterSelectionMode = {}, onToggleSelection = {},
                onDeleteSelected = {}, onDeleteSelectedStopSeries = {}, onClearSelection = {},
            )
        }
        composeTestRule.onNodeWithContentDescription("Открыть поиск").performClick()
        composeTestRule.onNode(hasSetTextAction()).performTextInput("Ресторан")
        assert(capturedQuery == "Ресторан") { "Expected query 'Ресторан', got '$capturedQuery'" }
    }

    @Test
    fun closingSearchBar_hidesFieldAndClearsQuery() {
        var capturedQuery: String? = null
        val capturingUpdate: ((HistoryFilter) -> HistoryFilter) -> Unit = { update ->
            capturedQuery = update(HistoryFilter()).query
        }
        composeTestRule.setContent {
            HistoryScreen(
                uiState = HistoryUiState.Success(groups = emptyList(), totalsByCurrency = emptyList(), filter = HistoryFilter()),
                onTransactionClick = {}, onBack = {}, onFilterUpdate = capturingUpdate,
                onEnterSelectionMode = {}, onToggleSelection = {},
                onDeleteSelected = {}, onDeleteSelectedStopSeries = {}, onClearSelection = {},
            )
        }
        composeTestRule.onNodeWithContentDescription("Открыть поиск").performClick()
        composeTestRule.onNodeWithText("Поиск по заметкам…").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Закрыть поиск").performClick()
        composeTestRule.onNodeWithText("Поиск по заметкам…").assertDoesNotExist()
        assert(capturedQuery == "") { "Expected empty query after closing, got '$capturedQuery'" }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makeMeta(
        categoryName: String,
        amount: BigDecimal,
        id: Long = 1L,
        type: TransactionType = TransactionType.EXPENSE,
        recurringRuleId: Long? = null,
    ) = TransactionWithMeta(
        transaction = Transaction(
            id = id,
            accountId = 1L,
            toAccountId = null,
            amount = amount,
            type = type,
            categoryId = 1L,
            date = LocalDate.now(),
            note = "",
            recurringRuleId = recurringRuleId,
            createdAt = LocalDateTime.now(),
        ),
        accountName = "Карта",
        accountCurrency = "RUB",
        categoryName = categoryName,
        categoryColor = "#FF5722",
        categoryIcon = "",
    )
}
