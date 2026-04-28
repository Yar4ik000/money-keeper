package com.moneykeeper.feature.accounts.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.core.domain.calculator.DepositCalculator
import com.moneykeeper.core.domain.model.AccrualBasis
import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.model.CapPeriod
import com.moneykeeper.core.domain.model.Deposit
import com.moneykeeper.core.domain.model.DepositEvent
import com.moneykeeper.core.domain.model.DepositEventType
import com.moneykeeper.core.domain.model.TransactionWithMeta
import com.moneykeeper.core.domain.repository.AccountRepository
import com.moneykeeper.core.domain.repository.DepositEventRepository
import com.moneykeeper.core.domain.repository.DepositRepository
import com.moneykeeper.core.domain.repository.TransactionRepository
import com.moneykeeper.core.domain.repository.TransactionRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    private val accountRepo: AccountRepository,
    transactionRepo: TransactionRepository,
    private val depositRepo: DepositRepository,
    private val depositEventRepo: DepositEventRepository,
    private val txRunner: TransactionRunner,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val accountId: Long = savedStateHandle.get<Long>("accountId")!!

    val account: StateFlow<Account?> = accountRepo.observeAllAccounts()
        .map { list -> list.find { it.id == accountId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val transactions: StateFlow<List<TransactionWithMeta>> = transactionRepo.observe(
        accountId = accountId,
        categoryId = null,
        type = null,
        from = LocalDate.of(2000, 1, 1),
        to = LocalDate.of(2100, 12, 31),
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val deposit: StateFlow<Deposit?> = depositRepo.observeAll()
        .map { list -> list.find { it.accountId == accountId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val depositEvents: StateFlow<List<DepositEvent>> = deposit
        .flatMapLatest { d -> if (d != null) depositEventRepo.observe(d.id) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedEventIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedEventIds: StateFlow<Set<Long>> = _selectedEventIds.asStateFlow()

    private val _isEventSelectionMode = MutableStateFlow(false)
    val isEventSelectionMode: StateFlow<Boolean> = _isEventSelectionMode.asStateFlow()

    fun enterEventSelectionMode(eventId: Long) {
        _isEventSelectionMode.value = true
        _selectedEventIds.value = setOf(eventId)
    }

    fun toggleEventSelection(eventId: Long) {
        val updated = _selectedEventIds.value.let {
            if (eventId in it) it - eventId else it + eventId
        }
        _selectedEventIds.value = updated
        if (updated.isEmpty()) _isEventSelectionMode.value = false
    }

    fun clearEventSelection() {
        _selectedEventIds.value = emptySet()
        _isEventSelectionMode.value = false
    }

    fun deleteSelectedEvents() {
        val dep = deposit.value ?: return
        val toDelete = depositEvents.value.filter { it.id in _selectedEventIds.value }
        val principalEvents = toDelete.filter {
            it.type == DepositEventType.PRINCIPAL_ADD || it.type == DepositEventType.PRINCIPAL_WITHDRAW
        }
        if (principalEvents.isEmpty()) return
        val earliestDate = principalEvents.minOf { it.date }
        clearEventSelection()
        viewModelScope.launch {
            txRunner.run {
                principalEvents.forEach { event ->
                    depositEventRepo.delete(event.id)
                    accountRepo.adjustBalance(accountId, event.amount.negate())
                }
            }
            recalculateInterest(dep.id, earliestDate)
        }
    }

    fun topUp(amount: BigDecimal, date: LocalDate, note: String) {
        val dep = deposit.value ?: return
        viewModelScope.launch {
            txRunner.run {
                depositEventRepo.insert(
                    DepositEvent(depositId = dep.id, date = date, type = DepositEventType.PRINCIPAL_ADD, amount = amount, note = note)
                )
                accountRepo.adjustBalance(accountId, amount)
            }
            recalculateInterest(dep.id, date)
        }
    }

    fun withdraw(amount: BigDecimal, date: LocalDate, note: String) {
        val dep = deposit.value ?: return
        viewModelScope.launch {
            txRunner.run {
                depositEventRepo.insert(
                    DepositEvent(depositId = dep.id, date = date, type = DepositEventType.PRINCIPAL_WITHDRAW, amount = amount.negate(), note = note)
                )
                accountRepo.adjustBalance(accountId, amount.negate())
            }
            recalculateInterest(dep.id, date)
        }
    }

    private suspend fun recalculateInterest(depositId: Long, fromDate: LocalDate) {
        val dep = depositRepo.getByAccountId(accountId) ?: return
        val step = dep.capitalizationPeriod ?: CapPeriod.MONTHLY
        val today = LocalDate.now()
        val effectiveEnd = minOf<LocalDate>(dep.endDate ?: today, today)

        // All events after topUp/withdraw was committed (includes the new PRINCIPAL_ADD/WITHDRAW)
        val allEvents = depositEventRepo.getAll(depositId)

        // Find capitalization/accrual events that need to be recomputed (from adjustment date onward)
        val staleInterestEvents = allEvents.filter {
            it.date >= fromDate &&
                (it.type == DepositEventType.INTEREST_ACCRUAL || it.type == DepositEventType.CAPITALIZATION)
        }
        val staleInterestSum = staleInterestEvents.fold(BigDecimal.ZERO) { acc, e -> acc + e.amount }

        if (staleInterestEvents.isNotEmpty()) {
            txRunner.run {
                staleInterestEvents.forEach { depositEventRepo.delete(it.id) }
                // For capitalized deposits the stale interest was added to balance — reverse it
                if (dep.isCapitalized && staleInterestSum.signum() > 0) {
                    accountRepo.adjustBalance(accountId, staleInterestSum.negate())
                }
            }
        }

        // Surviving events determine the last correct accrual boundary
        val survivingEvents = allEvents - staleInterestEvents.toSet()
        val lastAccrualDate = survivingEvents
            .filter { it.type == DepositEventType.INTEREST_ACCRUAL || it.type == DepositEventType.CAPITALIZATION }
            .maxOfOrNull { it.date } ?: dep.startDate

        var currentBalance = accountRepo.getById(accountId)?.balance ?: return
        val eventType = if (dep.isCapitalized) DepositEventType.CAPITALIZATION else DepositEventType.INTEREST_ACCRUAL
        var from = lastAccrualDate

        while (true) {
            val periodEnd = DepositCalculator.nextPeriodEnd(from, step)
            if (periodEnd.isAfter(effectiveEnd)) break

            val laterPrincipalDelta = survivingEvents
                .filter {
                    it.date > from &&
                        (it.type == DepositEventType.PRINCIPAL_ADD || it.type == DepositEventType.PRINCIPAL_WITHDRAW)
                }
                .fold(BigDecimal.ZERO) { acc, e -> acc + e.amount }
            val balanceAtFrom = currentBalance - laterPrincipalDelta

            val slices = when (dep.accrualBasis) {
                AccrualBasis.DAILY -> {
                    val changes = survivingEvents
                        .filter {
                            it.date > from && it.date < periodEnd &&
                                (it.type == DepositEventType.PRINCIPAL_ADD || it.type == DepositEventType.PRINCIPAL_WITHDRAW)
                        }
                        .map { it.date to it.amount }
                    DepositCalculator.accrueByPeriodDaily(balanceAtFrom, dep, from, periodEnd, changes)
                }
                AccrualBasis.PERIOD_START -> DepositCalculator.accrueByPeriod(balanceAtFrom, dep, from, periodEnd)
            }
            val totalInterest = slices.fold(BigDecimal.ZERO) { acc, (_, v) -> acc + v }
            if (totalInterest.signum() > 0) {
                txRunner.run {
                    slices.forEach { (date, amount) ->
                        depositEventRepo.insert(DepositEvent(depositId = depositId, date = date, type = eventType, amount = amount))
                    }
                    if (dep.isCapitalized) accountRepo.adjustBalance(accountId, totalInterest)
                }
                if (dep.isCapitalized) currentBalance += totalInterest
            }
            from = periodEnd
        }
    }
}
