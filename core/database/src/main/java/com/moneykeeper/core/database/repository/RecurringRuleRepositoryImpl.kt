package com.moneykeeper.core.database.repository

import com.moneykeeper.core.database.dao.AccountDao
import com.moneykeeper.core.database.dao.CategoryDao
import com.moneykeeper.core.database.dao.RecurringRuleDao
import com.moneykeeper.core.database.dao.TransactionDao
import com.moneykeeper.core.database.entity.RecurringRuleEntity
import com.moneykeeper.core.database.entity.toDomain
import com.moneykeeper.core.database.entity.toEntity
import com.moneykeeper.core.domain.model.RecurringRule
import com.moneykeeper.core.domain.model.RecurringRuleWithTemplate
import com.moneykeeper.core.domain.repository.RecurringRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class RecurringRuleRepositoryImpl(
    private val ruleDao: RecurringRuleDao,
    private val txDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
) : RecurringRuleRepository {

    override fun observeAll(): Flow<List<RecurringRule>> =
        ruleDao.observeAll().map { it.map { e -> e.toDomain() } }

    override fun observeAllWithTemplates(): Flow<List<RecurringRuleWithTemplate>> =
        ruleDao.observeAll().map { rules -> rules.mapNotNull { buildAggregate(it) } }

    override suspend fun getAllWithTemplates(today: LocalDate): List<RecurringRuleWithTemplate> =
        ruleDao.getAllActive(today.toString()).mapNotNull { buildAggregate(it) }

    override suspend fun getById(id: Long): RecurringRule? = ruleDao.getById(id)?.toDomain()

    override suspend fun getByIdWithTemplate(id: Long): RecurringRuleWithTemplate? =
        ruleDao.getById(id)?.let { buildAggregate(it) }

    override suspend fun save(rule: RecurringRule): Long =
        if (rule.id == 0L) ruleDao.insert(rule.toEntity())
        else { ruleDao.update(rule.toEntity()); rule.id }

    override suspend fun updateLastGeneratedDate(id: Long, date: LocalDate) =
        ruleDao.updateLastGeneratedDate(id, date.toString())

    override suspend fun delete(id: Long) {
        ruleDao.getById(id)?.let { ruleDao.delete(it) }
    }

    override suspend fun pruneOrphaned(): Int = ruleDao.deleteOrphaned()

    /**
     * Строит агрегат правило + шаблон.
     * Шаблон — последняя транзакция по этому правилу (seed из AddTransactionViewModel).
     * Если seed ещё не создан — null; правило не войдёт в ForecastEngine.
     */
    private suspend fun buildAggregate(rule: RecurringRuleEntity): RecurringRuleWithTemplate? {
        val template = txDao.getLastByRule(rule.id) ?: return null
        val account = accountDao.getById(template.accountId) ?: return null
        val category = template.categoryId?.let { categoryDao.getById(it) }
        return RecurringRuleWithTemplate(
            rule = rule.toDomain(),
            templateTransaction = template.toDomain(),
            accountName = account.name,
            categoryName = category?.name.orEmpty(),
            description = template.note.takeIf { it.isNotBlank() } ?: category?.name ?: account.name,
        )
    }
}
