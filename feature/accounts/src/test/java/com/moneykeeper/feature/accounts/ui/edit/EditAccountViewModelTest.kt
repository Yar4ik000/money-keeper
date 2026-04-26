package com.moneykeeper.feature.accounts.ui.edit

import androidx.lifecycle.SavedStateHandle
import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.model.AccountType
import com.moneykeeper.core.domain.model.Deposit
import com.moneykeeper.core.domain.repository.AccountRepository
import com.moneykeeper.core.domain.repository.DepositRepository
import com.moneykeeper.core.domain.money.MultiCurrencyTotal
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
class EditAccountViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ── fakes ────────────────────────────────────────────────────────────────

    private fun account(id: Long, name: String) = Account(
        id = id,
        name = name,
        type = AccountType.CARD,
        currency = "RUB",
        colorHex = "#4CAF50",
        iconName = "bank",
        balance = BigDecimal.ZERO,
        createdAt = LocalDate.now(),
    )

    private fun accountRepo(existing: List<Account> = emptyList()): AccountRepository {
        val stored = existing.toMutableList()
        return object : AccountRepository {
            override fun observeActiveAccounts(): Flow<List<Account>> = MutableStateFlow(stored)
            override fun observeAllAccounts(): Flow<List<Account>> = MutableStateFlow(stored)
            override fun observeTotalsByCurrency(): Flow<MultiCurrencyTotal> = throw UnsupportedOperationException()
            override suspend fun getById(id: Long): Account? = stored.find { it.id == id }
            override suspend fun save(account: Account): Long {
                stored.removeIf { it.id == account.id }
                stored.add(account)
                return account.id
            }
            override suspend fun archive(id: Long) = Unit
            override suspend fun unarchive(id: Long) = Unit
            override suspend fun updateSortOrders(orderedIds: List<Long>) = Unit
            override suspend fun delete(id: Long) = Unit
            override suspend fun adjustBalance(id: Long, delta: BigDecimal) = Unit
        }
    }

    private val fakeDepositRepo = object : DepositRepository {
        override fun observeAll(): Flow<List<Deposit>> = MutableStateFlow(emptyList())
        override fun observeExpiringSoon(daysThreshold: Int): Flow<List<Deposit>> = MutableStateFlow(emptyList())
        override suspend fun getAllActive(): List<Deposit> = emptyList()
        override suspend fun getByAccountId(accountId: Long): Deposit? = null
        override suspend fun save(deposit: Deposit): Long = 0L
        override suspend fun markClosed(id: Long) = Unit
    }

    private fun vm(
        accountId: Long? = null,
        existing: List<Account> = emptyList(),
    ) = EditAccountViewModel(
        accountRepo = accountRepo(existing),
        depositRepo = fakeDepositRepo,
        savedStateHandle = SavedStateHandle(mapOf("accountId" to (accountId ?: -1L))),
    )

    // ── duplicate name tests ─────────────────────────────────────────────────

    @Test
    fun `save with unique name succeeds`() = runTest {
        val viewModel = vm(existing = listOf(account(1L, "Карта")))
        viewModel.onNameChange("Наличные")
        viewModel.save().join()
        assertTrue(viewModel.uiState.value.saved)
    }

    @Test
    fun `save with duplicate name sets NameTaken error`() = runTest {
        val viewModel = vm(existing = listOf(account(1L, "Карта")))
        viewModel.onNameChange("Карта")
        viewModel.save().join()
        assertEquals(EditAccountError.NameTaken, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.saved)
    }

    @Test
    fun `duplicate check is case-insensitive`() = runTest {
        val viewModel = vm(existing = listOf(account(1L, "карта")))
        viewModel.onNameChange("КАРТА")
        viewModel.save().join()
        assertEquals(EditAccountError.NameTaken, viewModel.uiState.value.error)
    }

    @Test
    fun `duplicate check ignores leading and trailing whitespace`() = runTest {
        val viewModel = vm(existing = listOf(account(1L, "Карта")))
        viewModel.onNameChange("  Карта  ")
        viewModel.save().join()
        assertEquals(EditAccountError.NameTaken, viewModel.uiState.value.error)
    }

    @Test
    fun `editing existing account can keep its own name without NameTaken error`() = runTest {
        val viewModel = vm(accountId = 1L, existing = listOf(account(1L, "Карта")))
        viewModel.onNameChange("Карта")
        viewModel.save().join()
        assertTrue(viewModel.uiState.value.saved)
    }

    @Test
    fun `editing existing account cannot use another account name`() = runTest {
        val viewModel = vm(
            accountId = 2L,
            existing = listOf(account(1L, "Карта"), account(2L, "Наличные")),
        )
        viewModel.onNameChange("Карта")
        viewModel.save().join()
        assertEquals(EditAccountError.NameTaken, viewModel.uiState.value.error)
    }
}
