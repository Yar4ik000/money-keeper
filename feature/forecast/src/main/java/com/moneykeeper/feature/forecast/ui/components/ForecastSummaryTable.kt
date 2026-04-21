package com.moneykeeper.feature.forecast.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneykeeper.core.domain.forecast.AccountForecast
import com.moneykeeper.core.ui.util.accountIconVector
import com.moneykeeper.core.ui.util.formatAsCurrency
import com.moneykeeper.core.ui.util.parseHexColor
import com.moneykeeper.feature.forecast.R
import java.math.BigDecimal

private val IncomeColor = Color(0xFF4CAF50)

@Composable
fun ForecastSummaryTable(
    forecasts: List<AccountForecast>,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.forecast_by_account),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            forecasts.forEach { item ->
                val accentColor = parseHexColor(item.account.colorHex)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(accentColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = accountIconVector(item.account.iconName),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = item.account.name,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = item.currentBalance.formatAsCurrency(item.account.currency),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = " → ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = item.forecastedBalance.formatAsCurrency(item.account.currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (item.delta >= BigDecimal.ZERO) IncomeColor
                                else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
fun ForecastCurrencyTotals(
    currency: String,
    currentBalance: BigDecimal,
    forecastedBalance: BigDecimal,
    delta: BigDecimal,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.forecast_now),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        currentBalance.formatAsCurrency(currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.forecast_at_date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        forecastedBalance.formatAsCurrency(currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (delta >= BigDecimal.ZERO) IncomeColor
                                else MaterialTheme.colorScheme.error,
                    )
                }
            }
            val isGain = delta >= BigDecimal.ZERO
            val labelRes = if (isGain) R.string.forecast_delta_gain else R.string.forecast_delta_loss
            val amountText = if (isGain) "+" + delta.formatAsCurrency(currency)
                             else delta.abs().formatAsCurrency(currency)
            Text(
                text = stringResource(labelRes, amountText),
                style = MaterialTheme.typography.bodySmall,
                color = if (isGain) IncomeColor else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
