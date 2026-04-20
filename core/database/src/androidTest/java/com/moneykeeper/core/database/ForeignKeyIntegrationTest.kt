package com.moneykeeper.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneykeeper.core.database.entity.AccountEntity
import com.moneykeeper.core.database.entity.CategoryEntity
import com.moneykeeper.core.database.entity.DepositEntity
import com.moneykeeper.core.database.entity.RecurringRuleEntity
import com.moneykeeper.core.database.entity.TransactionEntity
import com.moneykeeper.core.domain.model.AccountType
import com.moneykeeper.core.domain.model.CategoryType
import com.moneykeeper.core.domain.model.Frequency
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
 * Verifies foreign-key ON DELETE behaviour (CASCADE and SET NULL).
 * FK enforcement is disabled by default in Room — enabled explicitly via PRAGMA callback.
 */
@RunWith(AndroidJUnit4::class)
class ForeignKeyIntegrationTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    db.execSQL("PRAGMA foreign_keys = ON")
                }
            })
            .build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insertAccount(name: String, type: AccountType = AccountType.CARD): Long =
        db.accountDao().upsert(
            AccountEntity(
                name = name, type = type, currency = "RUB",
                colorHex = "#000000", iconName = "CreditCard",
                balance = BigDecimal("1000.00"), createdAt = LocalDate.of(2026, 1, 1),
            )
        )

    private suspend fun insertTransaction(
        accountId: Long,
        toAccountId: Long? = null,
        categoryId: Long? = null,
        ruleId: Long? = null,
        type: TransactionType = TransactionType.EXPENSE,
    ): Long = db.transactionDao().upsert(
        TransactionEntity(
            accountId = accountId, toAccountId = toAccountId,
            amount = BigDecimal("100.00"), type = type,
            categoryId = categoryId, date = LocalDate.of(2026, 4, 1),
            note = "", recurringRuleId = ruleId,
            createdAt = LocalDateTime.of(2026, 4, 1, 12, 0),
        )
    )

    // ── CASCADE tests ─────────────────────────────────────────────────────────

    @Test
    fun deleteAccount_cascadesTransactions() = runTest {
        val accId = insertAccount("Card")
        insertTransaction(accId)
        insertTransaction(accId)

        db.accountDao().delete(db.accountDao().getById(accId)!!)

        val txs = db.transactionDao().observe(null, null, null, "2026-01-01", "2026-12-31").first()
        assertTrue(txs.isEmpty())
    }

    @Test
    fun deleteAccount_cascadesDeposit() = runTest {
        val accId = insertAccount("Deposit Account", type = AccountType.DEPOSIT)
        db.depositDao().upsert(
            DepositEntity(
                accountId = accId, initialAmount = BigDecimal("100000.00"),
                interestRate = BigDecimal("12.00"),
                startDate = LocalDate.of(2026, 1, 1), endDate = LocalDate.of(2027, 1, 1),
                isCapitalized = false, capitalizationPeriod = null, payoutAccountId = null,
            )
        )

        db.accountDao().delete(db.accountDao().getById(accId)!!)

        assertTrue(db.depositDao().observeAll().first().isEmpty())
    }

    // ── SET NULL tests ────────────────────────────────────────────────────────

    @Test
    fun deleteCategory_setsTransactionCategoryIdToNull() = runTest {
        val accId = insertAccount("Card")
        val catId = db.categoryDao().upsert(
            CategoryEntity(name = "Food", type = CategoryType.EXPENSE,
                colorHex = "#F00", iconName = "Tag")
        )
        val txId = insertTransaction(accId, categoryId = catId)

        db.categoryDao().delete(db.categoryDao().getById(catId)!!)

        val tx = db.transactionDao().getById(txId)
        assertNotNull(tx)
        assertNull(tx!!.categoryId)
    }

    @Test
    fun deleteRecurringRule_setsTransactionRuleIdToNull() = runTest {
        val accId = insertAccount("Card")
        val ruleId = db.recurringRuleDao().upsert(
            RecurringRuleEntity(frequency = Frequency.MONTHLY,
                startDate = LocalDate.of(2026, 1, 1), endDate = null)
        )
        val txId = insertTransaction(accId, ruleId = ruleId)

        db.recurringRuleDao().delete(db.recurringRuleDao().getById(ruleId)!!)

        val tx = db.transactionDao().getById(txId)
        assertNotNull(tx)
        assertNull(tx!!.recurringRuleId)
    }

    @Test
    fun deleteToAccount_setsTransactionToAccountIdToNull() = runTest {
        val srcId = insertAccount("Source")
        val dstId = insertAccount("Destination")
        val txId = insertTransaction(srcId, toAccountId = dstId, type = TransactionType.TRANSFER)

        db.accountDao().delete(db.accountDao().getById(dstId)!!)

        val tx = db.transactionDao().getById(txId)
        assertNotNull(tx)
        assertNull(tx!!.toAccountId)
    }

    @Test
    fun deletePayoutAccount_setsDepositPayoutAccountIdToNull() = runTest {
        val depositAccId = insertAccount("Deposit Account", type = AccountType.DEPOSIT)
        val payoutAccId = insertAccount("Payout Account")
        db.depositDao().upsert(
            DepositEntity(
                accountId = depositAccId, initialAmount = BigDecimal("100000.00"),
                interestRate = BigDecimal("12.00"),
                startDate = LocalDate.of(2026, 1, 1), endDate = LocalDate.of(2027, 1, 1),
                isCapitalized = false, capitalizationPeriod = null,
                payoutAccountId = payoutAccId,
            )
        )

        db.accountDao().delete(db.accountDao().getById(payoutAccId)!!)

        val deposit = db.depositDao().observeAll().first()[0]
        assertNull(deposit.payoutAccountId)
    }

    @Test
    fun deleteParentCategory_setsChildParentCategoryIdToNull() = runTest {
        val parentId = db.categoryDao().upsert(
            CategoryEntity(name = "Food", type = CategoryType.EXPENSE,
                colorHex = "#F00", iconName = "Tag")
        )
        val childId = db.categoryDao().upsert(
            CategoryEntity(name = "Restaurant", type = CategoryType.EXPENSE,
                colorHex = "#F00", iconName = "Tag", parentCategoryId = parentId)
        )

        db.categoryDao().delete(db.categoryDao().getById(parentId)!!)

        val child = db.categoryDao().getById(childId)
        assertNotNull(child)
        assertNull(child!!.parentCategoryId)
    }
}
