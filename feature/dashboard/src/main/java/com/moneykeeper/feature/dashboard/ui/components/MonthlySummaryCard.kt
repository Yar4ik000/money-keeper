package com.moneykeeper.feature.dashboard.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moneykeeper.core.domain.analytics.PeriodSummaryByCurrency
import com.moneykeeper.core.ui.locale.AppLocale
import com.moneykeeper.core.ui.util.formatAsCurrency
import com.moneykeeper.feature.dashboard.R
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.TextStyle

private val IncomeBarColor = Color(0xFF4CAF50)

@Composable
fun MonthlySummaryCard(summary: List<PeriodSummaryByCurrency>) {
    val expenseBarColor = MaterialTheme.colorScheme.error
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = currentMonthName(),
                style = MaterialTheme.typography.titleMedium,
            )
            if (summary.isEmpty()) {
                Text(
                    text = stringResource(R.string.dashboard_month_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                return@Column
            }
            summary.forEachIndexed { idx, row ->
                if (idx > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                if (summary.size > 1) {
                    Text(
                        row.currency,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    SummaryItem(
                        label = stringResource(R.string.dashboard_income),
                        amount = row.income,
                        currency = row.currency,
                        positive = true,
                        modifier = Modifier.weight(1f),
                    )
                    SummaryItem(
                        label = stringResource(R.string.dashboard_expense),
                        amount = row.expense,
                        currency = row.currency,
                        positive = false,
                        modifier = Modifier.weight(1f),
                    )
                }
                val total = row.income + row.expense
                if (total > BigDecimal.ZERO) {
                    val incomeFraction = (row.income.toDouble() / total.toDouble())
                        .toFloat().coerceIn(0f, 1f)
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    ) {
                        val incomeWidth = size.width * incomeFraction
                        drawRect(IncomeBarColor, size = Size(incomeWidth, size.height))
                        drawRect(
                            expenseBarColor,
                            topLeft = Offset(incomeWidth, 0f),
                            size = Size(size.width - incomeWidth, size.height),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryItem(
    label: String,
    amount: BigDecimal,
    currency: String,
    positive: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(
            text = amount.formatAsCurrency(currency),
            style = MaterialTheme.typography.titleMedium,
            color = if (positive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
        )
    }
}

private fun currentMonthName(): String {
    val locale = AppLocale.current()
    return LocalDate.now().month
        .getDisplayName(TextStyle.FULL_STANDALONE, locale)
        .replaceFirstChar { it.uppercase(locale) }
}
