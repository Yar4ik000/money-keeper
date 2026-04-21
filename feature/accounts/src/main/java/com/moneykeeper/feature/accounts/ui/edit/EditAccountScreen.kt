package com.moneykeeper.feature.accounts.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneykeeper.core.domain.model.AccountType
import com.moneykeeper.core.domain.model.CapPeriod
import com.moneykeeper.core.domain.model.Deposit
import com.moneykeeper.core.ui.util.ACCOUNT_ICON_OPTIONS
import com.moneykeeper.core.ui.util.AmountTextField
import com.moneykeeper.core.ui.util.accountIconVector
import com.moneykeeper.feature.accounts.R
import com.moneykeeper.feature.accounts.ui.list.parseColor
import java.math.BigDecimal

private val PRESET_COLORS = listOf(
    "#F44336", "#E91E63", "#9C27B0", "#3F51B5", "#2196F3",
    "#00BCD4", "#4CAF50", "#8BC34A", "#FF9800", "#FF5722",
    "#795548", "#607D8B",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditAccountScreen(
    accountId: Long? = null,
    viewModel: EditAccountViewModel = hiltViewModel(),
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val errorDepositMsg = stringResource(R.string.error_deposit_params_missing)

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    LaunchedEffect(state.error) {
        when (val err = state.error) {
            is EditAccountError.DepositParamsMissing -> snackbarHostState.showSnackbar(errorDepositMsg)
            is EditAccountError.Domain -> snackbarHostState.showSnackbar(err.error.message ?: "Error")
            null -> Unit
            else -> Unit // field-level errors shown inline; no snackbar needed
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (accountId == null) stringResource(R.string.edit_account_new_title)
                        else stringResource(R.string.edit_account_edit_title)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Account name (обязательное)
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { RequiredLabel(stringResource(R.string.edit_account_name)) },
                isError = state.error is EditAccountError.NameEmpty,
                supportingText = if (state.error is EditAccountError.NameEmpty) {
                    { Text(stringResource(R.string.error_name_empty)) }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Account type chips
            Text(stringResource(R.string.edit_account_type), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountType.entries.forEach { type ->
                    FilterChip(
                        selected = state.type == type,
                        onClick = { viewModel.onTypeChange(type) },
                        label = { Text(stringResource(accountTypeLabelRes(type))) },
                    )
                }
            }

            // Currency dropdown
            CurrencyDropdown(
                selected = state.currency,
                onSelect = viewModel::onCurrencyChange,
            )

            // Color picker
            Text(stringResource(R.string.edit_account_color), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PRESET_COLORS.forEach { hex ->
                    val isSelected = state.colorHex == hex
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(parseColor(hex))
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape,
                            )
                            .clickable { viewModel.onColorChange(hex) },
                    )
                }
            }

            // Icon picker
            Text(stringResource(R.string.edit_account_icon), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ACCOUNT_ICON_OPTIONS.forEach { (name, vector) ->
                    val isSelected = state.iconName == name
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(8.dp),
                            )
                            .clickable { viewModel.onIconChange(name) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = vector,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            // Initial balance (hidden for DEPOSIT/SAVINGS — balance = deposit.initialAmount)
            if (state.type != AccountType.DEPOSIT && state.type != AccountType.SAVINGS) {
                val isEditingExisting = accountId != null
                AmountTextField(
                    value = state.balanceInput,
                    onValueChange = viewModel::onBalanceInputChange,
                    label = { Text(stringResource(R.string.edit_account_initial_balance)) },
                    placeholder = { Text("0") },
                    readOnly = isEditingExisting,
                    enabled = !isEditingExisting,
                    supportingText = if (isEditingExisting) {
                        { Text(stringResource(R.string.edit_account_balance_locked_hint)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Deposit / savings section
            if (state.type == AccountType.DEPOSIT || state.type == AccountType.SAVINGS) {
                DepositSection(
                    deposit = state.deposit ?: if (state.type == AccountType.SAVINGS) defaultSavingsDeposit() else defaultDeposit(),
                    onChange = viewModel::onDepositChange,
                    error = state.error,
                    isNewDeposit = accountId == null,
                    isSavings = state.type == AccountType.SAVINGS,
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.edit_account_save))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyDropdown(selected: String, onSelect: (String) -> Unit) {
    val currencies = listOf("RUB", "USD", "EUR", "GBP", "CNY")
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.edit_account_currency)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            currencies.forEach { currency ->
                DropdownMenuItem(
                    text = { Text(currency) },
                    onClick = { onSelect(currency); expanded = false },
                )
            }
        }
    }
}

/** Метка поля с красной звёздочкой для обязательных полей. */
@Composable
internal fun RequiredLabel(text: String) {
    Text(
        buildAnnotatedString {
            append(text)
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) { append(" *") }
        }
    )
}

private fun accountTypeLabelRes(type: AccountType): Int = when (type) {
    AccountType.CARD       -> R.string.account_type_card
    AccountType.CASH       -> R.string.account_type_cash
    AccountType.DEPOSIT    -> R.string.account_type_deposit
    AccountType.SAVINGS    -> R.string.account_type_savings
    AccountType.INVESTMENT -> R.string.account_type_investment
    AccountType.OTHER      -> R.string.account_type_other
}
