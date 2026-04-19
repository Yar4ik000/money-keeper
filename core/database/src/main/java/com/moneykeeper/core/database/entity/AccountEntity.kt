package com.moneykeeper.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.model.AccountType
import java.math.BigDecimal
import java.time.LocalDate

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val currency: String,
    val colorHex: String,
    val iconName: String,
    val balance: BigDecimal,
    val isArchived: Boolean = false,
    val createdAt: LocalDate,
    val sortOrder: Int = 0,
)

fun AccountEntity.toDomain() = Account(
    id = id, name = name, type = type, currency = currency,
    colorHex = colorHex, iconName = iconName, balance = balance,
    isArchived = isArchived, createdAt = createdAt, sortOrder = sortOrder,
)

fun Account.toEntity() = AccountEntity(
    id = id, name = name, type = type, currency = currency,
    colorHex = colorHex, iconName = iconName, balance = balance,
    isArchived = isArchived, createdAt = createdAt, sortOrder = sortOrder,
)
