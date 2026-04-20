package com.moneykeeper.core.domain.usecase

import java.time.LocalDate
import javax.inject.Inject

class CatchUpRecurringTransactionsUseCase @Inject constructor(
    private val generateUseCase: GenerateRecurringTransactionsUseCase,
) {
    suspend operator fun invoke(today: LocalDate = LocalDate.now()) = generateUseCase(today)
}
