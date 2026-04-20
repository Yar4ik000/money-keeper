package com.moneykeeper.feature.forecast.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moneykeeper.core.domain.forecast.TimelineEvent
import com.moneykeeper.core.ui.util.formatAsCurrency
import java.math.BigDecimal
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru"))
private val dayFormatter = DateTimeFormatter.ofPattern("d MMM", Locale("ru"))
private val IncomeColor = Color(0xFF4CAF50)

fun LazyListScope.eventTimeline(events: List<TimelineEvent>, currency: String) {
    val grouped = events.groupBy { YearMonth.from(it.date) }.toSortedMap()
    grouped.forEach { (month, monthEvents) ->
        item(key = "header_$month") {
            Text(
                text = month.format(monthFormatter).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        items(monthEvents, key = { "${it.date}_${it.description}_${it.amountDelta}" }) { event ->
            TimelineEventItem(event = event, currency = currency)
        }
        item(key = "divider_$month") {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
fun TimelineEventItem(event: TimelineEvent, currency: String) {
    val (icon, color) = when (event) {
        is TimelineEvent.DepositMaturity -> Icons.Default.AccountBalance to MaterialTheme.colorScheme.primary
        is TimelineEvent.RecurringIncome -> Icons.Default.TrendingUp to IncomeColor
        is TimelineEvent.RecurringExpense -> Icons.Default.TrendingDown to MaterialTheme.colorScheme.error
    }
    val sign = if (event.amountDelta >= BigDecimal.ZERO) "+" else ""
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null, tint = color) },
        headlineContent = { Text(event.description) },
        supportingContent = { Text(event.date.format(dayFormatter)) },
        trailingContent = {
            Text(
                text = sign + event.amountDelta.abs().formatAsCurrency(currency),
                color = if (event.amountDelta >= BigDecimal.ZERO) IncomeColor
                        else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
    )
}
