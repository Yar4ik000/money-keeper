package com.moneykeeper.feature.transactions.ui.categories

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import com.moneykeeper.core.ui.components.DeleteConfirmDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneykeeper.core.domain.model.Category
import com.moneykeeper.core.ui.util.categoryIconVector
import com.moneykeeper.core.ui.util.parseHexColor
import com.moneykeeper.feature.transactions.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel = hiltViewModel(),
    onAddCategory: () -> Unit,
    onEditCategory: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val isSelectionMode = selectedIds.isNotEmpty()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    BackHandler(enabled = isSelectionMode) { selectedIds = emptySet() }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.categories_selected_count, selectedIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.categories_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = onAddCategory) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.categories_add))
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (categories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.categories_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(categories, key = { (cat, _) -> cat.id }) { (category, depth) ->
                    CategoryItem(
                        category = category,
                        depth = depth,
                        isSelectionMode = isSelectionMode,
                        isSelected = category.id in selectedIds,
                        onEdit = { onEditCategory(category.id) },
                        onClick = {
                            if (isSelectionMode) {
                                selectedIds = if (category.id in selectedIds)
                                    selectedIds - category.id
                                else
                                    selectedIds + category.id
                            }
                        },
                        onLongPress = {
                            selectedIds = selectedIds + category.id
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            title = stringResource(R.string.categories_delete_selected_title),
            body = stringResource(R.string.categories_delete_selected_message, selectedIds.size),
            confirmLabel = stringResource(R.string.categories_delete_confirm),
            cancelLabel = stringResource(R.string.dialog_cancel),
            onConfirm = { viewModel.deleteCategories(selectedIds); selectedIds = emptySet(); showDeleteConfirm = false },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryItem(
    category: Category,
    depth: Int,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onEdit: () -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val iconCircleSize = when (depth) { 0 -> 36.dp; 1 -> 28.dp; else -> 22.dp }
    val iconSize = when (depth) { 0 -> 20.dp; 1 -> 16.dp; else -> 12.dp }
    val textStyle = when (depth) {
        0 -> MaterialTheme.typography.bodyLarge
        1 -> MaterialTheme.typography.bodyMedium
        else -> MaterialTheme.typography.bodySmall
    }
    val textColor = if (depth == 0) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    depth > 0  -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (depth == 1) 0.4f else 0.6f)
                    else       -> MaterialTheme.colorScheme.surface
                }
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            )
            .padding(
                start = (16 + depth * 24).dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (depth > 0) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(iconCircleSize)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
                    ),
            )
            Spacer(Modifier.width(8.dp))
        }
        Box(
            modifier = Modifier
                .size(iconCircleSize)
                .clip(CircleShape)
                .background(parseHexColor(category.colorHex)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = categoryIconVector(category.iconName),
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(iconSize),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = category.name,
                style = textStyle,
                color = textColor,
            )
        }
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
            )
        } else {
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = null)
            }
        }
    }
}
