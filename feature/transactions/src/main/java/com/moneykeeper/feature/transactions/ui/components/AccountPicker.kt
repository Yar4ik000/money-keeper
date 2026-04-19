package com.moneykeeper.feature.transactions.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import com.moneykeeper.core.domain.model.Account
import com.moneykeeper.core.domain.model.AccountType
import com.moneykeeper.feature.transactions.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountPicker(
    accounts: List<Account>,
    selectedId: Long?,
    title: String = stringResource(R.string.account_picker_title),
    onSelect: (Account) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var query by remember { mutableStateOf("") }
    val filtered = remember(accounts, query) {
        if (query.isBlank()) accounts
        else accounts.filter { it.name.contains(query, ignoreCase = true) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.account_picker_search)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
        HorizontalDivider(Modifier.padding(top = 4.dp))
        LazyColumn {
            items(filtered, key = { it.id }) { account ->
                val isSelected = account.id == selectedId
                val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bg)
                        .clickable { onSelect(account); onDismiss() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(parseCategoryColor(account.colorHex)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(account.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(accountTypeRes(account.type)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
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
