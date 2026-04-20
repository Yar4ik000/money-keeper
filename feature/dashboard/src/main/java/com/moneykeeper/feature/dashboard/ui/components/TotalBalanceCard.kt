package com.moneykeeper.feature.dashboard.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneykeeper.core.domain.money.CurrencyAmount
import com.moneykeeper.core.domain.money.MultiCurrencyTotal
import com.moneykeeper.core.ui.util.formatAsCurrency
import com.moneykeeper.feature.dashboard.R
import java.math.BigDecimal

@Composable
fun TotalBalanceCard(
    totals: MultiCurrencyTotal,
    defaultCurrency: String = "RUB",
) {
    val rows = if (totals.entries.isEmpty())
        listOf(CurrencyAmount.zero(defaultCurrency))
    else
        totals.entries.sortedWith(compareByDescending<CurrencyAmount> {
            it.currency == defaultCurrency
        }.thenBy { it.currency })

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.dashboard_total_balance),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            rows.forEachIndexed { index, row ->
                Text(
                    text = row.amount.formatAsCurrency(row.currency),
                    style = if (index == 0) MaterialTheme.typography.headlineLarge
                    else MaterialTheme.typography.titleMedium,
                    fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Medium,
                    color = if (row.amount >= BigDecimal.ZERO)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
