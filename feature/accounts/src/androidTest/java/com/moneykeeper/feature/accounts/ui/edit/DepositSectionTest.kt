package com.moneykeeper.feature.accounts.ui.edit

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneykeeper.core.domain.model.CapPeriod
import com.moneykeeper.core.domain.model.Deposit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class DepositSectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun defaultDeposit() = Deposit(
        id = 0L,
        accountId = 0L,
        initialAmount = BigDecimal("50000"),
        interestRate = BigDecimal("10"),
        startDate = LocalDate.of(2025, 1, 1),
        endDate = LocalDate.of(2026, 1, 1),
        isCapitalized = false,
        capitalizationPeriod = CapPeriod.MONTHLY,
        notifyDaysBefore = listOf(7),
        autoRenew = false,
        payoutAccountId = null,
        isActive = true,
    )

    @Test
    fun depositSection_showsRequiredFields() {
        composeTestRule.setContent {
            MaterialTheme {
                DepositSection(deposit = defaultDeposit(), onChange = {})
            }
        }
        composeTestRule.onNodeWithText("Сумма вклада *").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ставка, % годовых *").assertIsDisplayed()
    }

    @Test
    fun depositSection_showsForecastSection() {
        composeTestRule.setContent {
            MaterialTheme {
                DepositSection(deposit = defaultDeposit(), onChange = {})
            }
        }
        composeTestRule.onNodeWithText("Прогноз").assertIsDisplayed()
        composeTestRule.onNodeWithText("Итого к получению").assertIsDisplayed()
        composeTestRule.onNodeWithText("Начисленные проценты").assertIsDisplayed()
    }

    @Test
    fun depositSection_amountError_showsInlineMessage() {
        composeTestRule.setContent {
            MaterialTheme {
                DepositSection(
                    deposit = defaultDeposit(),
                    onChange = {},
                    error = EditAccountError.DepositAmountInvalid,
                )
            }
        }
        composeTestRule.onNodeWithText("Введите сумму вклада больше нуля").assertIsDisplayed()
    }

    @Test
    fun depositSection_rateError_showsInlineMessage() {
        composeTestRule.setContent {
            MaterialTheme {
                DepositSection(
                    deposit = defaultDeposit(),
                    onChange = {},
                    error = EditAccountError.DepositRateInvalid,
                )
            }
        }
        composeTestRule.onNodeWithText("Введите ставку больше нуля").assertIsDisplayed()
    }

    @Test
    fun depositSection_dateError_showsInlineMessage() {
        val badDates = defaultDeposit().copy(
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2025, 1, 1),
        )
        composeTestRule.setContent {
            MaterialTheme {
                DepositSection(
                    deposit = badDates,
                    onChange = {},
                    error = EditAccountError.DepositDateInvalid,
                )
            }
        }
        composeTestRule.onNodeWithText("Дата окончания должна быть позже даты начала").assertIsDisplayed()
    }

    @Test
    fun depositSection_capitalizationToggle_showsPeriodSelector() {
        val capitalizedDeposit = defaultDeposit().copy(isCapitalized = true)
        composeTestRule.setContent {
            MaterialTheme {
                DepositSection(deposit = capitalizedDeposit, onChange = {})
            }
        }
        composeTestRule.onNodeWithText("Период капитализации").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ежемесячно").assertIsDisplayed()
    }
}
