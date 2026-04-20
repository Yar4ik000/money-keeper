package com.moneykeeper.core.domain.usecase

import com.moneykeeper.core.domain.forecast.expandDates
import com.moneykeeper.core.domain.model.TransactionType
import com.moneykeeper.core.domain.repository.AccountRepository
import com.moneykeeper.core.domain.repository.RecurringRuleRepository
import com.moneykeeper.core.domain.repository.TransactionRepository
import com.moneykeeper.core.domain.repository.TransactionRunner
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

class GenerateRecurringTransactionsUseCase @Inject constructor(
    private val recurringRuleRepo: RecurringRuleRepository,
    private val transactionRepo: TransactionRepository,
    private val accountRepo: AccountRepository,
    private val txRunner: TransactionRunner,
) {
    suspend operator fun invoke(today: LocalDate = LocalDate.now()) {
        val rules = recurringRuleRepo.getAllWithTemplates(today)
        for (ruleWithTemplate in rules) {
            txRunner.run {
                val fresh = recurringRuleRepo.getById(ruleWithTemplate.rule.id) ?: return@run
                val lastGenerated = fresh.lastGeneratedDate ?: fresh.startDate.minusDays(1)
                if (!lastGenerated.isBefore(today)) return@run

                val dates = fresh.expandDates(from = lastGenerated.plusDays(1), to = today)
                val template = ruleWithTemplate.templateTransaction
                for (date in dates) {
                    val tx = template.copy(id = 0L, date = date, recurringRuleId = fresh.id)
                    transactionRepo.save(tx)
                    when (tx.type) {
                        TransactionType.INCOME ->
                            accountRepo.adjustBalance(tx.accountId, tx.amount)
                        TransactionType.EXPENSE,
                        TransactionType.SAVINGS ->
                            accountRepo.adjustBalance(tx.accountId, tx.amount.negate())
                        TransactionType.TRANSFER -> {
                            accountRepo.adjustBalance(tx.accountId, tx.amount.negate())
                            tx.toAccountId?.let { accountRepo.adjustBalance(it, tx.amount) }
                        }
                    }
                }
                if (dates.isNotEmpty()) {
                    recurringRuleRepo.updateLastGeneratedDate(fresh.id, today)
                }
            }
        }
    }
}
