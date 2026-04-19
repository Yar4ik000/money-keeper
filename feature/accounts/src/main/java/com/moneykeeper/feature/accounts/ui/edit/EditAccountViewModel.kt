package com.moneykeeper.feature.accounts.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.model.AccountType
import com.moneykeeper.core.domain.model.CapPeriod
import com.moneykeeper.core.domain.model.Deposit
import com.moneykeeper.core.domain.repository.AccountRepository
import com.moneykeeper.core.domain.repository.DepositRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class EditAccountViewModel @Inject constructor(
    private val accountRepo: AccountRepository,
    private val depositRepo: DepositRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val accountId: Long? = savedStateHandle.get<Long>("accountId")?.takeIf { it != -1L }

    private val _uiState = MutableStateFlow(EditAccountUiState())
    val uiState: StateFlow<EditAccountUiState> = _uiState.asStateFlow()

    init {
        if (accountId != null) {
            viewModelScope.launch {
                val account = accountRepo.getById(accountId) ?: return@launch
                _uiState.update {
                    it.copy(
                        name = account.name,
                        type = account.type,
                        currency = account.currency,
                        colorHex = account.colorHex,
                        iconName = account.iconName,
                        initialBalance = account.balance,
                        createdAt = account.createdAt,
                    )
                }
                depositRepo.getByAccountId(accountId)?.let { deposit ->
                    _uiState.update { it.copy(deposit = deposit) }
                }
            }
        }
    }

    fun onNameChange(name: String) = _uiState.update { it.copy(name = name, error = null) }

    fun onTypeChange(type: AccountType) = _uiState.update { s ->
        s.copy(
            type = type,
            deposit = if (type == AccountType.DEPOSIT) s.deposit ?: defaultDeposit() else null,
            error = null,
        )
    }

    fun onCurrencyChange(currency: String) = _uiState.update { it.copy(currency = currency) }

    fun onColorChange(colorHex: String) = _uiState.update { it.copy(colorHex = colorHex) }

    fun onIconChange(iconName: String) = _uiState.update { it.copy(iconName = iconName) }

    fun onBalanceChange(balance: BigDecimal) = _uiState.update { it.copy(initialBalance = balance) }

    fun onDepositChange(deposit: Deposit) = _uiState.update { it.copy(deposit = deposit, error = null) }

    fun save() = viewModelScope.launch {
        val s = _uiState.value
        if (s.name.isBlank()) {
            _uiState.update { it.copy(error = EditAccountError.NameEmpty) }
            return@launch
        }

        val effectiveBalance: BigDecimal = when (s.type) {
            AccountType.DEPOSIT -> {
                val deposit = s.deposit ?: run {
                    _uiState.update { it.copy(error = EditAccountError.DepositParamsMissing) }
                    return@launch
                }
                if (deposit.initialAmount <= BigDecimal.ZERO) {
                    _uiState.update { it.copy(error = EditAccountError.DepositAmountInvalid) }
                    return@launch
                }
                if (deposit.interestRate <= BigDecimal.ZERO) {
                    _uiState.update { it.copy(error = EditAccountError.DepositRateInvalid) }
                    return@launch
                }
                if (!deposit.endDate.isAfter(deposit.startDate)) {
                    _uiState.update { it.copy(error = EditAccountError.DepositDateInvalid) }
                    return@launch
                }
                deposit.initialAmount
            }
            else -> if (accountId != null) {
                accountRepo.getById(accountId)?.balance ?: s.initialBalance
            } else {
                s.initialBalance
            }
        }

        val savedId = accountRepo.save(
            Account(
                id = accountId ?: 0L,
                name = s.name,
                type = s.type,
                currency = s.currency,
                colorHex = s.colorHex,
                iconName = s.iconName,
                balance = effectiveBalance,
                createdAt = s.createdAt ?: LocalDate.now(),
            )
        )
        val id = if (accountId != null) accountId else savedId

        if (s.type == AccountType.DEPOSIT && s.deposit != null) {
            depositRepo.save(s.deposit.copy(accountId = id))
        }

        _uiState.update { it.copy(saved = true) }
    }
}
