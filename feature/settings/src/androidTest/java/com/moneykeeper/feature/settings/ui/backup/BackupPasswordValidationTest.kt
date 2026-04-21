package com.moneykeeper.feature.settings.ui.backup

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for ExportPasswordDialog validation.
 * Each test renders the real dialog composable and drives it via Compose semantics.
 *
 * Validation order: length → uppercase → lowercase → digit → confirmation match.
 */
@RunWith(AndroidJUnit4::class)
class BackupPasswordValidationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun launch(onSubmit: (CharArray) -> Unit = {}) {
        composeTestRule.setContent {
            MaterialTheme {
                ExportPasswordDialog(onSubmit = onSubmit, onDismiss = {})
            }
        }
    }

    private fun typeInField(label: String, text: String) {
        composeTestRule.onNodeWithText(label).performClick()
        composeTestRule.onNodeWithText(label).performTextInput(text)
    }

    private fun clickOk() = composeTestRule.onNodeWithText("ОК").performClick()

    // ── error cases ───────────────────────────────────────────────────────────

    @Test
    fun tooShort_showsLengthError() {
        launch()
        typeInField("Пароль", "Abc1")
        typeInField("Повторите пароль", "Abc1")
        clickOk()
        composeTestRule.onNodeWithText("Минимум 8 символов").assertIsDisplayed()
    }

    @Test
    fun noUppercase_showsUpperError() {
        launch()
        typeInField("Пароль", "abcdefg1")
        typeInField("Повторите пароль", "abcdefg1")
        clickOk()
        composeTestRule.onNodeWithText("Добавьте хотя бы одну заглавную букву").assertIsDisplayed()
    }

    @Test
    fun noLowercase_showsLowerError() {
        launch()
        typeInField("Пароль", "ABCDEFG1")
        typeInField("Повторите пароль", "ABCDEFG1")
        clickOk()
        composeTestRule.onNodeWithText("Добавьте хотя бы одну строчную букву").assertIsDisplayed()
    }

    @Test
    fun noDigit_showsDigitError() {
        launch()
        typeInField("Пароль", "Abcdefgh")
        typeInField("Повторите пароль", "Abcdefgh")
        clickOk()
        composeTestRule.onNodeWithText("Добавьте хотя бы одну цифру").assertIsDisplayed()
    }

    @Test
    fun mismatch_showsMismatchError() {
        launch()
        typeInField("Пароль", "ValidPass1")
        typeInField("Повторите пароль", "Different1")
        clickOk()
        composeTestRule.onNodeWithText("Пароли не совпадают").assertIsDisplayed()
    }

    // ── success case ──────────────────────────────────────────────────────────

    @Test
    fun validMatchingPassword_callsOnSubmit() {
        var submitted: CharArray? = null
        launch(onSubmit = { submitted = it })

        typeInField("Пароль", "ValidPass1")
        typeInField("Повторите пароль", "ValidPass1")
        clickOk()

        assertNotNull("onSubmit should have been called", submitted)
        assertEquals("ValidPass1", String(submitted!!))
    }

    // ── warning text ──────────────────────────────────────────────────────────

    @Test
    fun dialog_showsOfflineBruteForceWarning() {
        launch()
        composeTestRule.onNodeWithText("Пароль к файлу можно подбирать без ограничений попыток.", substring = true)
            .assertIsDisplayed()
    }
}
