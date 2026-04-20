package com.moneykeeper.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneykeeper.core.database.dao.AccountDao
import com.moneykeeper.core.database.entity.AccountEntity
import com.moneykeeper.core.domain.model.AccountType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AccountDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: AccountDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.accountDao()
    }

    @After
    fun tearDown() = db.close()

    private fun account(
        name: String,
        currency: String = "RUB",
        balance: BigDecimal = BigDecimal("1000.00"),
        isArchived: Boolean = false,
        sortOrder: Int = 0,
    ) = AccountEntity(
        name = name, type = AccountType.CARD, currency = currency,
        colorHex = "#000000", iconName = "CreditCard",
        balance = balance, isArchived = isArchived,
        createdAt = LocalDate.of(2026, 1, 1), sortOrder = sortOrder,
    )

    private fun assertAmount(expected: BigDecimal, actual: BigDecimal) =
        assertTrue("Expected ${expected.toPlainString()} but got ${actual.toPlainString()}",
            expected.compareTo(actual) == 0)

    @Test
    fun upsert_returnsGeneratedId() = runTest {
        val id = dao.upsert(account("Alpha"))
        assertTrue(id > 0)
    }

    @Test
    fun getById_returnsInsertedAccount() = runTest {
        val id = dao.upsert(account("Alpha"))
        val fetched = dao.getById(id)
        assertNotNull(fetched)
        assertEquals("Alpha", fetched!!.name)
        assertAmount(BigDecimal("1000.00"), fetched.balance)
    }

    @Test
    fun observeActive_excludesArchivedAccounts() = runTest {
        dao.upsert(account("Active"))
        val archId = dao.upsert(account("ToArchive"))
        dao.upsert(account("AlreadyArchived", isArchived = true))
        dao.archive(archId)

        val active = dao.observeActive().first()
        assertTrue(active.any { it.name == "Active" })
        assertFalse(active.any { it.name == "ToArchive" })
        assertFalse(active.any { it.name == "AlreadyArchived" })
    }

    @Test
    fun observeAll_includesArchivedAccounts() = runTest {
        dao.upsert(account("Active"))
        dao.upsert(account("Archived", isArchived = true))

        val all = dao.observeAll().first()
        assertEquals(2, all.size)
    }

    @Test
    fun adjustBalance_addsPositiveDelta() = runTest {
        val id = dao.upsert(account("Card", balance = BigDecimal("500.00")))
        val current = dao.getById(id)!!.balance
        dao.setBalance(id, current + BigDecimal("200.00"))
        assertAmount(BigDecimal("700.00"), dao.getById(id)!!.balance)
    }

    @Test
    fun adjustBalance_subtractsNegativeDelta() = runTest {
        val id = dao.upsert(account("Card", balance = BigDecimal("1000.00")))
        val current = dao.getById(id)!!.balance
        dao.setBalance(id, current + BigDecimal("-300.00"))
        assertAmount(BigDecimal("700.00"), dao.getById(id)!!.balance)
    }

    @Test
    fun adjustBalance_canProduceNegativeBalance() = runTest {
        val id = dao.upsert(account("Card", balance = BigDecimal("100.00")))
        val current = dao.getById(id)!!.balance
        dao.setBalance(id, current + BigDecimal("-500.00"))
        assertAmount(BigDecimal("-400.00"), dao.getById(id)!!.balance)
    }

    @Test
    fun observeTotalsByCurrency_sumsBalancesPerCurrency() = runTest {
        dao.upsert(account("RUB-1", currency = "RUB", balance = BigDecimal("1000.00")))
        dao.upsert(account("RUB-2", currency = "RUB", balance = BigDecimal("2000.00")))
        dao.upsert(account("USD-1", currency = "USD", balance = BigDecimal("500.00")))

        val totals = dao.observeTotalsByCurrency().first()
        assertEquals(2, totals.size)
        val rub = totals.first { it.currency == "RUB" }
        val usd = totals.first { it.currency == "USD" }
        assertAmount(BigDecimal("3000.00"), rub.total)
        assertAmount(BigDecimal("500.00"), usd.total)
    }

    @Test
    fun observeTotalsByCurrency_excludesArchivedAccounts() = runTest {
        dao.upsert(account("Active", currency = "RUB", balance = BigDecimal("1000.00")))
        val archId = dao.upsert(account("Archived", currency = "RUB", balance = BigDecimal("5000.00")))
        dao.archive(archId)

        val totals = dao.observeTotalsByCurrency().first()
        assertEquals(1, totals.size)
        assertAmount(BigDecimal("1000.00"), totals[0].total)
    }

    @Test
    fun sortOrder_respected_in_observeActive() = runTest {
        dao.upsert(account("B", sortOrder = 2))
        dao.upsert(account("A", sortOrder = 1))
        dao.upsert(account("C", sortOrder = 3))

        val result = dao.observeActive().first()
        assertEquals(listOf("A", "B", "C"), result.map { it.name })
    }

    @Test
    fun delete_removesAccount() = runTest {
        val id = dao.upsert(account("Temp"))
        val entity = dao.getById(id)!!
        dao.delete(entity)
        assertNull(dao.getById(id))
    }

    @Test
    fun update_changesName() = runTest {
        val id = dao.upsert(account("OldName"))
        val entity = dao.getById(id)!!
        dao.update(entity.copy(name = "NewName"))
        assertEquals("NewName", dao.getById(id)!!.name)
    }
}
