package com.moneykeeper.feature.transactions.ui.categories

import com.moneykeeper.core.domain.model.Category
import com.moneykeeper.core.domain.model.CategoryType

data class EditCategoryUiState(
    val name: String = "",
    val type: CategoryType = CategoryType.EXPENSE,
    val colorHex: String = "#9E9E9E",
    val iconName: String = "Category",
    val parentCategoryId: Long? = null,
    val availableParents: List<Category> = emptyList(),
    val saved: Boolean = false,
    val savedCategoryId: Long? = null,
    val error: EditCategoryError? = null,
)

sealed interface EditCategoryError {
    data object NameEmpty : EditCategoryError
}
