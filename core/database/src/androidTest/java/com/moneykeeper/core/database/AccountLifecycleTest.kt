package com.moneykeeper.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneykeeper.core.database.dao.AccountDao
import com.moneykeeper.core.database.dao.TransactionDao
import com.moneykeeper.core.database.entity.AccountEntity
import com.moneykeeper.core.database.entity.TransactionEntity
import com.moneykeeper.core.domain.model.AccountType
import com.moneykeeper.core.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Integration scenarios for account archive/unarchive lifecycle.
 *
 * Tests the full chain: archive → verify exclusion from active list and balance totals
 * → verify transactions survive (no cascade delete) → unarchive → full restoration.
 */
@RunWith(AndroidJUnit4::class)
class AccountLifecycleTest {

    private lateinit var db: AppDatabase
    private lateinit var accountDao: AccountDao
    private lateinit var txDao: TransactionDao

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accountDao = db.accountDao()
        txDao = db.transactionDao()
    }

    @After
    fun tearDown() = db.close()

    // ── helpers ───────────────────────────────────────────────────────────────

    private suspend fun newAccount(
        name: String,
        currency: String = "RUB",
        balance: BigDecimal = BigDecimal("1000"),
    ) = accountDao.upsert(
        AccountEntity(
            name = name, type = AccountType.CARD, currency = currency,
            colorHex = "#000", iconName = "CreditCard",
            balance = balance, createdAt = LocalDate.of(2026, 1, 1),
        )
    )

    private suspend fun newTx(accountId: Long, amount: BigDecimal = BigDecimal("500")) =
        txDao.upsert(
            TransactionEntity(
                accountId = accountId, toAccountId = null,
                amount = amount, type = TransactionType.EXPENSE,
                categoryId = null, date = LocalDate.of(2026, 4, 10),
                note = "", recurringRuleId = null,
                createdAt = LocalDateTime.of(2026, 4, 10, 12, 0),
            )
        )

    private fun assertAmount(expected: BigDecimal, actual: BigDecimal) =
        assertTrue(
            "Expected ${expected.toPlainString()} but got ${actual.toPlainString()}",
            expected.compareTo(actual) == 0,
        )

    // ── archive visibility ────────────────────────────────────────────────────

    @Test
    fun archive_removesFromActiveList_butVisibleInAll() = runTest {
        val cardId = newAccount("Карта")
        val cashId = newAccount("Наличные")

        accountDao.archive(cardId)

        val active = accountDao.observeActive().first()
        assertEquals(1, active.size)
        assertEquals("Наличные", active[0].name)

        val all = accountDao.observeAll().first()
        assertEquals(2, all.size)
    }

    @Test
    fun archive3accounts_unarchive1_correctActiveCount() = runTest {
        val id1 = newAccount("Карта 1")
        val id2 = newAccount("Карта 2")
        val id3 = newAccount("Карта 3")

        accountDao.archive(id1)
        accountDao.archive(id2)

        assertEquals(1, accountDao.observeActive().first().size)
        assertEquals(3, accountDao.observeAll().first().size)

        accountDao.unarchive(id1)

        val active = accountDao.observeActive().first()
        assertEquals(2, active.size)
        assertTrue(active.any { it.id == id1 })
        assertTrue(active.any { it.id == id3 })
    }

    // ── transactions survive archive/unarchive ────────────────────────────────

    @Test
    fun archiveAccount_transactionsPersistInDatabase() = runTest {
        val cardId = newAccount("Карта")
        newTx(cardId, BigDecimal("1200"))
        newTx(cardId, BigDecimal("800"))

        accountDao.archive(cardId)

        // Transactions must still exist — archive is soft, not a delete
        val allTxs = txDao.getAll()
        assertEquals(2, allTxs.size)
        assertEquals(cardId, allTxs[0].accountId)
    }

    @Test
    fun archivedAccountTransactions_stillVisibleWithNoAccountFilter() = runTest {
        val activeId = newAccount("Активная")
        val archivedId = newAccount("Архивная")

        newTx(activeId, BigDecimal("300"))
        newTx(archivedId, BigDecimal("700"))

        accountDao.archive(archivedId)

        // observe() with no account filter sees ALL transactions including archived account's
        val all = txDao.observe(
            accountId = null, categoryId = null, type = null,
            from = "2026-01-01", to = "2026-12-31",
        ).first()
        assertEquals(2, all.size)

        // Filtering by archivedId specifically still works
        val archivedTxs = txDao.observe(
            accountId = archivedId, categoryId = null, type = null,
            from = "2026-01-01", to = "2026-12-31",
        ).first()
        assertEquals(1, archivedTxs.size)
        assertAmount(BigDecimal("700"), archivedTxs[0].amount)
    }

    @Test
    fun unarchive_transactionsUnchanged() = runTest {
        val id = newAccount("Карта")
        newTx(id, BigDecimal("500"))
        newTx(id, BigDecimal("250"))

        accountDao.archive(id)
        accountDao.unarchive(id)

        val txs = txDao.getAll()
        assertEquals(2, txs.size)
        assertTrue(txs.all { it.accountId == id })
    }

    // ── balance totals (observeTotalsByCurrency) ──────────────────────────────

    @Test
    fun archiveAccount_balanceExcludedFromTotals() = runTest {
        val cardId = newAccount("Карта", "RUB", BigDecimal("10000"))
        val cashId = newAccount("Наличные", "RUB", BigDecimal("5000"))

        accountDao.archive(cardId)

        val totals = accountDao.observeTotalsByCurrency().first()
        assertEquals(1, totals.size)
        assertEquals("RUB", totals[0].currency)
        // Only Cash's balance should contribute
        assertAmount(BigDecimal("5000"), totals[0].total)
    }

    @Test
    fun archiveAllAccounts_totalsByCurrencyIsEmpty() = runTest {
        val id1 = newAccount("A", "RUB", BigDecimal("3000"))
        val id2 = newAccount("B", "RUB", BigDecimal("2000"))

        accountDao.archive(id1)
        accountDao.archive(id2)

        val totals = accountDao.observeTotalsByCurrency().first()
        assertTrue(totals.isEmpty())
    }

    @Test
    fun unarchiveAccount_balanceRestoredInTotals() = runTest {
        val cardId = newAccount("Карта", "RUB", BigDecimal("8000"))
        val cashId = newAccount("Наличные", "RUB", BigDecimal("2000"))

        accountDao.archive(cardId)
        // Only cash visible
        var totals = accountDao.observeTotalsByCurrency().first()
        assertAmount(BigDecimal("2000"), totals.single().total)

        accountDao.unarchive(cardId)
        // Both accounts active again
        totals = accountDao.observeTotalsByCurrency().first()
        assertAmount(BigDecimal("10000"), totals.single().total)
    }

    @Test
    fun multiCurrency_archiveOneAccount_doesNotAffectOtherCurrency() = runTest {
        newAccount("RUB Карта", "RUB", BigDecimal("5000"))
        val usdId = newAccount("USD Карта", "USD", BigDecimal("100"))

        accountDao.archive(usdId)

        val totals = accountDao.observeTotalsByCurrency().first()
        assertEquals(1, totals.size)
        assertEquals("RUB", totals[0].currency)
        assertAmount(BigDecimal("5000"), totals[0].total)
    }
}
