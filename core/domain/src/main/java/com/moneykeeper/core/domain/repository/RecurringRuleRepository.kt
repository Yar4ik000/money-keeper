package com.moneykeeper.core.domain.repository

import com.moneykeeper.core.domain.model.RecurringRule
import com.moneykeeper.core.domain.model.RecurringRuleWithTemplate
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface RecurringRuleRepository {
    fun observeAll(): Flow<List<RecurringRule>>
    fun observeAllWithTemplates(): Flow<List<RecurringRuleWithTemplate>>
    suspend fun getAllWithTemplates(today: LocalDate = LocalDate.now()): List<RecurringRuleWithTemplate>
    suspend fun getById(id: Long): RecurringRule?
    suspend fun getByIdWithTemplate(id: Long): RecurringRuleWithTemplate?
    suspend fun save(rule: RecurringRule): Long
    suspend fun updateLastGeneratedDate(id: Long, date: LocalDate)
    suspend fun delete(id: Long)
    suspend fun pruneOrphaned(): Int
}
