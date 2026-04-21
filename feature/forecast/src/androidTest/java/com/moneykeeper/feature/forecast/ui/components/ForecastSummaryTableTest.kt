package com.moneykeeper.feature.forecast.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class ForecastCurrencyTotalsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun zeroDelta_hidesAtDateColumnAndDeltaLabel() {
        composeTestRule.setContent {
            MaterialTheme {
                ForecastCurrencyTotals(
                    currency = "RUB",
                    currentBalance = BigDecimal("10000"),
                    forecastedBalance = BigDecimal("10000"),
                    delta = BigDecimal.ZERO,
                )
            }
        }

        composeTestRule.onNodeWithText("Сейчас").assertIsDisplayed()
        composeTestRule.onNodeWithText("К дате").assertDoesNotExist()
        composeTestRule.onNodeWithText("Прирост:", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Убыток:", substring = true).assertDoesNotExist()
    }

    @Test
    fun positiveDelta_showsGainLabel() {
        composeTestRule.setContent {
            MaterialTheme {
                ForecastCurrencyTotals(
                    currency = "RUB",
                    currentBalance = BigDecimal("10000"),
                    forecastedBalance = BigDecimal("12500"),
                    delta = BigDecimal("2500"),
                )
            }
        }

        composeTestRule.onNodeWithText("Сейчас").assertIsDisplayed()
        composeTestRule.onNodeWithText("К дате").assertIsDisplayed()
        composeTestRule.onNodeWithText("Прирост:", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Убыток:", substring = true).assertDoesNotExist()
    }

    @Test
    fun negativeDelta_showsLossLabel() {
        composeTestRule.setContent {
            MaterialTheme {
                ForecastCurrencyTotals(
                    currency = "RUB",
                    currentBalance = BigDecimal("10000"),
                    forecastedBalance = BigDecimal("7500"),
                    delta = BigDecimal("-2500"),
                )
            }
        }

        composeTestRule.onNodeWithText("Сейчас").assertIsDisplayed()
        composeTestRule.onNodeWithText("К дате").assertIsDisplayed()
        composeTestRule.onNodeWithText("Убыток:", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Прирост:", substring = true).assertDoesNotExist()
    }
}
