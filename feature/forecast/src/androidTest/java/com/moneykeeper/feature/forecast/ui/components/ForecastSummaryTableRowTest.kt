package com.moneykeeper.feature.forecast.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneykeeper.core.domain.forecast.AccountForecast
import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.model.AccountType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Verifies per-account rows hide the "X → X" arrow when nothing actually changes,
 * so accounts that won't move by the forecast date don't look like they do.
 */
@RunWith(AndroidJUnit4::class)
class ForecastSummaryTableRowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun account(id: Long, balance: String) = Account(
        id = id,
        name = "Acc $id",
        type = AccountType.CARD,
        currency = "RUB",
        colorHex = "#000000",
        iconName = "CreditCard",
        balance = BigDecimal(balance),
        createdAt = LocalDate.of(2026, 1, 1),
    )

    @Test
    fun zeroDeltaRow_hidesArrowAndDuplicatedBalance() {
        val forecast = AccountForecast(
            account = account(1L, "120000"),
            currentBalance = BigDecimal("120000"),
            forecastedBalance = BigDecimal("120000"),
            delta = BigDecimal.ZERO,
        )
        composeTestRule.setContent {
            MaterialTheme {
                ForecastSummaryTable(forecasts = listOf(forecast))
            }
        }

        composeTestRule.onNodeWithText("Acc 1").assertIsDisplayed()
        // Arrow should NOT appear when nothing moves
        composeTestRule.onNodeWithText("→", substring = true).assertDoesNotExist()
    }

    @Test
    fun nonZeroDeltaRow_showsArrowAndForecastedBalance() {
        val forecast = AccountForecast(
            account = account(2L, "10000"),
            currentBalance = BigDecimal("10000"),
            forecastedBalance = BigDecimal("12500"),
            delta = BigDecimal("2500"),
        )
        composeTestRule.setContent {
            MaterialTheme {
                ForecastSummaryTable(forecasts = listOf(forecast))
            }
        }

        composeTestRule.onNodeWithText("Acc 2").assertIsDisplayed()
        composeTestRule.onNodeWithText("→", substring = true).assertIsDisplayed()
    }
}
