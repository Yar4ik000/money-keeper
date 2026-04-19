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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class TransactionDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var txDao: TransactionDao
    private lateinit var accountDao: AccountDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // In-memory БД без SQLCipher — шифрование отдельно от логики DAO
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        txDao = db.transactionDao()
        accountDao = db.accountDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insert_and_query_returns_sorted_by_date_desc() = runTest {
        val accountId = accountDao.upsert(
            AccountEntity(
                name = "Card", type = AccountType.CARD, currency = "RUB",
                colorHex = "#000000", iconName = "CreditCard",
                balance = BigDecimal("1000.00"), createdAt = LocalDate.of(2026, 1, 1),
            )
        )
        val older = TransactionEntity(
            accountId = accountId, toAccountId = null,
            amount = BigDecimal("100.00"), type = TransactionType.EXPENSE,
            categoryId = null, date = LocalDate.of(2026, 4, 1),
            createdAt = LocalDateTime.of(2026, 4, 1, 10, 0),
        )
        val newer = older.copy(
            amount = BigDecimal("200.00"),
            date = LocalDate.of(2026, 4, 10),
            createdAt = LocalDateTime.of(2026, 4, 10, 10, 0),
        )
        txDao.upsert(older)
        txDao.upsert(newer)

        val result = txDao.observe(
            accountId = accountId, categoryId = null, type = null,
            from = "2026-01-01", to = "2026-12-31",
        ).first()

        assertEquals(2, result.size)
        assertEquals(BigDecimal("200.00"), result[0].amount)
        assertEquals(BigDecimal("100.00"), result[1].amount)
    }
}
