package com.moneykeeper.feature.dashboard.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moneykeeper.core.ui.util.formatAsCurrency
import com.moneykeeper.feature.dashboard.R
import com.moneykeeper.feature.dashboard.ui.DepositWithDaysLeft
import java.time.format.DateTimeFormatter

private val DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yy")

@Composable
fun ExpiringDepositsWidget(
    deposits: List<DepositWithDaysLeft>,
    onDepositClick: (Long) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.dashboard_expiring_deposits),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            deposits.forEach { item ->
                ListItem(
                    headlineContent = { Text(item.accountName) },
                    supportingContent = {
                        val daysText = pluralStringResource(
                            R.plurals.days_left,
                            item.daysLeft,
                            item.daysLeft,
                        )
                        Text("$daysText · ${item.projectedAmount.formatAsCurrency()}")
                    },
                    trailingContent = {
                        Text(item.deposit.endDate?.format(DATE_FMT) ?: "")
                    },
                    modifier = Modifier.clickable { onDepositClick(item.deposit.accountId) },
                )
            }
        }
    }
}
