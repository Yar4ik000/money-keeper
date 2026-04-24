package com.moneykeeper.feature.dashboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneykeeper.core.domain.model.TransactionType
import com.moneykeeper.core.domain.model.TransactionWithMeta
import com.moneykeeper.core.ui.util.categoryIconVector
import com.moneykeeper.core.ui.util.formatAsCurrency
import com.moneykeeper.core.ui.util.parseHexColor
import com.moneykeeper.feature.dashboard.R

@Composable
fun RecentTransactionsHeader(onSeeAllClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.dashboard_recent_transactions),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onSeeAllClick) {
            Text(stringResource(R.string.dashboard_see_all))
        }
    }
}

@Composable
fun TransactionListItem(meta: TransactionWithMeta, onClick: () -> Unit) {
    val accentColor = parseHexColor(meta.categoryColor)
    val (amountColor, amountSign) = when (meta.transaction.type) {
        TransactionType.INCOME   -> MaterialTheme.colorScheme.primary to "+"
        TransactionType.EXPENSE,
        TransactionType.SAVINGS  -> MaterialTheme.colorScheme.error to "-"
        TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurface to ""
    }

    ListItem(
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accentColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = categoryIconVector(meta.categoryIcon),
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        headlineContent = {
            Text(meta.categoryName.ifEmpty { stringResource(R.string.dashboard_category_none) })
        },
        supportingContent = {
            if (meta.transaction.type == TransactionType.TRANSFER && meta.toAccountName != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(meta.accountName, style = MaterialTheme.typography.bodySmall)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(12.dp))
                    Text(
                        buildString {
                            append(meta.toAccountName)
                            if (meta.transaction.note.isNotEmpty()) append(" · ${meta.transaction.note}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                val parts = listOfNotNull(
                    meta.accountName.takeIf { it.isNotEmpty() },
                    meta.transaction.note.takeIf { it.isNotEmpty() },
                )
                if (parts.isNotEmpty()) Text(parts.joinToString(" · "))
            }
        },
        trailingContent = {
            Text(
                text = "$amountSign${meta.transaction.amount.formatAsCurrency(meta.accountCurrency)}",
                color = amountColor,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
