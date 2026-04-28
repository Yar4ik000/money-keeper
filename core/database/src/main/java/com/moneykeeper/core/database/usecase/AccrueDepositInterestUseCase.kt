package com.moneykeeper.core.database.usecase

import androidx.room.withTransaction
import com.moneykeeper.core.database.DatabaseProvider
import com.moneykeeper.core.database.entity.DepositEventEntity
import com.moneykeeper.core.database.entity.toDomain
import com.moneykeeper.core.domain.calculator.DepositCalculator
import com.moneykeeper.core.domain.model.AccrualBasis
import com.moneykeeper.core.domain.model.CapPeriod
import com.moneykeeper.core.domain.model.DepositEventType
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

class AccrueDepositInterestUseCase @Inject constructor(
    private val databaseProvider: DatabaseProvider,
) {

    suspend fun run() {
        val db = runCatching { databaseProvider.require() }.getOrNull() ?: return
        val today = LocalDate.now()

        val deposits = db.depositDao().getAllActive()
        for (depositEntity in deposits) {
            val deposit = depositEntity.toDomain()
            val step = deposit.capitalizationPeriod ?: CapPeriod.MONTHLY
            val effectiveEnd = minOf<LocalDate>(deposit.endDate ?: today, today)

            val events = db.depositEventDao().getAll(deposit.id)
            val lastAccrualDate = events
                .filter { it.type == DepositEventType.INTEREST_ACCRUAL || it.type == DepositEventType.CAPITALIZATION }
                .maxOfOrNull { it.date }
                ?: deposit.startDate

            var from = lastAccrualDate
            var currentBalance = db.accountDao().getById(deposit.accountId)?.balance ?: continue

            while (true) {
                val periodEnd = DepositCalculator.nextPeriodEnd(from, step)
                // Only process a period once it is fully complete
                if (periodEnd.isAfter(effectiveEnd)) break

                val laterPrincipalDelta = events
                    .filter {
                        it.date > from &&
                            (it.type == DepositEventType.PRINCIPAL_ADD || it.type == DepositEventType.PRINCIPAL_WITHDRAW)
                    }
                    .fold(BigDecimal.ZERO) { acc, e -> acc + e.amount }
                val balanceAtFrom = currentBalance - laterPrincipalDelta

                val eventType = if (deposit.isCapitalized) DepositEventType.CAPITALIZATION else DepositEventType.INTEREST_ACCRUAL
                val slices = when (deposit.accrualBasis) {
                    AccrualBasis.DAILY -> {
                        val changes = events
                            .filter {
                                it.date > from && it.date < periodEnd &&
                                    (it.type == DepositEventType.PRINCIPAL_ADD || it.type == DepositEventType.PRINCIPAL_WITHDRAW)
                            }
                            .map { it.date to it.amount }
                        DepositCalculator.accrueByPeriodDaily(balanceAtFrom, deposit, from, periodEnd, changes)
                    }
                    AccrualBasis.PERIOD_START -> DepositCalculator.accrueByPeriod(balanceAtFrom, deposit, from, periodEnd)
                }
                val totalInterest = slices.fold(BigDecimal.ZERO) { acc, (_, v) -> acc + v }

                if (totalInterest.signum() > 0) {
                    val newEntities = slices.map { (date, amount) ->
                        DepositEventEntity(depositId = deposit.id, date = date, type = eventType, amount = amount)
                    }
                    db.withTransaction {
                        db.depositEventDao().insertAll(newEntities)
                        if (deposit.isCapitalized) {
                            db.accountDao().setBalance(deposit.accountId, currentBalance + totalInterest)
                        }
                    }
                    if (deposit.isCapitalized) currentBalance += totalInterest
                }

                from = periodEnd
            }
        }
    }
}
