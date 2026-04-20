package com.moneykeeper.feature.transactions.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moneykeeper.core.domain.model.Category
import com.moneykeeper.core.domain.model.CategoryType
import com.moneykeeper.core.domain.model.TransactionType
import com.moneykeeper.core.ui.util.categoryIconVector
import com.moneykeeper.core.ui.util.parseHexColor
import com.moneykeeper.feature.transactions.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPicker(
    categories: List<Category>,
    transactionType: TransactionType,
    selectedId: Long?,
    onSelect: (Category) -> Unit,
    onDismiss: () -> Unit,
    onAddCategory: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val categoryType = transactionType.toCategoryType()
    val filtered = categories.filter { it.type == categoryType }
    val roots = filtered.filter { it.parentCategoryId == null }
    val childrenByParent = filtered
        .filter { it.parentCategoryId != null }
        .groupBy { it.parentCategoryId }
    var expandedId by remember { mutableStateOf<Long?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.category_picker_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onAddCategory) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
        HorizontalDivider()
        LazyColumn {
            roots.forEach { root ->
                val children = childrenByParent[root.id].orEmpty()
                item(key = root.id) {
                    CategoryRow(
                        category = root,
                        isSelected = selectedId == root.id,
                        hasChildren = children.isNotEmpty(),
                        isExpanded = expandedId == root.id,
                        indented = false,
                        onClick = { onSelect(root); onDismiss() },
                        onToggle = {
                            expandedId = if (expandedId == root.id) null else root.id
                        },
                    )
                }
                if (expandedId == root.id) {
                    items(children, key = { it.id }) { child ->
                        CategoryRow(
                            category = child,
                            isSelected = selectedId == child.id,
                            hasChildren = false,
                            isExpanded = false,
                            indented = true,
                            onClick = { onSelect(child); onDismiss() },
                            onToggle = {},
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    isSelected: Boolean,
    hasChildren: Boolean,
    isExpanded: Boolean,
    indented: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(
                start = if (indented) 40.dp else 16.dp,
                end = 8.dp,
                top = 12.dp,
                bottom = 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(parseHexColor(category.colorHex)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = categoryIconVector(category.iconName),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (hasChildren) {
            IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                )
            }
        }
    }
}

internal fun parseCategoryColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    Color(0xFF9E9E9E)
}

private fun TransactionType.toCategoryType(): CategoryType = when (this) {
    TransactionType.INCOME -> CategoryType.INCOME
    TransactionType.EXPENSE,
    TransactionType.SAVINGS -> CategoryType.EXPENSE
    TransactionType.TRANSFER -> CategoryType.TRANSFER
}
