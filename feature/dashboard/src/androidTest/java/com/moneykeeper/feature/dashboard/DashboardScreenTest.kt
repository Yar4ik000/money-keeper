package com.moneykeeper.feature.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneykeeper.core.domain.analytics.PeriodSummaryByCurrency
import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.model.AccountType
import com.moneykeeper.core.domain.model.Deposit
import com.moneykeeper.core.domain.model.TransactionType
import com.moneykeeper.core.domain.model.TransactionWithMeta
import com.moneykeeper.core.domain.model.Transaction
import com.moneykeeper.core.domain.money.CurrencyAmount
import com.moneykeeper.core.domain.money.MultiCurrencyTotal
import com.moneykeeper.core.ui.util.formatAsCurrency
import com.moneykeeper.feature.dashboard.ui.DashboardScreen
import com.moneykeeper.feature.dashboard.ui.DashboardUiState
import com.moneykeeper.feature.dashboard.ui.DepositWithDaysLeft
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val emptyState = DashboardUiState(isLoading = false)

    @Test
    fun totalBalanceCard_showsZeroWhenNoAccounts() {
        composeTestRule.setContent {
            DashboardScreen(
                state = emptyState,
                onAccountClick = {},
                onAddAccount = {},
                onAddTransaction = {},
                onSeeAllTransactions = {},
                onDepositClick = {},
                onSettings = {},
                onTransactionClick = {},
            )
        }
        val expected = BigDecimal.ZERO.formatAsCurrency("RUB")
        composeTestRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun totalBalanceCard_showsBalanceForSingleCurrency() {
        val state = emptyState.copy(
            totalsByCurrency = MultiCurrencyTotal(
                listOf(CurrencyAmount("RUB", BigDecimal("150000"))),
            ),
        )
        composeTestRule.setContent {
            DashboardScreen(
                state = state,
                onAccountClick = {},
                onAddAccount = {},
                onAddTransaction = {},
                onSeeAllTransactions = {},
                onDepositClick = {},
                onSettings = {},
                onTransactionClick = {},
            )
        }
        val expected = BigDecimal("150000").formatAsCurrency("RUB")
        composeTestRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun accountsCarousel_showsAccountName() {
        val account = Account(
            id = 1L, name = "Тестовый счёт", type = AccountType.CARD,
            currency = "RUB", colorHex = "#2196F3", iconName = "card",
            balance = BigDecimal("5000"), createdAt = LocalDate.now(),
        )
        val state = emptyState.copy(accounts = listOf(account))
        composeTestRule.setContent {
            DashboardScreen(
                state = state,
                onAccountClick = {},
                onAddAccount = {},
                onAddTransaction = {},
                onSeeAllTransactions = {},
                onDepositClick = {},
                onSettings = {},
                onTransactionClick = {},
            )
        }
        composeTestRule.onNodeWithText("Тестовый счёт").assertIsDisplayed()
    }

    @Test
    fun monthlySummaryCard_showsEmptyMessage_whenNoSummary() {
        composeTestRule.setContent {
            DashboardScreen(
                state = emptyState,
                onAccountClick = {},
                onAddAccount = {},
                onAddTransaction = {},
                onSeeAllTransactions = {},
                onDepositClick = {},
                onSettings = {},
                onTransactionClick = {},
            )
        }
        composeTestRule.onNodeWithText("За этот месяц операций нет").assertIsDisplayed()
    }

    @Test
    fun monthlySummaryCard_showsIncomeAndExpense() {
        val state = emptyState.copy(
            monthlySummary = listOf(
                PeriodSummaryByCurrency("RUB", BigDecimal("20000"), BigDecimal("8000")),
            ),
        )
        composeTestRule.setContent {
            DashboardScreen(
                state = state,
                onAccountClick = {},
                onAddAccount = {},
                onAddTransaction = {},
                onSeeAllTransactions = {},
                onDepositClick = {},
                onSettings = {},
                onTransactionClick = {},
            )
        }
        composeTestRule.onNodeWithText("Доходы").assertIsDisplayed()
        composeTestRule.onNodeWithText("Расходы").assertIsDisplayed()
    }

    @Test
    fun expiringDepositsWidget_notShown_whenEmpty() {
        composeTestRule.setContent {
            DashboardScreen(
                state = emptyState,
                onAccountClick = {},
                onAddAccount = {},
                onAddTransaction = {},
                onSeeAllTransactions = {},
                onDepositClick = {},
                onSettings = {},
                onTransactionClick = {},
            )
        }
        composeTestRule.onNodeWithText("Заканчивающиеся вклады").assertDoesNotExist()
    }

    @Test
    fun expiringDepositsWidget_shown_whenHasDeposits() {
        val deposit = Deposit(
            id = 1L, accountId = 10L,
            initialAmount = BigDecimal("100000"),
            interestRate = BigDecimal("10"),
            startDate = LocalDate.now().minusMonths(6),
            endDate = LocalDate.now().plusDays(15),
            isCapitalized = false, capitalizationPeriod = null,
            notifyDaysBefore = listOf(7), autoRenew = false, payoutAccountId = null, isActive = true,
        )
        val state = emptyState.copy(
            expiringDeposits = listOf(
                DepositWithDaysLeft(
                    deposit = deposit,
                    accountName = "Мой вклад",
                    daysLeft = 15,
                    projectedAmount = BigDecimal("105000"),
                ),
            ),
        )
        composeTestRule.setContent {
            DashboardScreen(
                state = state,
                onAccountClick = {},
                onAddAccount = {},
                onAddTransaction = {},
                onSeeAllTransactions = {},
                onDepositClick = {},
                onSettings = {},
                onTransactionClick = {},
            )
        }
        composeTestRule.onNodeWithText("Заканчивающиеся вклады").assertIsDisplayed()
        composeTestRule.onNodeWithText("Мой вклад").assertIsDisplayed()
    }

    @Test
    fun totalBalanceCard_rendersEachCurrencyRow_whenMultipleCurrencies() {
        // Feature-presence: per docs §6, strategy C is "no conversion — each currency
        // on its own row". Breaking this (e.g., accidentally displaying only the first
        // row, or converting at display time) should fail this test loudly.
        val state = emptyState.copy(
            totalsByCurrency = MultiCurrencyTotal(
                listOf(
                    CurrencyAmount("RUB", BigDecimal("100000")),
                    CurrencyAmount("USD", BigDecimal("500")),
                ),
            ),
        )
        composeTestRule.setContent {
            DashboardScreen(
                state = state,
                onAccountClick = {},
                onAddAccount = {},
                onAddTransaction = {},
                onSeeAllTransactions = {},
                onDepositClick = {},
                onSettings = {},
                onTransactionClick = {},
            )
        }
        composeTestRule.onNodeWithText(BigDecimal("100000").formatAsCurrency("RUB")).assertIsDisplayed()
        composeTestRule.onNodeWithText(BigDecimal("500").formatAsCurrency("USD")).assertIsDisplayed()
    }

    @Test
    fun recentTransactions_showsCategoryName() {
        val tx = Transaction(
            id = 1L, accountId = 1L, toAccountId = null,
            amount = BigDecimal("500"), type = TransactionType.EXPENSE,
            categoryId = 1L, date = LocalDate.now(), note = "",
            createdAt = LocalDateTime.now(),
        )
        val meta = TransactionWithMeta(
            transaction = tx,
            accountName = "Карта",
            accountCurrency = "RUB",
            categoryName = "Продукты",
            categoryColor = "#4CAF50",
            categoryIcon = "",
        )
        val state = emptyState.copy(recentTransactions = listOf(meta))
        composeTestRule.setContent {
            DashboardScreen(
                state = state,
                onAccountClick = {},
                onAddAccount = {},
                onAddTransaction = {},
                onSeeAllTransactions = {},
                onDepositClick = {},
                onSettings = {},
                onTransactionClick = {},
            )
        }
        composeTestRule.onNodeWithText("Продукты").assertIsDisplayed()
    }
}
