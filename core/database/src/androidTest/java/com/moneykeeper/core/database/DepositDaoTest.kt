package com.moneykeeper.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneykeeper.core.database.dao.AccountDao
import com.moneykeeper.core.database.dao.DepositDao
import com.moneykeeper.core.database.entity.AccountEntity
import com.moneykeeper.core.database.entity.DepositEntity
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
class DepositDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var depositDao: DepositDao
    private lateinit var accountDao: AccountDao
    private var accountId = 0L

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        depositDao = db.depositDao()
        accountDao = db.accountDao()
        accountId = accountDao.upsert(
            AccountEntity(
                name = "Deposit Account", type = AccountType.DEPOSIT, currency = "RUB",
                colorHex = "#42A5F5", iconName = "AccountBalance",
                balance = BigDecimal("100000.00"), createdAt = LocalDate.of(2026, 1, 1),
            )
        )
    }

    @After
    fun tearDown() = db.close()

    private fun deposit(
        acctId: Long = accountId,
        endDate: LocalDate? = LocalDate.of(2027, 1, 1),
        isActive: Boolean = true,
        rate: BigDecimal = BigDecimal("12.00"),
    ) = DepositEntity(
        accountId = acctId,
        initialAmount = BigDecimal("100000.00"),
        interestRate = rate,
        startDate = LocalDate.of(2026, 1, 1),
        endDate = endDate,
        isCapitalized = false,
        capitalizationPeriod = null,
        payoutAccountId = null,
        isActive = isActive,
    )

    @Test
    fun insert_and_getByAccountId_returnsDeposit() = runTest {
        depositDao.upsert(deposit())
        val result = depositDao.getByAccountId(accountId)
        assertNotNull(result)
        assertEquals(BigDecimal("12.00"), result!!.interestRate)
        assertEquals(BigDecimal("100000.00"), result.initialAmount)
    }

    @Test
    fun observeActive_excludesClosedDeposits() = runTest {
        val id = depositDao.upsert(deposit())
        depositDao.markClosed(id)
        assertTrue(depositDao.observeActive().first().isEmpty())
    }

    @Test
    fun markClosed_setsIsActiveFalse() = runTest {
        val id = depositDao.upsert(deposit())
        depositDao.markClosed(id)
        val all = depositDao.observeAll().first()
        assertEquals(1, all.size)
        assertFalse(all[0].isActive)
    }

    @Test
    fun getAllActive_excludesClosedDeposits() = runTest {
        val id = depositDao.upsert(deposit())
        depositDao.markClosed(id)
        assertTrue(depositDao.getAllActive().isEmpty())
    }

    @Test
    fun deposit_withNullEndDate_isAllowed() = runTest {
        depositDao.upsert(deposit(endDate = null))
        val result = depositDao.getByAccountId(accountId)
        assertNotNull(result)
        assertNull(result!!.endDate)
    }

    @Test
    fun observeExpiringSoon_includesDepositsWithinWindow() = runTest {
        depositDao.upsert(deposit(endDate = LocalDate.of(2026, 4, 25)))
        val expiring = depositDao.observeExpiringSoon("2026-04-20", "2026-05-20").first()
        assertEquals(1, expiring.size)
    }

    @Test
    fun observeExpiringSoon_excludesDepositsBeyondThreshold() = runTest {
        depositDao.upsert(deposit(endDate = LocalDate.of(2026, 6, 1)))
        val expiring = depositDao.observeExpiringSoon("2026-04-20", "2026-05-20").first()
        assertTrue(expiring.isEmpty())
    }

    @Test
    fun observeExpiringSoon_excludesAlreadyExpiredDeposits() = runTest {
        val acct2 = accountDao.upsert(
            AccountEntity(
                name = "Old Deposit", type = AccountType.DEPOSIT, currency = "RUB",
                colorHex = "#222", iconName = "AccountBalance",
                balance = BigDecimal.ZERO, createdAt = LocalDate.of(2026, 1, 1),
            )
        )
        depositDao.upsert(deposit(acctId = acct2, endDate = LocalDate.of(2026, 4, 1)))
        val expiring = depositDao.observeExpiringSoon("2026-04-20", "2026-05-20").first()
        assertTrue(expiring.isEmpty())
    }

    @Test
    fun observeExpiringSoon_excludesOpenEndedDeposits() = runTest {
        depositDao.upsert(deposit(endDate = null))
        val expiring = depositDao.observeExpiringSoon("2026-04-20", "2026-05-20").first()
        assertTrue(expiring.isEmpty())
    }

    @Test
    fun upsertWithSameAccountId_replacesExistingDeposit() = runTest {
        depositDao.upsert(deposit(rate = BigDecimal("12.00")))
        depositDao.upsert(deposit(rate = BigDecimal("15.00")))
        val all = depositDao.observeAll().first()
        assertEquals(1, all.size)
        assertEquals(BigDecimal("15.00"), all[0].interestRate)
    }

    @Test
    fun delete_removesDeposit() = runTest {
        val id = depositDao.upsert(deposit())
        val entity = depositDao.observeAll().first()[0]
        depositDao.delete(entity)
        assertTrue(depositDao.observeAll().first().isEmpty())
    }
}
