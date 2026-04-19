package com.moneykeeper.core.database.converter

import androidx.room.TypeConverter
import com.moneykeeper.core.domain.model.AccountType
import com.moneykeeper.core.domain.model.BudgetPeriod
import com.moneykeeper.core.domain.model.CapPeriod
import com.moneykeeper.core.domain.model.CategoryType
import com.moneykeeper.core.domain.model.Frequency
import com.moneykeeper.core.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class Converters {

    // LocalDate <-> ISO-строка "2026-04-19"
    @TypeConverter fun fromLocalDate(d: LocalDate?): String? = d?.toString()
    @TypeConverter fun toLocalDate(s: String?): LocalDate? = s?.let(LocalDate::parse)

    // LocalDateTime <-> ISO-строка "2026-04-19T10:00:00"
    @TypeConverter fun fromLocalDateTime(d: LocalDateTime?): String? = d?.toString()
    @TypeConverter fun toLocalDateTime(s: String?): LocalDateTime? = s?.let(LocalDateTime::parse)

    // BigDecimal <-> строка без потери точности (toPlainString убирает научную нотацию "1.5E+2")
    @TypeConverter fun fromBigDecimal(v: BigDecimal?): String? = v?.toPlainString()
    @TypeConverter fun toBigDecimal(s: String?): BigDecimal? = s?.let { BigDecimal(it) }

    @TypeConverter fun fromAccountType(v: AccountType): String = v.name
    @TypeConverter fun toAccountType(s: String): AccountType = AccountType.valueOf(s)

    @TypeConverter fun fromCapPeriod(v: CapPeriod?): String? = v?.name
    @TypeConverter fun toCapPeriod(s: String?): CapPeriod? = s?.let { CapPeriod.valueOf(it) }

    @TypeConverter fun fromCategoryType(v: CategoryType): String = v.name
    @TypeConverter fun toCategoryType(s: String): CategoryType = CategoryType.valueOf(s)

    @TypeConverter fun fromTransactionType(v: TransactionType): String = v.name
    @TypeConverter fun toTransactionType(s: String): TransactionType = TransactionType.valueOf(s)

    @TypeConverter fun fromFrequency(v: Frequency): String = v.name
    @TypeConverter fun toFrequency(s: String): Frequency = Frequency.valueOf(s)

    @TypeConverter fun fromBudgetPeriod(v: BudgetPeriod): String = v.name
    @TypeConverter fun toBudgetPeriod(s: String): BudgetPeriod = BudgetPeriod.valueOf(s)
}
