package com.moneykeeper.feature.transactions.ui.add

import androidx.lifecycle.SavedStateHandle
import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.model.AccountType
import com.moneykeeper.core.domain.model.Category
import com.moneykeeper.core.domain.model.Frequency
import com.moneykeeper.core.domain.model.RecurringRule
import com.moneykeeper.core.domain.model.Transaction
import com.moneykeeper.core.domain.model.TransactionType
import com.moneykeeper.core.domain.model.TransactionWithMeta
import com.moneykeeper.core.domain.money.MultiCurrencyTotal
import com.moneykeeper.core.domain.repository.AccountRepository
import com.moneykeeper.core.domain.repository.CategoryRepository
import com.moneykeeper.core.domain.repository.RecurringRuleRepository
import com.moneykeeper.core.domain.repository.TransactionRepository
import com.moneykeeper.core.domain.repository.TransactionRunner
import com.moneykeeper.core.domain.analytics.AccountSum
import com.moneykeeper.core.domain.analytics.CategorySum
import com.moneykeeper.core.domain.analytics.MonthlyBarEntry
import com.moneykeeper.core.domain.analytics.PeriodSummaryByCurrency
import com.moneykeeper.core.domain.model.CategoryType
import com.moneykeeper.core.domain.model.RecurringRuleWithTemplate
import com.moneykeeper.feature.transactions.domain.TransactionSaver
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AddTransactionViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ── fakes ────────────────────────────────────────────────────────────────

    private fun account(id: Long, currency: String) = Account(
        id = id, name = "Account $id", type = AccountType.CARD,
        currency = currency, colorHex = "#4CAF50", iconName = "bank",
        balance = BigDecimal.ZERO, createdAt = LocalDate.now(),
    )

    private fun accountRepo(vararg accounts: Account): AccountRepository {
        val list = accounts.toList()
        return object : AccountRepository {
            override fun observeActiveAccounts(): Flow<List<Account>> = MutableStateFlow(list)
            override fun observeAllAccounts(): Flow<List<Account>> = MutableStateFlow(list)
            override fun observeTotalsByCurrency(): Flow<MultiCurrencyTotal> = throw UnsupportedOperationException()
            override suspend fun getById(id: Long) = list.find { it.id == id }
            override suspend fun save(account: Account) = account.id
            override suspend fun archive(id: Long) = Unit
            override suspend fun unarchive(id: Long) = Unit
            override suspend fun updateSortOrders(orderedIds: List<Long>) = Unit
            override suspend fun delete(id: Long) = Unit
            override suspend fun adjustBalance(id: Long, delta: BigDecimal) = Unit
        }
    }

    private val fakeCategoryRepo = object : CategoryRepository {
        override fun observeAll(): Flow<List<Category>> = MutableStateFlow(emptyList())
        override fun observeByType(type: CategoryType): Flow<List<Category>> = MutableStateFlow(emptyList())
        override fun observeRootCategories(): Flow<List<Category>> = MutableStateFlow(emptyList())
        override suspend fun getById(id: Long): Category? = null
        override suspend fun save(category: Category): Long = 0L
        override suspend fun delete(id: Long) = Unit
    }

    private val fakeTxRepo = object : TransactionRepository {
        val saved = mutableListOf<Transaction>()
        override fun observe(accountId: Long?, categoryId: Long?, type: TransactionType?, from: LocalDate, to: LocalDate): Flow<List<TransactionWithMeta>> = throw UnsupportedOperationException()
        override fun observeRecent(limit: Int): Flow<List<TransactionWithMeta>> = throw UnsupportedOperationException()
        override fun observePeriodSummary(from: LocalDate, to: LocalDate): Flow<List<PeriodSummaryByCurrency>> = throw UnsupportedOperationException()
        override fun observeByCategory(currency: String, from: LocalDate, to: LocalDate, type: TransactionType): Flow<List<CategorySum>> = throw UnsupportedOperationException()
        override fun observeByAccount(currency: String, from: LocalDate, to: LocalDate, type: TransactionType): Flow<List<AccountSum>> = throw UnsupportedOperationException()
        override fun observeMonthlyTrend(currency: String, from: LocalDate, to: LocalDate): Flow<List<MonthlyBarEntry>> = throw UnsupportedOperationException()
        override suspend fun getAll(): List<TransactionWithMeta> = emptyList()
        override suspend fun getById(id: Long): Transaction? = null
        override suspend fun getByIds(ids: Set<Long>): List<Transaction> = emptyList()
        override suspend fun save(transaction: Transaction): Long { saved += transaction; return 1L }
        override suspend fun delete(id: Long) = Unit
        override suspend fun deleteByIds(ids: Set<Long>) = Unit
    }

    private val fakeRuleRepo = object : RecurringRuleRepository {
        override fun observeAll() = throw UnsupportedOperationException()
        override fun observeAllWithTemplates() = throw UnsupportedOperationException()
        override suspend fun getAllWithTemplates(today: LocalDate) = throw UnsupportedOperationException()
        override suspend fun getById(id: Long): RecurringRule? = null
        override suspend fun getByIdWithTemplate(id: Long): RecurringRuleWithTemplate? = null
        override suspend fun save(rule: RecurringRule): Long = 0L
        override suspend fun updateLastGeneratedDate(id: Long, date: LocalDate) = Unit
        override suspend fun delete(id: Long) = Unit
        override suspend fun pruneOrphaned() = 0
    }

    private fun vm(vararg accounts: Account): AddTransactionViewModel {
        val runner = object : TransactionRunner { override suspend fun <T> run(block: suspend () -> T) = block() }
        return AddTransactionViewModel(
            transactionSaver = TransactionSaver(fakeTxRepo, accountRepo(*accounts), fakeRuleRepo, runner),
            accountRepo = accountRepo(*accounts),
            categoryRepo = fakeCategoryRepo,
            savedStateHandle = SavedStateHandle(),
        )
    }

    // ── currency mismatch for TRANSFER ───────────────────────────────────────

    @Test
    fun `transfer between same-currency accounts saves successfully`() = runTest {
        val rubA = account(1L, "RUB")
        val rubB = account(2L, "RUB")
        val viewModel = vm(rubA, rubB)
        viewModel.onTypeChange(TransactionType.TRANSFER)
        viewModel.onAccountSelect(rubA)
        viewModel.onToAccountSelect(rubB)
        viewModel.onAmountInputChange("500")
        viewModel.onSave().join()
        assertTrue(viewModel.uiState.value.saved)
        assertFalse(fakeTxRepo.saved.isEmpty())
    }

    @Test
    fun `transfer between different-currency accounts returns CurrencyMismatch error`() = runTest {
        val rub = account(1L, "RUB")
        val usd = account(2L, "USD")
        val viewModel = vm(rub, usd)
        viewModel.onTypeChange(TransactionType.TRANSFER)
        viewModel.onAccountSelect(rub)
        viewModel.onToAccountSelect(usd)
        viewModel.onAmountInputChange("100")
        viewModel.onSave().join()
        assertEquals(AddTxError.CurrencyMismatch, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.saved)
        assertTrue(fakeTxRepo.saved.isEmpty())
    }
}
