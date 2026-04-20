package com.moneykeeper.feature.settings.domain

import android.content.Context
import android.net.Uri
import com.moneykeeper.core.domain.model.TransactionType
import com.moneykeeper.core.domain.repository.TransactionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ExportCsvUseCase @Inject constructor(
    private val transactionRepo: TransactionRepository,
    @ApplicationContext private val context: Context,
) {
    suspend fun export(uri: Uri) {
        val transactions = transactionRepo.getAll()

        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.write("\uFEFF")
            writer.write(
                listOf("Дата", "Тип", "Категория", "Счёт", "Счёт получатель", "Сумма", "Заметка")
                    .joinToString(";") { it.csvEscape() } + "\r\n"
            )

            transactions.sortedByDescending { it.transaction.date }.forEach { meta ->
                val tx = meta.transaction
                val type = when (tx.type) {
                    TransactionType.INCOME   -> "Доход"
                    TransactionType.EXPENSE  -> "Расход"
                    TransactionType.TRANSFER -> "Перевод"
                    TransactionType.SAVINGS  -> "Сбережения"
                }
                val row = listOf(
                    tx.date.toString(),
                    type,
                    meta.categoryName,
                    meta.accountName,
                    "",
                    tx.amount.toPlainString(),
                    tx.note,
                ).joinToString(";") { it.csvEscape() }
                writer.write(row)
                writer.write("\r\n")
            }
        }
    }
}
