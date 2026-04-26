package com.moneykeeper.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.moneykeeper.core.domain.calculator.DepositCalculator
import com.moneykeeper.core.domain.model.CapPeriod
import com.moneykeeper.core.domain.model.Deposit
import com.moneykeeper.core.domain.model.RateTier
import org.json.JSONArray
import java.math.BigDecimal
import java.time.LocalDate

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `deposit_events` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `depositId` INTEGER NOT NULL,
                `date` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `amount` TEXT NOT NULL,
                `note` TEXT NOT NULL DEFAULT '',
                FOREIGN KEY(`depositId`) REFERENCES `deposits`(`id`) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `idx_deposit_events_depositId_date` ON `deposit_events` (`depositId`, `date`)")

        val today = LocalDate.now()
        val cursor = db.query(
            "SELECT id, accountId, initialAmount, interestRate, startDate, endDate, isCapitalized, capitalizationPeriod, rateTiersJson FROM deposits WHERE isActive = 1"
        )
        cursor.use {
            while (it.moveToNext()) {
                val depositId = it.getLong(0)
                val accountId = it.getLong(1)
                val initialAmount = BigDecimal(it.getString(2))
                val interestRate = BigDecimal(it.getString(3))
                val startDate = LocalDate.parse(it.getString(4))
                val endDate = if (it.isNull(5)) null else LocalDate.parse(it.getString(5))
                val isCapitalized = it.getInt(6) != 0
                val capPeriodStr = if (it.isNull(7)) null else it.getString(7)
                val capitalizationPeriod = capPeriodStr?.let { s -> CapPeriod.valueOf(s) }
                val rateTiersJson = if (it.isNull(8)) null else it.getString(8)
                val rateTiers = parseRateTiers(rateTiersJson)

                val deposit = Deposit(
                    id = depositId, accountId = accountId,
                    initialAmount = initialAmount, interestRate = interestRate,
                    startDate = startDate, endDate = endDate,
                    isCapitalized = isCapitalized, capitalizationPeriod = capitalizationPeriod,
                    payoutAccountId = null, rateTiers = rateTiers,
                )

                insertEvent(db, depositId, startDate.toString(), "PRINCIPAL_ADD", initialAmount.toPlainString())

                val effectiveEnd = minOf<LocalDate>(endDate ?: today, today)
                val eventType = if (isCapitalized) "CAPITALIZATION" else "INTEREST_ACCRUAL"
                var finalBalance = initialAmount

                if (effectiveEnd.isAfter(startDate)) {
                    val slices = DepositCalculator.accrueByPeriod(initialAmount, deposit, startDate, effectiveEnd)
                    for ((date, amount) in slices) {
                        insertEvent(db, depositId, date.toString(), eventType, amount.toPlainString())
                    }
                    val totalInterest = slices.fold(BigDecimal.ZERO) { acc, (_, v) -> acc + v }
                    finalBalance = initialAmount + totalInterest
                }

                db.execSQL(
                    "UPDATE accounts SET balance = ? WHERE id = ?",
                    arrayOf(finalBalance.toPlainString(), accountId),
                )
            }
        }
    }

    private fun insertEvent(db: SupportSQLiteDatabase, depositId: Long, date: String, type: String, amount: String) {
        db.execSQL(
            "INSERT INTO deposit_events (depositId, date, type, amount, note) VALUES (?, ?, ?, ?, '')",
            arrayOf<Any?>(depositId, date, type, amount),
        )
    }

    private fun parseRateTiers(json: String?): List<RateTier> {
        json ?: return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                RateTier(LocalDate.parse(obj.getString("fromDate")), BigDecimal(obj.getString("ratePercent")))
            }
        }.getOrElse { emptyList() }
    }
}
