package com.moneykeeper.feature.accounts.ui.edit

import com.moneykeeper.core.domain.error.DomainError
import com.moneykeeper.core.domain.model.AccountType
import com.moneykeeper.core.domain.model.CapPeriod
import com.moneykeeper.core.domain.model.Deposit
import java.math.BigDecimal
import java.time.LocalDate

data class EditAccountUiState(
    val name: String = "",
    val type: AccountType = AccountType.CARD,
    val currency: String = "RUB",
    val colorHex: String = "#4CAF50",
    val iconName: String = "AccountBalance",
    /** Raw text in the balance field — may contain trailing "." while user is typing. */
    val balanceInput: String = "0",
    val deposit: Deposit? = null,
    val createdAt: LocalDate? = null,
    val saved: Boolean = false,
    val error: EditAccountError? = null,
) {
    val initialBalance: BigDecimal
        get() = balanceInput.replace(",", ".").toBigDecimalOrNull() ?: BigDecimal.ZERO
}

sealed interface EditAccountError {
    data object NameEmpty : EditAccountError
    data object DepositParamsMissing : EditAccountError
    data object DepositAmountInvalid : EditAccountError
    data object DepositRateInvalid : EditAccountError
    data object DepositDateInvalid : EditAccountError
    data object DepositEndDatePast : EditAccountError
    data class Domain(val error: DomainError) : EditAccountError
}

fun defaultDeposit(accountId: Long = 0L): Deposit = Deposit(
    id = 0L, accountId = accountId,
    initialAmount = BigDecimal.ZERO,
    interestRate = BigDecimal("10.0"),
    startDate = LocalDate.now(),
    endDate = LocalDate.now().plusYears(1),
    isCapitalized = false,
    capitalizationPeriod = CapPeriod.MONTHLY,
    notifyDaysBefore = listOf(7),
    autoRenew = false, payoutAccountId = null, isActive = true,
)

fun defaultSavingsDeposit(accountId: Long = 0L): Deposit = Deposit(
    id = 0L, accountId = accountId,
    initialAmount = BigDecimal.ZERO,
    interestRate = BigDecimal("5.0"),
    startDate = LocalDate.now(),
    endDate = null,
    isCapitalized = true,
    capitalizationPeriod = CapPeriod.DAILY,
    notifyDaysBefore = emptyList(),
    autoRenew = false, payoutAccountId = null, isActive = true,
)
