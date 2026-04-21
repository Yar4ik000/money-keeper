package com.moneykeeper.feature.accounts.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import com.moneykeeper.core.ui.util.accountIconVector
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.model.AccountType
import com.moneykeeper.core.domain.model.Deposit
import com.moneykeeper.core.domain.money.MultiCurrencyTotal
import com.moneykeeper.core.ui.locale.AppLocale
import com.moneykeeper.feature.accounts.R
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel = hiltViewModel(),
    onAccountClick: (Long) -> Unit,
    onAddAccount: () -> Unit,
    onEditAccount: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val showArchived by viewModel.showArchived.collectAsStateWithLifecycle()
    val reorderDraft by viewModel.reorderDraft.collectAsStateWithLifecycle()
    val inReorderMode = reorderDraft != null

    Scaffold(
        topBar = {
            if (inReorderMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.accounts_reorder_title)) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.cancelReorder() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.accounts_reorder_cancel),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.saveReorder() }) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = stringResource(R.string.accounts_reorder_save),
                            )
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.accounts_title)) },
                    actions = {
                        IconButton(onClick = { viewModel.setShowArchived(!showArchived) }) {
                            Icon(
                                imageVector = if (showArchived) Icons.Outlined.VisibilityOff
                                              else Icons.Outlined.Visibility,
                                contentDescription = stringResource(
                                    if (showArchived) R.string.accounts_hide_archived
                                    else R.string.accounts_show_archived,
                                ),
                            )
                        }
                        IconButton(onClick = { viewModel.startReorder() }) {
                            Icon(
                                Icons.Default.SwapVert,
                                contentDescription = stringResource(R.string.accounts_reorder),
                            )
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!inReorderMode) {
                FloatingActionButton(onClick = onAddAccount) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.accounts_add))
                }
            }
        },
    ) { padding ->
        if (inReorderMode) {
            ReorderList(
                accounts = reorderDraft.orEmpty(),
                onMoveUp = { viewModel.moveAccount(it, -1) },
                onMoveDown = { viewModel.moveAccount(it, +1) },
                modifier = Modifier.padding(padding),
            )
        } else when (val s = state) {
            is AccountsUiState.Loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is AccountsUiState.Success -> AccountsList(
                state = s,
                onAccountClick = onAccountClick,
                onEditAccount = onEditAccount,
                onArchive = { viewModel.archiveAccount(it) },
                onUnarchive = { viewModel.unarchiveAccount(it) },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun ReorderList(
    accounts: List<Account>,
    onMoveUp: (Long) -> Unit,
    onMoveDown: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(accounts, key = { it.id }) { account ->
            val index = accounts.indexOf(account)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(parseColor(account.colorHex)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = accountIconVector(account.iconName),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { onMoveUp(account.id) },
                        enabled = index > 0,
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.accounts_move_up),
                        )
                    }
                    IconButton(
                        onClick = { onMoveDown(account.id) },
                        enabled = index < accounts.size - 1,
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.accounts_move_down),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountsList(
    state: AccountsUiState.Success,
    onAccountClick: (Long) -> Unit,
    onEditAccount: (Long) -> Unit,
    onArchive: (Long) -> Unit,
    onUnarchive: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val depositMap = state.deposits.associateBy { it.accountId }
    val grouped = state.accounts.groupBy { it.type }
    val order = listOf(
        AccountType.CARD,
        AccountType.CASH,
        AccountType.DEPOSIT,
        AccountType.SAVINGS,
        AccountType.INVESTMENT,
        AccountType.OTHER,
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        item { TotalBalanceCard(state.totalsByCurrency) }

        order.forEach { type ->
            val accounts = grouped[type] ?: return@forEach
            item {
                Text(
                    text = stringResource(accountTypeSectionRes(type)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(accounts, key = { it.id }) { account ->
                AccountCard(
                    account = account,
                    deposit = depositMap[account.id],
                    onClick = { onAccountClick(account.id) },
                    onEdit = { onEditAccount(account.id) },
                    onArchive = { onArchive(account.id) },
                    onUnarchive = { onUnarchive(account.id) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun TotalBalanceCard(totals: MultiCurrencyTotal) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.accounts_total_balance),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            totals.entries.forEach { entry ->
                Text(
                    text = entry.amount.formatAmount(entry.currency),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (totals.entries.isEmpty()) {
                Text(
                    text = BigDecimal.ZERO.formatAmount("RUB"),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun AccountCard(
    account: Account,
    deposit: Deposit?,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(parseColor(account.colorHex)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = accountIconVector(account.iconName),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        account.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (account.isArchived)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    if (account.isArchived) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.accounts_archived_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
                Text(
                    stringResource(accountTypeRes(account.type)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (account.type == AccountType.DEPOSIT && deposit != null) {
                    DepositProgressRow(deposit, today)
                }
            }

            Spacer(Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = account.balance.formatAmount(account.currency),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (account.balance < BigDecimal.ZERO)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurface,
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.account_detail_edit)) },
                            onClick = { menuExpanded = false; onEdit() },
                        )
                        if (account.isArchived) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.accounts_unarchive)) },
                                onClick = { menuExpanded = false; onUnarchive() },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.accounts_archive)) },
                                onClick = { menuExpanded = false; onArchive() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DepositProgressRow(deposit: Deposit, today: LocalDate) {
    val endDate = deposit.endDate ?: return
    val totalDays = ChronoUnit.DAYS.between(deposit.startDate, endDate).coerceAtLeast(1)
    val passedDays = ChronoUnit.DAYS.between(deposit.startDate, today.coerceAtMost(endDate)).coerceAtLeast(0)
    val progress = passedDays.toFloat() / totalDays.toFloat()
    val daysLeft = ChronoUnit.DAYS.between(today, endDate).coerceAtLeast(0)
    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy").withLocale(AppLocale.current())

    Column(Modifier.padding(top = 4.dp)) {
        Text(
            text = stringResource(R.string.deposit_days_left, endDate.format(formatter), daysLeft),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
        )
    }
}

private fun accountTypeRes(type: AccountType): Int = when (type) {
    AccountType.CARD       -> R.string.account_type_card
    AccountType.CASH       -> R.string.account_type_cash
    AccountType.DEPOSIT    -> R.string.account_type_deposit
    AccountType.SAVINGS    -> R.string.account_type_savings
    AccountType.INVESTMENT -> R.string.account_type_investment
    AccountType.OTHER      -> R.string.account_type_other
}

private fun accountTypeSectionRes(type: AccountType): Int = when (type) {
    AccountType.CARD       -> R.string.accounts_section_cards
    AccountType.CASH       -> R.string.accounts_section_cash
    AccountType.DEPOSIT    -> R.string.accounts_section_deposits
    AccountType.SAVINGS    -> R.string.accounts_section_savings
    AccountType.INVESTMENT -> R.string.accounts_section_investments
    AccountType.OTHER      -> R.string.accounts_section_other
}

internal fun parseColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    Color(0xFF4CAF50)
}

internal fun BigDecimal.formatAmount(currency: String): String {
    val nf = NumberFormat.getNumberInstance(AppLocale.current()).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    val symbol = when (currency) {
        "RUB" -> "₽"
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        else  -> currency
    }
    return "${nf.format(this.setScale(2, RoundingMode.HALF_EVEN))} $symbol"
}
