package com.moneykeeper.feature.auth

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneykeeper.feature.auth.ui.components.PinKeypad
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests that PinKeypad correctly fires its callbacks and that the
 * 4-digit auto-submit pattern works when wired as real screens do it.
 */
@RunWith(AndroidJUnit4::class)
class PinKeypadTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── individual callbacks ──────────────────────────────────────────────────

    @Test
    fun pressDigit1_callsOnDigitWithChar1() {
        var received: Char? = null
        composeTestRule.setContent {
            MaterialTheme { PinKeypad(onDigit = { received = it }, onDelete = {}) }
        }
        composeTestRule.onNodeWithText("1").performClick()
        assertEquals('1', received)
    }

    @Test
    fun pressDigit0_callsOnDigitWithChar0() {
        var received: Char? = null
        composeTestRule.setContent {
            MaterialTheme { PinKeypad(onDigit = { received = it }, onDelete = {}) }
        }
        composeTestRule.onNodeWithText("0").performClick()
        assertEquals('0', received)
    }

    @Test
    fun pressAllNineDigits_eachFiresCorrectChar() {
        val received = mutableListOf<Char>()
        composeTestRule.setContent {
            MaterialTheme { PinKeypad(onDigit = { received += it }, onDelete = {}) }
        }
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9").forEach { digit ->
            composeTestRule.onNodeWithText(digit).performClick()
        }
        assertEquals("123456789".toList(), received)
    }

    @Test
    fun pressBackspace_callsOnDelete() {
        var deleteCount = 0
        composeTestRule.setContent {
            MaterialTheme { PinKeypad(onDigit = {}, onDelete = { deleteCount++ }) }
        }
        composeTestRule.onNodeWithTag("pin_backspace").performClick()
        assertEquals(1, deleteCount)
    }

    @Test
    fun digitButtons_doNotFireOnDelete() {
        var deleteCount = 0
        composeTestRule.setContent {
            MaterialTheme { PinKeypad(onDigit = {}, onDelete = { deleteCount++ }) }
        }
        listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9").forEach {
            composeTestRule.onNodeWithText(it).performClick()
        }
        assertEquals("No digit button should fire onDelete", 0, deleteCount)
    }

    // ── 4-digit auto-submit pattern ───────────────────────────────────────────

    @Test
    fun fourDigitSequence_accumulatesCorrectly() {
        val collected = StringBuilder()
        composeTestRule.setContent {
            MaterialTheme {
                PinKeypad(
                    onDigit = { collected.append(it) },
                    onDelete = { if (collected.isNotEmpty()) collected.deleteCharAt(collected.length - 1) },
                )
            }
        }
        listOf("7", "3", "1", "9").forEach {
            composeTestRule.onNodeWithText(it).performClick()
        }
        assertEquals("7319", collected.toString())
    }

    @Test
    fun backspaceMidSequence_removesLastChar() {
        val pin = StringBuilder()
        composeTestRule.setContent {
            MaterialTheme {
                PinKeypad(
                    onDigit = { pin.append(it) },
                    onDelete = { if (pin.isNotEmpty()) pin.deleteCharAt(pin.length - 1) },
                )
            }
        }
        composeTestRule.onNodeWithText("1").performClick()
        composeTestRule.onNodeWithText("2").performClick()
        composeTestRule.onNodeWithText("3").performClick()
        assertEquals("123", pin.toString())

        composeTestRule.onNodeWithTag("pin_backspace").performClick()
        assertEquals("12", pin.toString())

        composeTestRule.onNodeWithText("5").performClick()
        assertEquals("125", pin.toString())
    }

    @Test
    fun autoSubmitPattern_firesAt4thDigit() {
        var submittedPin = ""
        composeTestRule.setContent {
            MaterialTheme {
                var pinBuffer by remember { mutableStateOf("") }
                PinKeypad(
                    onDigit = { digit ->
                        if (pinBuffer.length < 4) {
                            pinBuffer += digit
                            if (pinBuffer.length == 4) {
                                submittedPin = pinBuffer
                                pinBuffer = ""
                            }
                        }
                    },
                    onDelete = {
                        if (pinBuffer.isNotEmpty()) pinBuffer = pinBuffer.dropLast(1)
                    },
                )
            }
        }

        composeTestRule.onNodeWithText("2").performClick()
        composeTestRule.onNodeWithText("5").performClick()
        composeTestRule.onNodeWithText("8").performClick()
        assertTrue("Should not have submitted after 3 digits", submittedPin.isEmpty())

        composeTestRule.onNodeWithText("1").performClick()
        assertEquals("2581", submittedPin)
    }

    @Test
    fun withoutConfirmCallback_allVisibleTextIsDigits() {
        composeTestRule.setContent {
            MaterialTheme { PinKeypad(onDigit = {}, onDelete = {}, onConfirm = null) }
        }
        // All 10 digit buttons must be present
        listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9").forEach { digit ->
            composeTestRule.onNodeWithText(digit).assertExists()
        }
        // No extra text buttons (like "OK" or "✓") should appear
        composeTestRule.onNodeWithText("OK").assertDoesNotExist()
        composeTestRule.onNodeWithText("ОК").assertDoesNotExist()
    }
}
