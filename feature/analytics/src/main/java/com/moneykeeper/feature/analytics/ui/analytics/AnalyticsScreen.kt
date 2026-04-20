package com.moneykeeper.feature.analytics.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneykeeper.core.ui.util.formatAsCurrency
import com.moneykeeper.core.ui.util.parseHexColor
import com.moneykeeper.feature.analytics.R
import com.moneykeeper.feature.analytics.ui.components.ChartIncomeColor
import com.moneykeeper.feature.analytics.ui.components.ExpensesPieChart
import com.moneykeeper.feature.analytics.ui.components.MonthlyBarChart
import com.moneykeeper.feature.analytics.ui.components.PeriodSelector
import java.math.BigDecimal
import java.time.YearMonth

private enum class BreakdownMode { CATEGORIES, ACCOUNTS }

@Composable
fun AnalyticsRoute(
    onCategoryClick: (Long) -> Unit,
    onSeeAllTransactions: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AnalyticsScreen(
        uiState = uiState,
        onPrevPeriod = viewModel::prevPeriod,
        onNextPeriod = viewModel::nextPeriod,
        onJumpPeriod = viewModel::jumpToPeriod,
        onCurrencySelect = viewModel::selectCurrency,
        onCategoryClick = onCategoryClick,
        onSeeAllTransactions = onSeeAllTransactions,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalyticsScreen(
    uiState: AnalyticsUiState,
    onPrevPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onJumpPeriod: (YearMonth) -> Unit,
    onCurrencySelect: (String) -> Unit,
    onCategoryClick: (Long) -> Unit,
    onSeeAllTransactions: () -> Unit,
) {
    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var breakdownMode by remember { mutableStateOf(BreakdownMode.CATEGORIES) }
    val expenseColor = MaterialTheme.colorScheme.error

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            PeriodSelector(
                period = uiState.period,
                onPrev = onPrevPeriod,
                onNext = onNextPeriod,
                onJump = onJumpPeriod,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        if (uiState.availableCurrencies.size > 1) {
            item {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    uiState.availableCurrencies.forEach { currency ->
                        FilterChip(
                            selected = currency == uiState.selectedCurrency,
                            onClick = { onCurrencySelect(currency) },
                            label = { Text(currency) },
                        )
                    }
                }
            }
        }

        // Monthly trend chart
        if (uiState.monthlyTrend.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.analytics_monthly_trend),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                MonthlyBarChart(
                    data = uiState.monthlyTrend,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    LegendItem(color = ChartIncomeColor, label = stringResource(R.string.analytics_legend_income))
                    Spacer(Modifier.width(24.dp))
                    LegendItem(color = expenseColor, label = stringResource(R.string.analytics_legend_expense))
                }
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
        }

        // Grouping mode toggle
        if (uiState.periodHasTransactions) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = breakdownMode == BreakdownMode.CATEGORIES,
                        onClick = { breakdownMode = BreakdownMode.CATEGORIES },
                        label = { Text(stringResource(R.string.analytics_group_by_category)) },
                    )
                    FilterChip(
                        selected = breakdownMode == BreakdownMode.ACCOUNTS,
                        onClick = { breakdownMode = BreakdownMode.ACCOUNTS },
                        label = { Text(stringResource(R.string.analytics_group_by_account)) },
                    )
                }
            }
        }

        if (breakdownMode == BreakdownMode.CATEGORIES) {
            // Expenses by category
            if (uiState.categoryExpenses.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.analytics_top_expenses),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ExpensesPieChart(
                            data = uiState.categoryExpenses,
                            modifier = Modifier.size(180.dp),
                        )
                    }
                }
                items(uiState.categoryExpenses) { expense ->
                    CategoryExpenseItem(
                        expense = expense,
                        currency = uiState.selectedCurrency,
                        onClick = { onCategoryClick(expense.category.id) },
                    )
                }
                if (uiState.categoryExpenses.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(
                                R.string.analytics_daily_avg,
                                uiState.averageDailyExpense.formatAsCurrency(uiState.selectedCurrency),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
            }

            // Income by category
            if (uiState.incomeCategoryExpenses.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.analytics_top_incomes),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ExpensesPieChart(
                            data = uiState.incomeCategoryExpenses,
                            modifier = Modifier.size(180.dp),
                        )
                    }
                }
                items(uiState.incomeCategoryExpenses) { income ->
                    CategoryExpenseItem(
                        expense = income,
                        currency = uiState.selectedCurrency,
                        onClick = { onCategoryClick(income.category.id) },
                    )
                }
            }
        } else {
            // Account breakdown — expenses
            if (uiState.expensesByAccount.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.analytics_top_expenses),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                items(uiState.expensesByAccount) { breakdown ->
                    AccountBreakdownItem(
                        breakdown = breakdown,
                        currency = uiState.selectedCurrency,
                        barColor = expenseColor,
                    )
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
            }

            // Account breakdown — income
            if (uiState.incomeByAccount.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.analytics_top_incomes),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                items(uiState.incomeByAccount) { breakdown ->
                    AccountBreakdownItem(
                        breakdown = breakdown,
                        currency = uiState.selectedCurrency,
                        barColor = ChartIncomeColor,
                    )
                }
            }
        }

        if (!uiState.periodHasTransactions) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.analytics_no_data))
                }
            }
        }

        item {
            TextButton(
                onClick = onSeeAllTransactions,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text(stringResource(R.string.analytics_see_all_transactions))
            }
        }
    }
}

@Composable
private fun AccountBreakdownItem(
    breakdown: AccountBreakdown,
    currency: String,
    barColor: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = breakdown.accountName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = breakdown.total.formatAsCurrency(currency),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LinearProgressIndicator(
                progress = { breakdown.percentage / 100f },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                text = "${breakdown.transactionCount} · ${String.format("%.0f", breakdown.percentage)}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CategoryExpenseItem(
    expense: CategoryExpense,
    currency: String,
    onClick: () -> Unit,
) {
    val color = parseHexColor(expense.category.colorHex)
    ListItem(
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = expense.category.name.take(1).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                )
            }
        },
        headlineContent = { Text(expense.category.name) },
        supportingContent = { Text("${expense.transactionCount} операций · ${String.format("%.0f", expense.percentage)}%") },
        trailingContent = {
            Text(
                text = expense.total.formatAsCurrency(currency),
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
