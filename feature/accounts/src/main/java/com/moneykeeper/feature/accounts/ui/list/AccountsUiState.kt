package com.moneykeeper.feature.accounts.ui.list

import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.model.Deposit
import com.moneykeeper.core.domain.money.MultiCurrencyTotal

sealed interface AccountsUiState {
    data object Loading : AccountsUiState
    data class Success(
        val accounts: List<Account>,
        val totalsByCurrency: MultiCurrencyTotal,
        val deposits: List<Deposit>,
    ) : AccountsUiState
}
