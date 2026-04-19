package com.moneykeeper.feature.transactions.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moneykeeper.core.domain.model.TransactionType
import com.moneykeeper.feature.transactions.R

@Composable
fun TransactionTypeSelector(
    selected: TransactionType,
    onSelect: (TransactionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TransactionType.entries.forEach { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(type) },
                label = { Text(stringResource(transactionTypeRes(type))) },
            )
        }
    }
}

internal fun transactionTypeRes(type: TransactionType): Int = when (type) {
    TransactionType.INCOME -> R.string.tx_type_income
    TransactionType.EXPENSE -> R.string.tx_type_expense
    TransactionType.TRANSFER -> R.string.tx_type_transfer
    TransactionType.SAVINGS -> R.string.tx_type_savings
}
