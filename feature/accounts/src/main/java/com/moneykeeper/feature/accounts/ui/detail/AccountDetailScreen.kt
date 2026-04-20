package com.moneykeeper.feature.accounts.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.model.AccountType
import com.moneykeeper.core.domain.model.Deposit
import com.moneykeeper.core.domain.model.TransactionType
import com.moneykeeper.core.domain.model.TransactionWithMeta
import com.moneykeeper.core.ui.locale.AppLocale
import com.moneykeeper.feature.accounts.R
import com.moneykeeper.core.domain.calculator.DepositCalculator
import com.moneykeeper.feature.accounts.ui.list.formatAmount
import com.moneykeeper.feature.accounts.ui.list.parseColor
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    accountId: Long,
    viewModel: AccountDetailViewModel = hiltViewModel(),
    onEditClick: () -> Unit,
    onTransferClick: () -> Unit,
    onBack: () -> Unit,
) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val deposit by viewModel.deposit.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(account?.name ?: stringResource(R.string.account_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (account?.type != AccountType.DEPOSIT) {
                        IconButton(onClick = onTransferClick) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = stringResource(R.string.account_detail_transfer))
                        }
                    }
                    IconButton(onClick = onEditClick) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.account_detail_edit))
                    }
                },
            )
        },
    ) { padding ->
        val acc = account
        if (acc == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item { AccountSummaryCard(account = acc, deposit = deposit) }
            item {
                Text(
                    stringResource(R.string.account_detail_transactions),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (transactions.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.account_detail_no_transactions),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(transactions, key = { it.transaction.id }) { txMeta ->
                    TransactionRow(txMeta = txMeta, currency = acc.currency)
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun AccountSummaryCard(account: Account, deposit: Deposit?) {
    val today = remember { LocalDate.now() }
    Card(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = parseColor(account.colorHex).copy(alpha = 0.15f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(account.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                account.balance.formatAmount(account.currency),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (account.balance < BigDecimal.ZERO)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurface,
            )

            if (account.type == AccountType.DEPOSIT && deposit != null) {
                Spacer(Modifier.height(8.dp))
                val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy").withLocale(AppLocale.current()) }
                val projected = DepositCalculator.projectedBalance(deposit, today)
                val interest = DepositCalculator.simpleInterest(
                    deposit.initialAmount, deposit.interestRate, deposit.startDate,
                    today.coerceAtMost(deposit.endDate),
                )
                Text(
                    "${stringResource(R.string.deposit_rate)}: ${deposit.interestRate.toPlainString()}%",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "${deposit.startDate.format(dateFormatter)} – ${deposit.endDate.format(dateFormatter)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${stringResource(R.string.deposit_forecast_interest)}: ${interest.formatAmount(account.currency)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TransactionRow(txMeta: TransactionWithMeta, currency: String) {
    val tx = txMeta.transaction
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy").withLocale(AppLocale.current()) }
    val isExpense = tx.type == TransactionType.EXPENSE || tx.type == TransactionType.TRANSFER
    val amountColor = if (isExpense) MaterialTheme.colorScheme.error else Color(0xFF388E3C)
    val sign = if (isExpense) "−" else "+"

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                txMeta.categoryName.ifBlank { tx.type.name },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (tx.note.isNotBlank()) {
                Text(tx.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(tx.date.format(dateFormatter), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "$sign${tx.amount.formatAmount(currency)}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = amountColor,
        )
    }
}
