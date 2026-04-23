package com.moneykeeper.feature.settings.ui.recurring

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneykeeper.core.domain.forecast.advance
import com.moneykeeper.core.domain.model.Frequency
import com.moneykeeper.core.domain.model.RecurringRuleWithTemplate
import com.moneykeeper.core.ui.locale.AppLocale
import com.moneykeeper.feature.settings.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecurringRulesScreen(
    onBack: () -> Unit,
    onRuleClick: (Long) -> Unit,
    viewModel: RecurringRulesViewModel = hiltViewModel(),
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val isSelectionMode = selectedIds.isNotEmpty()
    var showStopConfirm by remember { mutableStateOf(false) }

    BackHandler(enabled = isSelectionMode) { viewModel.clearSelection() }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text(selectedIds.size.toString()) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showStopConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.recurring_rules_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                )
            }
        },
    ) { padding ->
        if (rules.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.recurring_rules_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(rules, key = { it.rule.id }) { item ->
                    RecurringRuleItem(
                        item = item,
                        isSelected = item.rule.id in selectedIds,
                        isSelectionMode = isSelectionMode,
                        onClick = {
                            if (isSelectionMode) viewModel.toggleSelection(item.rule.id)
                            else onRuleClick(item.rule.id)
                        },
                        onLongClick = {
                            if (!isSelectionMode) viewModel.enterSelectionMode(item.rule.id)
                            else viewModel.toggleSelection(item.rule.id)
                        },
                    )
                }
            }
        }
    }

    if (showStopConfirm) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            title = { Text(stringResource(R.string.recurring_rules_stop_title)) },
            text = { Text(stringResource(R.string.recurring_rules_stop_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showStopConfirm = false
                        viewModel.stopSelected()
                    },
                ) {
                    Text(
                        stringResource(R.string.recurring_rules_stop_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecurringRuleItem(
    item: RecurringRuleWithTemplate,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val nextDate = remember(item.rule) {
        val base = item.rule.lastGeneratedDate ?: item.rule.startDate
        val candidate = base.advance(item.rule.frequency, item.rule.interval)
        when {
            item.rule.endDate != null && candidate > item.rule.endDate -> null
            candidate >= today -> candidate
            else -> {
                var d = candidate
                while (d < today && (item.rule.endDate == null || d <= item.rule.endDate)) {
                    d = d.advance(item.rule.frequency, item.rule.interval)
                }
                if (item.rule.endDate != null && d > item.rule.endDate) null else d
            }
        }
    }

    val freqLabel = frequencyLabel(item.rule.frequency, item.rule.interval)
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy").withLocale(AppLocale.current()) }
    val subtitle = buildString {
        append(freqLabel)
        if (item.categoryName.isNotEmpty()) append(" · ${item.categoryName}")
        append(" · ${item.accountName}")
    }
    val nextLabel = if (nextDate != null)
        "→ ${nextDate.format(dateFormatter)}"
    else
        null

    ListItem(
        leadingContent = {
            if (isSelectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = null)
            } else {
                Icon(Icons.Outlined.Repeat, contentDescription = null)
            }
        },
        headlineContent = { Text(item.description) },
        supportingContent = {
            Column {
                Text(subtitle)
                if (nextLabel != null) {
                    Text(
                        text = nextLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.recurring_rules_ended),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}

@Composable
private fun frequencyLabel(frequency: Frequency, interval: Int): String = when (frequency) {
    Frequency.DAILY   -> if (interval == 1) stringResource(R.string.recurring_freq_daily)
                         else stringResource(R.string.recurring_freq_daily_n, interval)
    Frequency.WEEKLY  -> if (interval == 1) stringResource(R.string.recurring_freq_weekly)
                         else stringResource(R.string.recurring_freq_weekly_n, interval)
    Frequency.MONTHLY -> if (interval == 1) stringResource(R.string.recurring_freq_monthly)
                         else stringResource(R.string.recurring_freq_monthly_n, interval)
    Frequency.YEARLY  -> if (interval == 1) stringResource(R.string.recurring_freq_yearly)
                         else stringResource(R.string.recurring_freq_yearly_n, interval)
}
