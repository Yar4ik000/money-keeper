package com.moneykeeper.feature.transactions.ui.edit

import androidx.lifecycle.SavedStateHandle
import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.model.AccountType
import com.moneykeeper.core.domain.model.Category
import com.moneykeeper.core.domain.model.CategoryType
import com.moneykeeper.core.domain.model.Frequency
import com.moneykeeper.core.domain.model.RecurringRule
import com.moneykeeper.core.domain.model.RecurringRuleWithTemplate
import com.moneykeeper.core.domain.model.Transaction
import com.moneykeeper.core.domain.model.TransactionType
import com.moneykeeper.core.domain.model.TransactionWithMeta
import com.moneykeeper.core.domain.repository.AccountRepository
import com.moneykeeper.core.domain.repository.CategoryRepository
import com.moneykeeper.core.domain.repository.RecurringRuleRepository
import com.moneykeeper.core.domain.repository.TransactionRepository
import com.moneykeeper.core.domain.repository.TransactionRunner
import com.moneykeeper.feature.transactions.domain.RecurringUpdate
import com.moneykeeper.feature.transactions.domain.TransactionSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class EditTransactionViewModelTest {

    private val today = LocalDate.now()
    private val now = LocalDateTime.now()

    private val rule = RecurringRule(
        id = 7L, frequency = Frequency.MONTHLY, interval = 1,
        startDate = today, endDate = null,
    )
    private val testTx = Transaction(
        id = 42L, accountId = 1L, toAccountId = null,
        amount = BigDecimal("100"), type = TransactionType.EXPENSE,
        categoryId = null, date = today, note = "test",
        recurringRuleId = rule.id, createdAt = now,
    )
    private val testAccount = Account(
        id = 1L, name = "Card", type = AccountType.CARD, currency = "RUB",
        colorHex = "#FF0000", iconName = "card",
        balance = BigDecimal.ZERO, createdAt = today,
    )

    // tracking fakes
    private var savedRuleId: Long? = null
    private var deletedRuleId: Long? = null

    private val fakeAccountRepo = object : AccountRepository {
        override fun observeActiveAccounts(): Flow<List<Account>> = MutableStateFlow(listOf(testAccount))
        override fun observeAllAccounts() = throw UnsupportedOperationException()
        override fun observeTotalsByCurrency() = throw UnsupportedOperationException()
        override suspend fun getById(id: Long) = testAccount.takeIf { it.id == id }
        override suspend fun save(account: Account) = 0L
        override suspend fun archive(id: Long) = Unit
        override suspend fun unarchive(id: Long) = Unit
        override suspend fun updateSortOrders(orderedIds: List<Long>) = Unit
        override suspend fun delete(id: Long) = Unit
        override suspend fun adjustBalance(id: Long, delta: BigDecimal) = Unit
    }

    private val fakeCategoryRepo = object : CategoryRepository {
        override fun observeAll(): Flow<List<Category>> = MutableStateFlow(emptyList())
        override fun observeByType(type: CategoryType) = MutableStateFlow(emptyList<Category>())
        override fun observeRootCategories() = MutableStateFlow(emptyList<Category>())
        override suspend fun getById(id: Long) = null
        override suspend fun save(category: Category) = 0L
        override suspend fun delete(id: Long) = Unit
    }

    private val fakeTxRepo = object : TransactionRepository {
        override fun observe(accountId: Long?, categoryId: Long?, type: TransactionType?, from: LocalDate, to: LocalDate) =
            throw UnsupportedOperationException()
        override fun observeRecent(limit: Int) = throw UnsupportedOperationException()
        override fun observePeriodSummary(from: LocalDate, to: LocalDate) = throw UnsupportedOperationException()
        override fun observeByCategory(currency: String, from: LocalDate, to: LocalDate, type: TransactionType) =
            throw UnsupportedOperationException()
        override fun observeByAccount(currency: String, from: LocalDate, to: LocalDate, type: TransactionType) =
            throw UnsupportedOperationException()
        override fun observeMonthlyTrend(currency: String, from: LocalDate, to: LocalDate) =
            throw UnsupportedOperationException()
        override suspend fun getById(id: Long) = testTx.takeIf { it.id == id }
        override suspend fun getByIds(ids: Set<Long>) = listOf(testTx).filter { it.id in ids }
        override suspend fun save(transaction: Transaction) = transaction.id
        override suspend fun delete(id: Long) = Unit
        override suspend fun deleteByIds(ids: Set<Long>) = Unit
        override suspend fun getAll(): List<TransactionWithMeta> = throw UnsupportedOperationException()
    }

    private lateinit var fakeRuleRepo: RecurringRuleRepository

    @Before
    fun setUpDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDownDispatcher() {
        Dispatchers.resetMain()
    }

    @Before
    fun resetTracking() {
        savedRuleId = null
        deletedRuleId = null
        fakeRuleRepo = object : RecurringRuleRepository {
            private val rules = mutableMapOf(rule.id to rule)
            override fun observeAll() = throw UnsupportedOperationException()
            override fun observeAllWithTemplates() = throw UnsupportedOperationException()
            override suspend fun getAllWithTemplates(today: LocalDate) = throw UnsupportedOperationException()
            override suspend fun getById(id: Long) = rules[id]
            override suspend fun save(rule: RecurringRule): Long {
                savedRuleId = rule.id
                rules[rule.id] = rule
                return rule.id
            }
            override suspend fun updateLastGeneratedDate(id: Long, date: LocalDate) = Unit
            override suspend fun delete(id: Long) {
                deletedRuleId = id
                rules.remove(id)
            }
            override suspend fun pruneOrphaned() = 0
        }
    }

    private fun createViewModel(): EditTransactionViewModel {
        val runner = object : TransactionRunner {
            override suspend fun <T> run(block: suspend () -> T) = block()
        }
        return EditTransactionViewModel(
            transactionSaver = TransactionSaver(fakeTxRepo, fakeAccountRepo, fakeRuleRepo, runner),
            transactionRepo = fakeTxRepo,
            accountRepo = fakeAccountRepo,
            categoryRepo = fakeCategoryRepo,
            recurringRuleRepo = fakeRuleRepo,
            savedStateHandle = SavedStateHandle(mapOf("transactionId" to 42L)),
        )
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `initial state loads recurringRule from repo`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(rule, vm.uiState.value.recurringRule)
        assertTrue(vm.uiState.value.isRecurring)
    }

    @Test
    fun `uncheck then Nazad snaps toggle back with rule intact`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onRecurringToggle(false)
        assertTrue("Dialog must show", vm.showStopSeriesDialog.value)

        vm.onRecurringUncheckConfirm(false)  // "Назад"
        assertFalse("Dialog must dismiss", vm.showStopSeriesDialog.value)
        assertTrue("Toggle must be on", vm.uiState.value.isRecurring)
        assertEquals("Rule must be intact after Назад", rule, vm.uiState.value.recurringRule)
    }

    @Test
    fun `uncheck then OK clears isRecurring and recurringRule`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onRecurringToggle(false)
        vm.onRecurringUncheckConfirm(true)

        assertFalse(vm.uiState.value.isRecurring)
        assertNull(vm.uiState.value.recurringRule)
    }

    @Test
    fun `re-enabling after OK-confirm restores originalRule in UI`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onRecurringToggle(false)
        vm.onRecurringUncheckConfirm(true)  // cleared rule
        assertNull("Sanity check: recurringRule must be null after confirm", vm.uiState.value.recurringRule)

        vm.onRecurringToggle(true)  // user changes mind

        assertTrue("isRecurring must be true", vm.uiState.value.isRecurring)
        assertEquals(
            "Original rule must be restored so UI is not deceiving the user",
            rule, vm.uiState.value.recurringRule,
        )
    }

    @Test
    fun `re-enabling then saving uses Set — rule is genuinely preserved`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onRecurringToggle(false)
        vm.onRecurringUncheckConfirm(true)  // confirmed stop
        vm.onRecurringToggle(true)           // changed mind
        vm.onSave()
        advanceUntilIdle()

        // Set calls recurringRuleRepo.save(rule) — not delete
        assertNotNull("recurringRuleRepo.save must have been called (Set path)", savedRuleId)
        assertNull("recurringRuleRepo.delete must NOT have been called", deletedRuleId)
    }

    @Test
    fun `confirming stop without re-enable saves with StopSeries`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onRecurringToggle(false)
        vm.onRecurringUncheckConfirm(true)  // confirmed stop, no re-enable
        vm.onSave()
        advanceUntilIdle()

        assertEquals("Rule must be deleted by StopSeries", rule.id, deletedRuleId)
        assertNull("recurringRuleRepo.save must NOT have been called", savedRuleId)
    }

    @Test
    fun `dialog fires again on second uncheck attempt after Nazad`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onRecurringToggle(false)
        vm.onRecurringUncheckConfirm(false)  // "Назад"
        assertFalse(vm.showStopSeriesDialog.value)

        vm.onRecurringToggle(false)
        assertTrue("Dialog must appear on second uncheck attempt", vm.showStopSeriesDialog.value)
    }

    @Test
    fun `modifying rule via sheet after re-enable saves the modified rule`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onRecurringToggle(false)
        vm.onRecurringUncheckConfirm(true)
        vm.onRecurringToggle(true)

        // User opens the rule sheet and changes interval to 3
        val modifiedRule = rule.copy(interval = 3)
        vm.onRecurringRuleChange(modifiedRule)
        vm.onSave()
        advanceUntilIdle()

        assertNotNull("Rule must be saved", savedRuleId)
        assertEquals("Modified interval must be persisted", 3, fakeRuleRepo.getById(rule.id)?.interval)
    }
}
