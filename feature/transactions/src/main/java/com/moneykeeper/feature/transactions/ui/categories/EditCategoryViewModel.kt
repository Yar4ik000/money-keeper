package com.moneykeeper.feature.transactions.ui.categories

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.core.domain.model.Category
import com.moneykeeper.core.domain.model.CategoryType
import com.moneykeeper.core.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditCategoryViewModel @Inject constructor(
    private val categoryRepo: CategoryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val categoryId: Long? =
        savedStateHandle.get<Long>("categoryId")?.takeIf { it != -1L }

    private val _uiState = MutableStateFlow(EditCategoryUiState())
    val uiState: StateFlow<EditCategoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            categoryRepo.observeAll().collect { allCategories ->
                // On first emission, load existing category if editing
                if (categoryId != null && _uiState.value.name.isEmpty()) {
                    val existing = categoryRepo.getById(categoryId)
                    if (existing != null) {
                        _uiState.update {
                            it.copy(
                                name = existing.name,
                                type = existing.type,
                                colorHex = existing.colorHex,
                                iconName = existing.iconName,
                                parentCategoryId = existing.parentCategoryId,
                            )
                        }
                    }
                }
                val currentType = _uiState.value.type
                _uiState.update {
                    it.copy(
                        availableParents = allCategories.filter { c ->
                            c.type == currentType
                                && categoryDepth(c, allCategories) < 2
                                && c.id != categoryId
                                && (categoryId == null || c.parentCategoryId != categoryId)
                        },
                    )
                }
            }
        }
    }

    fun onNameChange(name: String) =
        _uiState.update { it.copy(name = name, error = null) }

    fun onTypeChange(type: CategoryType) =
        _uiState.update { it.copy(type = type, parentCategoryId = null) }

    fun onColorChange(hex: String) =
        _uiState.update { it.copy(colorHex = hex) }

    fun onIconChange(icon: String) =
        _uiState.update { it.copy(iconName = icon) }

    fun onParentChange(parentId: Long?) =
        _uiState.update { it.copy(parentCategoryId = parentId) }

    private fun categoryDepth(category: Category, all: List<Category>): Int {
        var depth = 0
        var parentId = category.parentCategoryId
        while (parentId != null) {
            depth++
            parentId = all.find { it.id == parentId }?.parentCategoryId
        }
        return depth
    }

    fun onSave() = viewModelScope.launch {
        val s = _uiState.value
        if (s.name.isBlank()) {
            _uiState.update { it.copy(error = EditCategoryError.NameEmpty) }
            return@launch
        }
        val id = categoryRepo.save(
            Category(
                id = categoryId ?: 0L,
                name = s.name.trim(),
                type = s.type,
                colorHex = s.colorHex,
                iconName = s.iconName,
                parentCategoryId = s.parentCategoryId,
            )
        )
        _uiState.update { it.copy(saved = true, savedCategoryId = id) }
    }
}
