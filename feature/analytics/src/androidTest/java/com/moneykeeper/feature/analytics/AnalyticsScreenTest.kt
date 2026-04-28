package com.moneykeeper.feature.analytics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneykeeper.core.domain.model.Category
import com.moneykeeper.core.domain.model.CategoryType
import com.moneykeeper.feature.analytics.ui.analytics.AccountCategoryBreakdown
import com.moneykeeper.feature.analytics.ui.analytics.AnalyticsScreen
import com.moneykeeper.feature.analytics.ui.analytics.AnalyticsUiState
import com.moneykeeper.feature.analytics.ui.analytics.CategoryExpense
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class AnalyticsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun category(id: Long, name: String) = Category(
        id = id, name = name, type = CategoryType.EXPENSE,
        colorHex = "#FF5722", iconName = "Star",
    )

    private fun catExpense(id: Long, name: String, total: String = "100") = CategoryExpense(
        category = category(id, name),
        total = BigDecimal(total),
        percentage = 100f,
        transactionCount = 1,
    )

    private fun accCatBreakdown(
        id: Long,
        name: String,
        categories: List<CategoryExpense> = emptyList(),
    ) = AccountCategoryBreakdown(
        accountId = id, accountName = name,
        accountColorHex = "#2196F3", accountIconName = "CreditCard",
        total = BigDecimal("1000"), percentage = 100f, transactionCount = 2,
        categories = categories,
    )

    private fun state(
        hasTransactions: Boolean = true,
        categoryExpenses: List<CategoryExpense> = emptyList(),
        expensesByAccount: List<AccountCategoryBreakdown> = emptyList(),
    ) = AnalyticsUiState(
        isLoading = false,
        period = YearMonth.of(2026, 4),
        availableCurrencies = listOf("RUB"),
        selectedCurrency = "RUB",
        periodHasTransactions = hasTransactions,
        categoryExpenses = categoryExpenses,
        expensesByAccount = expensesByAccount,
    )

    private fun setScreen(uiState: AnalyticsUiState) {
        composeTestRule.setContent {
            AnalyticsScreen(
                uiState = uiState,
                onPrevPeriod = {}, onNextPeriod = {}, onJumpPeriod = {},
                onCurrencySelect = {}, onCategoryClick = {}, onSeeAllTransactions = {},
            )
        }
    }

    // ── mode chips ────────────────────────────────────────────────────────────

    @Test
    fun bothModeChips_visibleWhenHasTransactions() {
        setScreen(state(hasTransactions = true))
        composeTestRule.onNodeWithText("По категориям").assertIsDisplayed()
        composeTestRule.onNodeWithText("По счетам").assertIsDisplayed()
    }

    @Test
    fun modeChips_hiddenWhenNoTransactions() {
        setScreen(state(hasTransactions = false))
        composeTestRule.onNodeWithText("По категориям").assertDoesNotExist()
        composeTestRule.onNodeWithText("По счетам").assertDoesNotExist()
    }

    // ── rollup chip visibility ────────────────────────────────────────────────

    @Test
    fun rollupChip_visibleInCategoryMode() {
        setScreen(state(hasTransactions = true))
        composeTestRule.onNodeWithText("Группировать по разделам").assertIsDisplayed()
    }

    @Test
    fun rollupChip_visibleInAccountMode() {
        setScreen(state(hasTransactions = true))
        composeTestRule.onNodeWithText("По счетам").performClick()
        composeTestRule.onNodeWithText("Группировать по разделам").assertIsDisplayed()
    }

    @Test
    fun rollupChip_hiddenWhenNoTransactions() {
        setScreen(state(hasTransactions = false))
        composeTestRule.onNodeWithText("Группировать по разделам").assertDoesNotExist()
    }

    // ── account mode ─────────────────────────────────────────────────────────

    @Test
    fun accountMode_showsCorrectTitle() {
        setScreen(state(
            hasTransactions = true,
            expensesByAccount = listOf(accCatBreakdown(1L, "Карта")),
        ))
        composeTestRule.onNodeWithText("По счетам").performClick()
        composeTestRule.onNodeWithText("Расходы по счетам").assertIsDisplayed()
        composeTestRule.onNodeWithText("Расходы по категориям").assertDoesNotExist()
    }

    @Test
    fun accountMode_showsAccountName() {
        setScreen(state(
            hasTransactions = true,
            expensesByAccount = listOf(accCatBreakdown(1L, "Карта Сбербанк")),
        ))
        composeTestRule.onNodeWithText("По счетам").performClick()
        composeTestRule.onNodeWithText("Карта Сбербанк").assertIsDisplayed()
    }

    @Test
    fun accountMode_categoriesHidden_beforeExpand() {
        setScreen(state(
            hasTransactions = true,
            expensesByAccount = listOf(
                accCatBreakdown(1L, "Карта", categories = listOf(catExpense(10L, "Еда"))),
            ),
        ))
        composeTestRule.onNodeWithText("По счетам").performClick()
        composeTestRule.onNodeWithText("Еда").assertDoesNotExist()
    }

    @Test
    fun accountMode_expandAccount_showsCategories() {
        setScreen(state(
            hasTransactions = true,
            expensesByAccount = listOf(
                accCatBreakdown(1L, "Карта", categories = listOf(
                    catExpense(10L, "Еда"),
                    catExpense(20L, "Транспорт"),
                )),
            ),
        ))
        composeTestRule.onNodeWithText("По счетам").performClick()
        composeTestRule.onNodeWithText("Карта").performClick()
        composeTestRule.onNodeWithText("Еда").assertIsDisplayed()
        composeTestRule.onNodeWithText("Транспорт").assertIsDisplayed()
    }

    @Test
    fun accountMode_collapseAccount_hidesCategories() {
        setScreen(state(
            hasTransactions = true,
            expensesByAccount = listOf(
                accCatBreakdown(1L, "Карта", categories = listOf(catExpense(10L, "Еда"))),
            ),
        ))
        composeTestRule.onNodeWithText("По счетам").performClick()
        composeTestRule.onNodeWithText("Карта").performClick()
        composeTestRule.onNodeWithText("Еда").assertIsDisplayed()
        composeTestRule.onNodeWithText("Карта").performClick()
        composeTestRule.onNodeWithText("Еда").assertDoesNotExist()
    }

    @Test
    fun accountMode_twoAccounts_expandOneDoesNotAffectOther() {
        setScreen(state(
            hasTransactions = true,
            expensesByAccount = listOf(
                accCatBreakdown(1L, "Карта", categories = listOf(catExpense(10L, "Еда"))),
                accCatBreakdown(2L, "Наличные", categories = listOf(catExpense(20L, "Транспорт"))),
            ),
        ))
        composeTestRule.onNodeWithText("По счетам").performClick()
        composeTestRule.onNodeWithText("Карта").performClick()
        composeTestRule.onNodeWithText("Еда").assertIsDisplayed()
        composeTestRule.onNodeWithText("Транспорт").assertDoesNotExist()
    }
}
