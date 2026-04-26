package com.moneykeeper.feature.accounts.ui.detail

import androidx.lifecycle.SavedStateHandle
import com.moneykeeper.core.domain.analytics.AccountCategorySum
import com.moneykeeper.core.domain.analytics.AccountSum
import com.moneykeeper.core.domain.analytics.CategorySum
import com.moneykeeper.core.domain.analytics.MonthlyBarEntry
import com.moneykeeper.core.domain.analytics.PeriodSummaryByCurrency
import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.model.CapPeriod
import com.moneykeeper.core.domain.model.Deposit
import com.moneykeeper.core.domain.model.DepositEvent
import com.moneykeeper.core.domain.model.DepositEventType
import com.moneykeeper.core.domain.model.Transaction
import com.moneykeeper.core.domain.model.TransactionType
import com.moneykeeper.core.domain.model.TransactionWithMeta
import com.moneykeeper.core.domain.money.MultiCurrencyTotal
import com.moneykeeper.core.domain.repository.AccountRepository
import com.moneykeeper.core.domain.repository.DepositEventRepository
import com.moneykeeper.core.domain.repository.DepositRepository
import com.moneykeeper.core.domain.repository.TransactionRepository
import com.moneykeeper.core.domain.repository.TransactionRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AccountDetailViewModelTest {

    private val testAccountId = 1L
    private val testDepositId = 10L

    private val deletedEventIds = mutableListOf<Long>()
    private val adjustments = mutableListOf<Pair<Long, BigDecimal>>()
    private val eventsFlow = MutableStateFlow<List<DepositEvent>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        deletedEventIds.clear()
        adjustments.clear()
        eventsFlow.value = emptyList()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun deposit() = Deposit(
        id = testDepositId, accountId = testAccountId,
        initialAmount = BigDecimal("50000"),
        interestRate = BigDecimal("16"),
        startDate = LocalDate.of(2025, 10, 31),
        endDate = LocalDate.of(2026, 10, 31),
        isCapitalized = true,
        capitalizationPeriod = CapPeriod.MONTHLY,
        payoutAccountId = null,
    )

    private fun principalEvent(id: Long, amount: BigDecimal = BigDecimal("950000")) = DepositEvent(
        id = id, depositId = testDepositId,
        date = LocalDate.of(2025, 11, 3),
        type = DepositEventType.PRINCIPAL_ADD,
        amount = amount,
    )

    // ── fakes ────────────────────────────────────────────────────────────────

    private val fakeAccountRepo = object : AccountRepository {
        override fun observeActiveAccounts(): Flow<List<Account>> = MutableStateFlow(emptyList())
        override fun observeAllAccounts(): Flow<List<Account>> = MutableStateFlow(emptyList())
        override fun observeTotalsByCurrency(): Flow<MultiCurrencyTotal> = throw UnsupportedOperationException()
        override suspend fun getById(id: Long): Account? = null
        override suspend fun save(account: Account): Long = account.id
        override suspend fun archive(id: Long) = Unit
        override suspend fun unarchive(id: Long) = Unit
        override suspend fun updateSortOrders(orderedIds: List<Long>) = Unit
        override suspend fun delete(id: Long) = Unit
        override suspend fun adjustBalance(id: Long, delta: BigDecimal) { adjustments += id to delta }
    }

    private val fakeTransactionRepo = object : TransactionRepository {
        override fun observe(accountId: Long?, categoryId: Long?, type: TransactionType?, from: LocalDate, to: LocalDate): Flow<List<TransactionWithMeta>> = MutableStateFlow(emptyList())
        override fun observeRecent(limit: Int): Flow<List<TransactionWithMeta>> = MutableStateFlow(emptyList())
        override fun observePeriodSummary(from: LocalDate, to: LocalDate): Flow<List<PeriodSummaryByCurrency>> = MutableStateFlow(emptyList())
        override fun observeByCategory(currency: String, from: LocalDate, to: LocalDate, type: TransactionType): Flow<List<CategorySum>> = MutableStateFlow(emptyList())
        override fun observeByAccount(currency: String, from: LocalDate, to: LocalDate, type: TransactionType): Flow<List<AccountSum>> = MutableStateFlow(emptyList())
        override fun observeByAccountAndCategory(currency: String, from: LocalDate, to: LocalDate, type: TransactionType): Flow<List<AccountCategorySum>> = MutableStateFlow(emptyList())
        override fun observeMonthlyTrend(currency: String, from: LocalDate, to: LocalDate): Flow<List<MonthlyBarEntry>> = MutableStateFlow(emptyList())
        override suspend fun getAll(): List<TransactionWithMeta> = emptyList()
        override suspend fun getById(id: Long): Transaction? = null
        override suspend fun getByIds(ids: Set<Long>): List<Transaction> = emptyList()
        override suspend fun save(transaction: Transaction): Long = 0L
        override suspend fun delete(id: Long) = Unit
        override suspend fun deleteByIds(ids: Set<Long>) = Unit
    }

    private val fakeDepositRepo = object : DepositRepository {
        override fun observeAll(): Flow<List<Deposit>> = MutableStateFlow(listOf(deposit()))
        override fun observeExpiringSoon(daysThreshold: Int): Flow<List<Deposit>> = MutableStateFlow(emptyList())
        override suspend fun getAllActive(): List<Deposit> = listOf(deposit())
        override suspend fun getByAccountId(accountId: Long): Deposit? = null // short-circuits recalculation
        override suspend fun save(deposit: Deposit): Long = deposit.id
        override suspend fun markClosed(id: Long) = Unit
    }

    private val fakeDepositEventRepo = object : DepositEventRepository {
        override fun observe(depositId: Long): Flow<List<DepositEvent>> = eventsFlow
        override suspend fun getAll(depositId: Long): List<DepositEvent> = eventsFlow.value
        override suspend fun insert(event: DepositEvent): Long = 0L
        override suspend fun delete(id: Long) { deletedEventIds += id }
        override suspend fun deleteAll(depositId: Long) = Unit
    }

    private val fakeTxRunner = object : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }

    private fun vm() = AccountDetailViewModel(
        accountRepo = fakeAccountRepo,
        transactionRepo = fakeTransactionRepo,
        depositRepo = fakeDepositRepo,
        depositEventRepo = fakeDepositEventRepo,
        txRunner = fakeTxRunner,
        savedStateHandle = SavedStateHandle(mapOf("accountId" to testAccountId)),
    )

    // ── Selection state machine ───────────────────────────────────────────────

    @Test
    fun initialState_selectionModeOff_idsEmpty() {
        val vm = vm()
        assertFalse(vm.isEventSelectionMode.value)
        assertTrue(vm.selectedEventIds.value.isEmpty())
    }

    @Test
    fun enterEventSelectionMode_setsMode_andSingleId() {
        val vm = vm()
        vm.enterEventSelectionMode(5L)
        assertTrue(vm.isEventSelectionMode.value)
        assertEquals(setOf(5L), vm.selectedEventIds.value)
    }

    @Test
    fun enterEventSelectionMode_replacesExistingSelection() {
        val vm = vm()
        vm.enterEventSelectionMode(5L)
        vm.enterEventSelectionMode(9L)
        assertEquals(setOf(9L), vm.selectedEventIds.value)
        assertTrue(vm.isEventSelectionMode.value)
    }

    @Test
    fun toggleEventSelection_addsNewId() {
        val vm = vm()
        vm.enterEventSelectionMode(5L)
        vm.toggleEventSelection(9L)
        assertEquals(setOf(5L, 9L), vm.selectedEventIds.value)
    }

    @Test
    fun toggleEventSelection_removesExistingId() {
        val vm = vm()
        vm.enterEventSelectionMode(5L)
        vm.toggleEventSelection(9L)
        vm.toggleEventSelection(5L)
        assertEquals(setOf(9L), vm.selectedEventIds.value)
        assertTrue(vm.isEventSelectionMode.value)
    }

    @Test
    fun toggleEventSelection_removingLastId_exitsSelectionMode() {
        val vm = vm()
        vm.enterEventSelectionMode(5L)
        vm.toggleEventSelection(5L)
        assertFalse(vm.isEventSelectionMode.value)
        assertTrue(vm.selectedEventIds.value.isEmpty())
    }

    @Test
    fun clearEventSelection_resetsAllSelectionState() {
        val vm = vm()
        vm.enterEventSelectionMode(5L)
        vm.toggleEventSelection(9L)
        vm.clearEventSelection()
        assertFalse(vm.isEventSelectionMode.value)
        assertTrue(vm.selectedEventIds.value.isEmpty())
    }

    // ── deleteSelectedEvents ──────────────────────────────────────────────────

    @Test
    fun deleteSelectedEvents_deletesPrincipalEvent_andClearsSelection() = runTest {
        eventsFlow.value = listOf(principalEvent(id = 1L))
        val vm = vm()

        // Subscribe to activate stateIn flows, then wait for initial emission
        backgroundScope.launch { vm.deposit.collect {} }
        backgroundScope.launch { vm.depositEvents.collect {} }
        advanceUntilIdle()

        vm.enterEventSelectionMode(1L)
        assertTrue(vm.isEventSelectionMode.value)

        vm.deleteSelectedEvents()
        advanceUntilIdle()

        assertFalse(vm.isEventSelectionMode.value)
        assertTrue(vm.selectedEventIds.value.isEmpty())
        assertTrue(deletedEventIds.contains(1L))
    }

    @Test
    fun deleteSelectedEvents_adjustsAccountBalanceByNegatedAmount() = runTest {
        val event = principalEvent(id = 1L, amount = BigDecimal("950000"))
        eventsFlow.value = listOf(event)
        val vm = vm()

        backgroundScope.launch { vm.deposit.collect {} }
        backgroundScope.launch { vm.depositEvents.collect {} }
        advanceUntilIdle()

        vm.enterEventSelectionMode(1L)
        vm.deleteSelectedEvents()
        advanceUntilIdle()

        val adjustment = adjustments.firstOrNull { it.first == testAccountId }
        assertEquals(0, BigDecimal("-950000").compareTo(adjustment?.second))
    }
}
