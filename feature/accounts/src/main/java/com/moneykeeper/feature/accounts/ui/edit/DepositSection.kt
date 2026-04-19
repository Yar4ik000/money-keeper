package com.moneykeeper.feature.accounts.ui.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.moneykeeper.core.domain.model.CapPeriod
import com.moneykeeper.core.domain.model.Deposit
import com.moneykeeper.core.ui.locale.AppLocale
import com.moneykeeper.feature.accounts.R
import com.moneykeeper.feature.accounts.domain.DepositCalculator
import com.moneykeeper.feature.accounts.ui.list.formatAmount
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepositSection(
    deposit: Deposit,
    onChange: (Deposit) -> Unit,
    error: EditAccountError? = null,
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy").withLocale(AppLocale.current()) }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    val projectedInterest by remember(deposit) {
        derivedStateOf {
            if (deposit.endDate.isAfter(deposit.startDate) && deposit.initialAmount > BigDecimal.ZERO) {
                val period = deposit.capitalizationPeriod
                if (deposit.isCapitalized && period != null)
                    DepositCalculator.compoundInterest(
                        deposit.initialAmount, deposit.interestRate,
                        deposit.startDate, deposit.endDate, period,
                    )
                else
                    DepositCalculator.simpleInterest(
                        deposit.initialAmount, deposit.interestRate,
                        deposit.startDate, deposit.endDate,
                    )
            } else BigDecimal.ZERO
        }
    }
    val projectedTotal by remember(deposit, projectedInterest) {
        derivedStateOf { deposit.initialAmount.add(projectedInterest) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.deposit_params_title), style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = deposit.initialAmount.toPlainString(),
            onValueChange = { v ->
                v.toBigDecimalOrNull()?.let { onChange(deposit.copy(initialAmount = it)) }
            },
            label = { RequiredLabel(stringResource(R.string.deposit_amount)) },
            isError = error is EditAccountError.DepositAmountInvalid,
            supportingText = if (error is EditAccountError.DepositAmountInvalid) {
                { Text(stringResource(R.string.error_deposit_amount_invalid)) }
            } else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = deposit.interestRate.toPlainString(),
            onValueChange = { v ->
                v.toBigDecimalOrNull()?.let { onChange(deposit.copy(interestRate = it)) }
            },
            label = { RequiredLabel(stringResource(R.string.deposit_rate)) },
            isError = error is EditAccountError.DepositRateInvalid,
            supportingText = if (error is EditAccountError.DepositRateInvalid) {
                { Text(stringResource(R.string.error_deposit_rate_invalid)) }
            } else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        // Start date — невидимый Box поверх TextField перехватывает касание,
        // т.к. selectable/clickable на readOnly OutlinedTextField не срабатывает.
        Box {
            OutlinedTextField(
                value = deposit.startDate.format(dateFormatter),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.deposit_start_date)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Box(Modifier.matchParentSize().clickable { showStartDatePicker = true })
        }

        // End date
        Box {
            OutlinedTextField(
                value = deposit.endDate.format(dateFormatter),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.deposit_end_date)) },
                isError = error is EditAccountError.DepositDateInvalid,
                supportingText = if (error is EditAccountError.DepositDateInvalid) {
                    { Text(stringResource(R.string.error_deposit_date_invalid)) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )
            Box(Modifier.matchParentSize().clickable { showEndDatePicker = true })
        }

        // Capitalization toggle
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.deposit_capitalization_label), Modifier.weight(1f))
            Switch(
                checked = deposit.isCapitalized,
                onCheckedChange = { on ->
                    onChange(
                        deposit.copy(
                            isCapitalized = on,
                            capitalizationPeriod = if (on) deposit.capitalizationPeriod ?: CapPeriod.MONTHLY else null,
                        )
                    )
                },
            )
        }

        if (deposit.isCapitalized) {
            Text(stringResource(R.string.deposit_cap_period), style = MaterialTheme.typography.labelLarge)
            Column(Modifier.selectableGroup()) {
                CapPeriod.entries.forEach { period ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = deposit.capitalizationPeriod == period,
                                onClick = { onChange(deposit.copy(capitalizationPeriod = period)) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = deposit.capitalizationPeriod == period, onClick = null)
                        Text(stringResource(capPeriodRes(period)), Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        OutlinedTextField(
            value = deposit.notifyDaysBefore.toString(),
            onValueChange = { v ->
                v.toIntOrNull()?.let { onChange(deposit.copy(notifyDaysBefore = it)) }
            },
            label = { Text(stringResource(R.string.deposit_notify_days)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        // Forecast preview card
        HorizontalDivider()
        ForecastCard(
            total = projectedTotal,
            interest = projectedInterest,
            currency = "RUB",
        )
    }

    // Date pickers
    if (showStartDatePicker) {
        LocalDatePickerDialog(
            initial = deposit.startDate,
            onConfirm = { date ->
                onChange(deposit.copy(startDate = date))
                showStartDatePicker = false
            },
            onDismiss = { showStartDatePicker = false },
        )
    }
    if (showEndDatePicker) {
        LocalDatePickerDialog(
            initial = deposit.endDate,
            onConfirm = { date ->
                onChange(deposit.copy(endDate = date))
                showEndDatePicker = false
            },
            onDismiss = { showEndDatePicker = false },
        )
    }
}

@Composable
private fun ForecastCard(total: BigDecimal, interest: BigDecimal, currency: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.deposit_forecast_title), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            ForecastRow(stringResource(R.string.deposit_forecast_total), total.formatAmount(currency))
            ForecastRow(stringResource(R.string.deposit_forecast_interest), interest.formatAmount(currency))
        }
    }
}

@Composable
private fun ForecastRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalDatePickerDialog(
    initial: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val epochMilli = initial.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = epochMilli)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = pickerState.selectedDateMillis ?: return@TextButton
                val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                onConfirm(date)
            }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

private fun capPeriodRes(period: CapPeriod): Int = when (period) {
    CapPeriod.MONTHLY   -> R.string.deposit_cap_monthly
    CapPeriod.QUARTERLY -> R.string.deposit_cap_quarterly
    CapPeriod.YEARLY    -> R.string.deposit_cap_yearly
}
