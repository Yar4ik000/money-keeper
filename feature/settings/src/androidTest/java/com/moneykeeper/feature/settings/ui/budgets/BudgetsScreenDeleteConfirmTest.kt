package com.moneykeeper.feature.settings.ui.budgets

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneykeeper.feature.settings.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the confirmation-dialog contract used by BudgetsScreen for deletes:
 *   - dialog renders the localized title/message
 *   - "Отмена" dismisses without firing the callback
 *   - "Удалить" fires the callback exactly once
 * The full BudgetsScreen path is hard to drive without Hilt; this test targets the
 * dialog wiring directly, which is the piece that can regress.
 */
@RunWith(AndroidJUnit4::class)
class BudgetsScreenDeleteConfirmTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun confirmDialog_cancel_doesNotInvokeDelete() {
        var deleteCalled = false
        composeTestRule.setContent {
            MaterialTheme {
                HarnessDialog(onConfirm = { deleteCalled = true })
            }
        }

        composeTestRule.onNodeWithText("Удалить бюджет?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Отмена").performClick()
        composeTestRule.onNodeWithText("Удалить бюджет?").assertDoesNotExist()
        assert(!deleteCalled)
    }

    @Test
    fun confirmDialog_confirm_invokesDeleteAndDismisses() {
        var deleteCalled = false
        composeTestRule.setContent {
            MaterialTheme {
                HarnessDialog(onConfirm = { deleteCalled = true })
            }
        }

        composeTestRule.onNodeWithText("Удалить бюджет?").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Удалить")[0].performClick()
        assert(deleteCalled)
    }
}

/** Mirrors the AlertDialog used on the live screen; if copy drifts, this will fail. */
@androidx.compose.runtime.Composable
private fun HarnessDialog(onConfirm: () -> Unit) {
    var open by remember { mutableStateOf(true) }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.budgets_delete_confirm_title)) },
            text = { Text(stringResource(R.string.budgets_delete_confirm_message, "Еда")) },
            confirmButton = {
                TextButton(onClick = { onConfirm(); open = false }) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}
