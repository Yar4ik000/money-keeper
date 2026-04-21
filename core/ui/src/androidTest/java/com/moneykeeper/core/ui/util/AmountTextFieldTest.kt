package com.moneykeeper.core.ui.util

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AmountTextFieldTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendersValueWithThousandsSeparator() {
        composeTestRule.setContent {
            MaterialTheme {
                AmountTextField(value = "1000000", onValueChange = {})
            }
        }
        // U+202F narrow no-break space
        composeTestRule.onNodeWithText("1 000 000").assertIsDisplayed()
    }

    @Test
    fun typingPropagatesPlainText_withoutSeparators() {
        var captured = ""
        composeTestRule.setContent {
            MaterialTheme {
                var v by remember { mutableStateOf("") }
                AmountTextField(
                    value = v,
                    onValueChange = { v = it; captured = it },
                )
            }
        }

        composeTestRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))[0]
            .performTextInput("12345")

        composeTestRule.waitUntil(timeoutMillis = 2_000) { captured == "12345" }
    }

    @Test
    fun externalValueUpdate_syncsIntoField() {
        composeTestRule.setContent {
            MaterialTheme {
                var v by remember { mutableStateOf("100") }
                AmountTextField(value = v, onValueChange = { v = it })
                LaunchedEffect(Unit) { v = "5000" }
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 2_000) {
            runCatching {
                composeTestRule.onNodeWithText("5 000").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }
}
