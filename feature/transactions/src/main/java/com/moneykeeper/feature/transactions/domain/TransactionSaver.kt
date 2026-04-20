package com.moneykeeper.feature.transactions.domain

import com.moneykeeper.core.domain.model.RecurringRule
import com.moneykeeper.core.domain.model.Transaction
import com.moneykeeper.core.domain.model.TransactionType
import com.moneykeeper.core.domain.repository.AccountRepository
import com.moneykeeper.core.domain.repository.RecurringRuleRepository
import com.moneykeeper.core.domain.repository.TransactionDeleter
import com.moneykeeper.core.domain.repository.TransactionRepository
import com.moneykeeper.core.domain.repository.TransactionRunner
import java.math.BigDecimal
import javax.inject.Inject

class TransactionSaver @Inject constructor(
    private val transactionRepo: TransactionRepository,
    private val accountRepo: AccountRepository,
    private val recurringRuleRepo: RecurringRuleRepository,
    private val txRunner: TransactionRunner,
) : TransactionDeleter {

    suspend fun save(transaction: Transaction, recurringRule: RecurringRule? = null) {
        txRunner.run {
            val ruleId = recurringRule?.let { recurringRuleRepo.save(it) }
            transactionRepo.save(transaction.copy(recurringRuleId = ruleId))
            applyBalanceEffect(transaction)
        }
    }

    suspend fun replace(old: Transaction, new: Transaction, recurringRule: RecurringRule? = null) {
        txRunner.run {
            reverseBalanceEffect(old)
            val ruleId = recurringRule?.let { recurringRuleRepo.save(it) }
            transactionRepo.save(new.copy(recurringRuleId = ruleId ?: old.recurringRuleId))
            applyBalanceEffect(new)
        }
    }

    suspend fun delete(transaction: Transaction) {
        txRunner.run {
            transactionRepo.delete(transaction.id)
            reverseBalanceEffect(transaction)
        }
    }

    override suspend fun deleteMany(ids: Set<Long>) {
        if (ids.isEmpty()) return
        txRunner.run {
            val transactions = transactionRepo.getByIds(ids)
            if (transactions.isEmpty()) return@run
            val deltas = mutableMapOf<Long, BigDecimal>()
            for (tx in transactions) {
                when (tx.type) {
                    TransactionType.INCOME ->
                        deltas.merge(tx.accountId, tx.amount.negate(), BigDecimal::add)
                    TransactionType.EXPENSE,
                    TransactionType.SAVINGS ->
                        deltas.merge(tx.accountId, tx.amount, BigDecimal::add)
                    TransactionType.TRANSFER -> {
                        deltas.merge(tx.accountId, tx.amount, BigDecimal::add)
                        tx.toAccountId?.let { deltas.merge(it, tx.amount.negate(), BigDecimal::add) }
                    }
                }
            }
            transactionRepo.deleteByIds(ids)
            for ((accountId, delta) in deltas) {
                if (delta.signum() != 0) accountRepo.adjustBalance(accountId, delta)
            }
        }
    }

    private suspend fun applyBalanceEffect(tx: Transaction) {
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

    private suspend fun reverseBalanceEffect(tx: Transaction) {
        when (tx.type) {
            TransactionType.INCOME ->
                accountRepo.adjustBalance(tx.accountId, tx.amount.negate())
            TransactionType.EXPENSE,
            TransactionType.SAVINGS ->
                accountRepo.adjustBalance(tx.accountId, tx.amount)
            TransactionType.TRANSFER -> {
                accountRepo.adjustBalance(tx.accountId, tx.amount)
                tx.toAccountId?.let { accountRepo.adjustBalance(it, tx.amount.negate()) }
            }
        }
    }
}
