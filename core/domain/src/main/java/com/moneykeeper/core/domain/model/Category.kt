package com.moneykeeper.core.domain.model

data class Category(
    val id: Long = 0,
    val name: String,
    val type: CategoryType,
    val colorHex: String,
    val iconName: String,
    val parentCategoryId: Long? = null,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
)

enum class CategoryType { INCOME, EXPENSE, TRANSFER }
