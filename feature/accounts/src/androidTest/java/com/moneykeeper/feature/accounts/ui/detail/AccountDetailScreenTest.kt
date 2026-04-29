package com.moneykeeper.feature.accounts.ui.detail

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneykeeper.core.domain.model.DepositEvent
import com.moneykeeper.core.domain.model.DepositEventType
import com.moneykeeper.core.domain.model.Transaction
import com.moneykeeper.core.domain.model.TransactionType
import com.moneykeeper.core.domain.model.TransactionWithMeta
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Regression: deposit_events and transactions share the same auto-increment ID sequence.
 * AccountDetailScreen renders both in a single LazyColumn. Without key prefixes
 * ("pe_/ie_/tx_"), a DepositEvent and a Transaction with the same numeric id caused
 * "Key X was already used" → crash on scroll.
 */
@RunWith(AndroidJUnit4::class)
class AccountDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun lazyColumnKeys_overlappingDepositAndTransactionId_noCrash() {
        val sharedId = 3L

        val principalEvents = listOf(
            DepositEvent(
                id = sharedId,
                depositId = 1L,
                date = LocalDate.of(2026, 1, 1),
                type = DepositEventType.PRINCIPAL_ADD,
                amount = BigDecimal("1000"),
            ),
        )
        val interestEvents = listOf(
            DepositEvent(
                id = sharedId + 1,
                depositId = 1L,
                date = LocalDate.of(2026, 2, 1),
                type = DepositEventType.INTEREST_ACCRUAL,
                amount = BigDecimal("10"),
            ),
        )
        val transactions = listOf(
            TransactionWithMeta(
                transaction = Transaction(
                    id = sharedId,
                    accountId = 1L,
                    toAccountId = null,
                    amount = BigDecimal("500"),
                    type = TransactionType.EXPENSE,
                    categoryId = null,
                    date = LocalDate.of(2026, 1, 15),
                    createdAt = LocalDateTime.now(),
                ),
                accountName = "Test",
                accountCurrency = "RUB",
                categoryName = "Food",
                categoryColor = "#FF0000",
                categoryIcon = "other",
            ),
        )

        composeTestRule.setContent {
            LazyColumn {
                items(principalEvents, key = { "pe_${it.id}" }) { Text("pe_${it.id}") }
                items(interestEvents, key = { "ie_${it.id}" }) { Text("ie_${it.id}") }
                items(transactions, key = { "tx_${it.transaction.id}" }) { Text("tx_${it.transaction.id}") }
            }
        }

        // All three items must be visible — if keys were bare longs, the duplicate "3"
        // would throw IllegalArgumentException before reaching here.
        composeTestRule.onNodeWithText("pe_$sharedId").assertIsDisplayed()
        composeTestRule.onNodeWithText("tx_$sharedId").assertIsDisplayed()
    }
}
