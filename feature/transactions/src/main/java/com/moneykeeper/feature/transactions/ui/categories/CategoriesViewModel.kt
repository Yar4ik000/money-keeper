package com.moneykeeper.feature.transactions.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.core.domain.model.Category
import com.moneykeeper.core.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepo: CategoryRepository,
) : ViewModel() {

    val categories: StateFlow<List<Category>> = categoryRepo.observeAll()
        .map { all ->
            val byParent = all.filter { it.parentCategoryId != null }.groupBy { it.parentCategoryId!! }
            all.filter { it.parentCategoryId == null }
                .sortedBy { it.sortOrder }
                .flatMap { root -> listOf(root) + byParent[root.id].orEmpty().sortedBy { it.sortOrder } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteCategory(id: Long) = viewModelScope.launch {
        categoryRepo.delete(id)
    }

    fun deleteCategories(ids: Set<Long>) = viewModelScope.launch {
        ids.forEach { categoryRepo.delete(it) }
    }
}
