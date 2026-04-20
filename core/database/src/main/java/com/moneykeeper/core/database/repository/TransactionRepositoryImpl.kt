package com.moneykeeper.core.database.repository

import com.moneykeeper.core.database.dao.AccountDao
import com.moneykeeper.core.database.dao.CategoryDao
import com.moneykeeper.core.database.dao.TransactionDao
import com.moneykeeper.core.database.entity.toDomain
import com.moneykeeper.core.database.entity.toEntity
import com.moneykeeper.core.domain.analytics.AccountSum
import com.moneykeeper.core.domain.analytics.CategorySum
import com.moneykeeper.core.domain.analytics.MonthlyBarEntry
import com.moneykeeper.core.domain.analytics.PeriodSummaryByCurrency
import com.moneykeeper.core.domain.model.Transaction
import com.moneykeeper.core.domain.model.TransactionType
import com.moneykeeper.core.domain.model.TransactionWithMeta
import com.moneykeeper.core.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class TransactionRepositoryImpl(
    private val txDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
) : TransactionRepository {

    /**
     * Объединяет транзакции с данными о счёте/категории через [combine].
     * combine перекомпонует список при любом изменении любой таблицы.
     * В v1 приемлемо; при заметном лаге на 1000+ транзакций — добавить distinctUntilChanged
     * на accountDao/categoryDao (см. §2.7.1).
     */
    override fun observe(
        accountId: Long?,
        categoryId: Long?,
        type: TransactionType?,
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<TransactionWithMeta>> = combine(
        txDao.observe(accountId, categoryId, type?.name, from.toString(), to.toString()),
        accountDao.observeAll(),
        categoryDao.observeAll(),
    ) { txs, accounts, categories ->
        val accById = accounts.associateBy { it.id }
        val catById = categories.associateBy { it.id }
        txs.mapNotNull { tx ->
            val acc = accById[tx.accountId] ?: return@mapNotNull null
            val cat = tx.categoryId?.let { catById[it] }
            TransactionWithMeta(
                transaction = tx.toDomain(),
                accountName = acc.name,
                accountCurrency = acc.currency,
                categoryName = cat?.name.orEmpty(),
                categoryColor = cat?.colorHex ?: "#9E9E9E",
                categoryIcon = cat?.iconName ?: "HelpOutline",
            )
        }
    }

    override fun observeRecent(limit: Int): Flow<List<TransactionWithMeta>> = combine(
        txDao.observeRecent(limit),
        accountDao.observeAll(),
        categoryDao.observeAll(),
    ) { txs, accounts, categories ->
        val accById = accounts.associateBy { it.id }
        val catById = categories.associateBy { it.id }
        txs.mapNotNull { tx ->
            val acc = accById[tx.accountId] ?: return@mapNotNull null
            val cat = tx.categoryId?.let { catById[it] }
            TransactionWithMeta(
                transaction = tx.toDomain(),
                accountName = acc.name,
                accountCurrency = acc.currency,
                categoryName = cat?.name.orEmpty(),
                categoryColor = cat?.colorHex ?: "#9E9E9E",
                categoryIcon = cat?.iconName ?: "HelpOutline",
            )
        }
    }

    override fun observePeriodSummary(from: LocalDate, to: LocalDate): Flow<List<PeriodSummaryByCurrency>> =
        txDao.observePeriodSummary(from.toString(), to.toString()).map { rows ->
            rows.map { PeriodSummaryByCurrency(it.currency, it.income, it.expense) }
        }

    override fun observeByCategory(
        currency: String,
        from: LocalDate,
        to: LocalDate,
        type: TransactionType,
    ): Flow<List<CategorySum>> =
        txDao.observeByCategory(currency, from.toString(), to.toString(), type.name).map { rows ->
            rows.map { CategorySum(it.categoryId, it.total, it.count) }
        }

    override fun observeByAccount(
        currency: String,
        from: LocalDate,
        to: LocalDate,
        type: TransactionType,
    ): Flow<List<AccountSum>> =
        txDao.observeByAccount(currency, from.toString(), to.toString(), type.name).map { rows ->
            rows.map { AccountSum(it.accountId, it.total, it.count) }
        }

    override fun observeMonthlyTrend(currency: String, from: LocalDate, to: LocalDate): Flow<List<MonthlyBarEntry>> =
        txDao.observeMonthlyTrend(currency, from.toString(), to.toString()).map { rows ->
            rows.map { MonthlyBarEntry(it.yearMonth, it.income, it.expense) }
        }

    override suspend fun getById(id: Long): Transaction? = txDao.getById(id)?.toDomain()

    override suspend fun save(transaction: Transaction): Long = txDao.upsert(transaction.toEntity())

    override suspend fun delete(id: Long) {
        txDao.getById(id)?.let { txDao.delete(it) }
    }

    override suspend fun getByIds(ids: Set<Long>): List<Transaction> =
        txDao.getByIds(ids.toList()).map { it.toDomain() }

    override suspend fun deleteByIds(ids: Set<Long>) = txDao.deleteByIds(ids.toList())
}
