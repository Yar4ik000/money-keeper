package com.moneykeeper.feature.analytics.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneykeeper.core.domain.model.TransactionType
import com.moneykeeper.core.domain.model.TransactionWithMeta
import com.moneykeeper.core.domain.money.CurrencyAmount
import com.moneykeeper.core.ui.util.formatAsCurrency
import com.moneykeeper.core.ui.util.parseHexColor
import com.moneykeeper.feature.analytics.ui.history.TransactionGroup
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TransactionGroupHeader(group: TransactionGroup) {
    val dateFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"))
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = group.date.format(dateFormatter),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            group.dayTotals.forEach { ca ->
                DayTotalChip(ca)
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}

@Composable
private fun DayTotalChip(ca: CurrencyAmount) {
    val color = when {
        ca.amount > java.math.BigDecimal.ZERO -> MaterialTheme.colorScheme.primary
        ca.amount < java.math.BigDecimal.ZERO -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    val sign = if (ca.amount >= java.math.BigDecimal.ZERO) "+" else ""
    Text(
        text = "$sign${ca.amount.formatAsCurrency(ca.currency)}",
        style = MaterialTheme.typography.labelMedium,
        color = color,
        fontWeight = FontWeight.Medium,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionHistoryItem(
    meta: TransactionWithMeta,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val accentColor = parseHexColor(meta.categoryColor)
    val (amountColor, amountSign) = when (meta.transaction.type) {
        TransactionType.INCOME   -> MaterialTheme.colorScheme.primary to "+"
        TransactionType.EXPENSE,
        TransactionType.SAVINGS  -> MaterialTheme.colorScheme.error to "-"
        TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurface to "→"
    }

    ListItem(
        leadingContent = {
            if (isSelectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = null)
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = meta.categoryName.take(1).uppercase().ifEmpty { "?" },
                        style = MaterialTheme.typography.labelLarge,
                        color = accentColor,
                    )
                }
            }
        },
        headlineContent = {
            Text(meta.categoryName.ifEmpty { "Прочее" })
        },
        supportingContent = {
            val parts = listOfNotNull(
                meta.accountName.takeIf { it.isNotEmpty() },
                meta.transaction.note.takeIf { it.isNotEmpty() },
            )
            if (parts.isNotEmpty()) Text(parts.joinToString(" · "))
        },
        trailingContent = {
            Text(
                text = "$amountSign${meta.transaction.amount.formatAsCurrency(meta.accountCurrency)}",
                color = amountColor,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        modifier = Modifier
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surface
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}
