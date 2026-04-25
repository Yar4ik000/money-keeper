package com.moneykeeper.feature.accounts.ui.transfer

import com.moneykeeper.core.domain.analytics.AccountSum
import com.moneykeeper.core.domain.analytics.CategorySum
import com.moneykeeper.core.domain.analytics.MonthlyBarEntry
import com.moneykeeper.core.domain.analytics.PeriodSummaryByCurrency
import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.model.AccountType
import com.moneykeeper.core.domain.model.Transaction
import com.moneykeeper.core.domain.model.TransactionType
import com.moneykeeper.core.domain.model.TransactionWithMeta
import com.moneykeeper.core.domain.money.MultiCurrencyTotal
import com.moneykeeper.core.domain.repository.AccountRepository
import com.moneykeeper.core.domain.repository.TransactionRepository
import com.moneykeeper.core.domain.repository.TransactionRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class TransferViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ── fakes ────────────────────────────────────────────────────────────────

    private fun account(id: Long, currency: String) = Account(
        id = id,
        name = "Account $id",
        type = AccountType.CARD,
        currency = currency,
        colorHex = "#4CAF50",
        iconName = "bank",
        balance = BigDecimal("1000"),
        createdAt = LocalDate.now(),
    )

    private fun accountRepo(vararg accounts: Account): AccountRepository {
        val map = accounts.associateBy { it.id }.toMutableMap()
        return object : AccountRepository {
            override fun observeActiveAccounts(): Flow<List<Account>> = MutableStateFlow(accounts.toList())
            override fun observeAllAccounts(): Flow<List<Account>> = MutableStateFlow(accounts.toList())
            override fun observeTotalsByCurrency(): Flow<MultiCurrencyTotal> = throw UnsupportedOperationException()
            override suspend fun getById(id: Long): Account? = map[id]
            override suspend fun save(account: Account): Long = account.id
            override suspend fun archive(id: Long) = Unit
            override suspend fun unarchive(id: Long) = Unit
            override suspend fun updateSortOrders(orderedIds: List<Long>) = Unit
            override suspend fun delete(id: Long) = Unit
            override suspend fun adjustBalance(id: Long, delta: BigDecimal) = Unit
        }
    }

    private val fakeTxRepo = object : TransactionRepository {
        override fun observe(accountId: Long?, categoryId: Long?, type: TransactionType?, from: LocalDate, to: LocalDate): Flow<List<TransactionWithMeta>> = throw UnsupportedOperationException()
        override fun observeRecent(limit: Int): Flow<List<TransactionWithMeta>> = throw UnsupportedOperationException()
        override fun observePeriodSummary(from: LocalDate, to: LocalDate): Flow<List<PeriodSummaryByCurrency>> = throw UnsupportedOperationException()
        override fun observeByCategory(currency: String, from: LocalDate, to: LocalDate, type: TransactionType): Flow<List<CategorySum>> = throw UnsupportedOperationException()
        override fun observeByAccount(currency: String, from: LocalDate, to: LocalDate, type: TransactionType): Flow<List<AccountSum>> = throw UnsupportedOperationException()
        override fun observeMonthlyTrend(currency: String, from: LocalDate, to: LocalDate): Flow<List<MonthlyBarEntry>> = throw UnsupportedOperationException()
        override suspend fun getAll(): List<TransactionWithMeta> = emptyList()
        override suspend fun getById(id: Long): Transaction? = null
        override suspend fun getByIds(ids: Set<Long>): List<Transaction> = emptyList()
        override suspend fun save(transaction: Transaction): Long = 1L
        override suspend fun delete(id: Long) = Unit
        override suspend fun deleteByIds(ids: Set<Long>) = Unit
    }

    private val fakeRunner = object : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }

    private fun vm(vararg accounts: Account) = TransferViewModel(
        accountRepo = accountRepo(*accounts),
        transactionRepo = fakeTxRepo,
        txRunner = fakeRunner,
    )

    // ── currency mismatch ────────────────────────────────────────────────────

    @Test
    fun `transfer between same-currency accounts succeeds`() = runTest {
        val rubA = account(1L, "RUB")
        val rubB = account(2L, "RUB")
        val viewModel = vm(rubA, rubB)
        viewModel.onFromChange(1L)
        viewModel.onToChange(2L)
        viewModel.onAmountChange(BigDecimal("100"))
        viewModel.transfer().join()
        assertTrue(viewModel.uiState.value.saved)
    }

    @Test
    fun `transfer between different-currency accounts returns CurrencyMismatch error`() = runTest {
        val rub = account(1L, "RUB")
        val usd = account(2L, "USD")
        val viewModel = vm(rub, usd)
        viewModel.onFromChange(1L)
        viewModel.onToChange(2L)
        viewModel.onAmountChange(BigDecimal("100"))
        viewModel.transfer().join()
        assertEquals(TransferError.CurrencyMismatch, viewModel.uiState.value.error)
        assertTrue(!viewModel.uiState.value.saved)
    }

    @Test
    fun `transfer to same account returns SameAccount error`() = runTest {
        val rub = account(1L, "RUB")
        val viewModel = vm(rub)
        viewModel.onFromChange(1L)
        viewModel.onToChange(1L)
        viewModel.onAmountChange(BigDecimal("100"))
        viewModel.transfer().join()
        assertEquals(TransferError.SameAccount, viewModel.uiState.value.error)
    }
}
