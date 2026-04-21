package com.moneykeeper.feature.accounts.ui.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.moneykeeper.core.domain.calculator.DepositCalculator
import com.moneykeeper.core.domain.model.CapPeriod
import com.moneykeeper.core.domain.model.Deposit
import com.moneykeeper.core.domain.model.RateTier
import com.moneykeeper.core.ui.locale.AppLocale
import com.moneykeeper.core.ui.util.AmountTextField
import com.moneykeeper.feature.accounts.R
import com.moneykeeper.feature.accounts.ui.list.formatAmount
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val NOTIFY_OPTIONS = listOf(1, 3, 7, 14, 30)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DepositSection(
    deposit: Deposit,
    onChange: (Deposit) -> Unit,
    error: EditAccountError? = null,
    isNewDeposit: Boolean = false,
    isSavings: Boolean = false,
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy").withLocale(AppLocale.current()) }

    // String states for numeric fields — avoids losing trailing "." during input
    var amountText by remember(deposit.id) {
        mutableStateOf(
            if (deposit.initialAmount == BigDecimal.ZERO) ""
            else deposit.initialAmount.stripTrailingZeros().toPlainString()
        )
    }
    var rateText by remember(deposit.id) {
        mutableStateOf(deposit.interestRate.stripTrailingZeros().toPlainString())
    }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    val projectedInterest by remember(deposit) {
        derivedStateOf {
            val endOrToday = deposit.endDate ?: LocalDate.now().plusYears(1)
            if (endOrToday.isAfter(deposit.startDate) && deposit.initialAmount > BigDecimal.ZERO) {
                DepositCalculator.projectedBalance(deposit, endOrToday) - deposit.initialAmount
            } else BigDecimal.ZERO
        }
    }
    val projectedTotal by remember(deposit, projectedInterest) {
        derivedStateOf { deposit.initialAmount + projectedInterest }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (isSavings) stringResource(R.string.savings_params_title)
            else stringResource(R.string.deposit_params_title),
            style = MaterialTheme.typography.titleMedium,
        )

        // Amount
        AmountTextField(
            value = amountText,
            onValueChange = { v ->
                val filtered = v.replace(",", ".").filter { it.isDigit() || it == '.' }
                    .let { s -> // allow only one dot
                        val d = s.indexOf('.')
                        if (d >= 0) s.substring(0, d + 1) + s.substring(d + 1).filter { it.isDigit() } else s
                    }
                amountText = filtered
                filtered.toBigDecimalOrNull()?.let { onChange(deposit.copy(initialAmount = it)) }
            },
            label = { RequiredLabel(stringResource(R.string.deposit_amount)) },
            isError = error is EditAccountError.DepositAmountInvalid,
            supportingText = if (error is EditAccountError.DepositAmountInvalid) {
                { Text(stringResource(R.string.error_deposit_amount_invalid)) }
            } else null,
            modifier = Modifier.fillMaxWidth(),
        )

        // Base interest rate (shown only when no tiers, or always as "base")
        if (deposit.rateTiers.isEmpty()) {
            OutlinedTextField(
                value = rateText,
                onValueChange = { v ->
                    val filtered = v.replace(",", ".").filter { it.isDigit() || it == '.' }
                        .let { s ->
                            val d = s.indexOf('.')
                            if (d >= 0) s.substring(0, d + 1) + s.substring(d + 1).filter { it.isDigit() } else s
                        }
                    rateText = filtered
                    filtered.toBigDecimalOrNull()?.let { onChange(deposit.copy(interestRate = it)) }
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
        }

        // Rate tiers section
        RateTiersSection(
            deposit = deposit,
            rateText = rateText,
            onRateTextChange = { rateText = it },
            onChange = onChange,
        )

        // Start date
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

        // End date — only for term deposits, not for savings accounts
        if (!isSavings) {
            Box {
                val endDateError = error is EditAccountError.DepositDateInvalid ||
                    error is EditAccountError.DepositEndDatePast
                OutlinedTextField(
                    value = deposit.endDate?.format(dateFormatter) ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.deposit_end_date)) },
                    isError = endDateError,
                    supportingText = when (error) {
                        is EditAccountError.DepositDateInvalid ->
                            { { Text(stringResource(R.string.error_deposit_date_invalid)) } }
                        is EditAccountError.DepositEndDatePast ->
                            { { Text(stringResource(R.string.error_deposit_end_date_past)) } }
                        else -> null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.matchParentSize().clickable { showEndDatePicker = true })
            }
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
                            capitalizationPeriod = if (on)
                                deposit.capitalizationPeriod ?: if (isSavings) CapPeriod.DAILY else CapPeriod.MONTHLY
                            else null,
                        )
                    )
                },
            )
        }

        if (deposit.isCapitalized) {
            Text(stringResource(R.string.deposit_cap_period), style = MaterialTheme.typography.labelLarge)
            Column(Modifier.selectableGroup()) {
                val periods = if (isSavings)
                    CapPeriod.entries
                else
                    CapPeriod.entries.filter { it != CapPeriod.DAILY }
                periods.forEach { period ->
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

        if (!isSavings) {
            Text(
                text = stringResource(R.string.deposit_notify_days_label),
                style = MaterialTheme.typography.labelLarge,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NOTIFY_OPTIONS.forEach { days ->
                    val selected = days in deposit.notifyDaysBefore
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val updated = if (selected)
                                deposit.notifyDaysBefore - days
                            else
                                (deposit.notifyDaysBefore + days).sorted()
                            onChange(deposit.copy(notifyDaysBefore = updated))
                        },
                        label = { Text(pluralStringResource(R.plurals.notify_days, days, days)) },
                    )
                }
            }
        }

        if (!isSavings) {
            HorizontalDivider()
            ForecastCard(
                total = projectedTotal,
                interest = projectedInterest,
                currency = "RUB",
            )
        }
    }

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
            initial = deposit.endDate ?: LocalDate.now().plusYears(1),
            onConfirm = { date ->
                onChange(deposit.copy(endDate = date))
                showEndDatePicker = false
            },
            onDismiss = { showEndDatePicker = false },
            minDate = if (isNewDeposit) LocalDate.now() else null,
        )
    }
}

@Composable
private fun RateTiersSection(
    deposit: Deposit,
    rateText: String,
    onRateTextChange: (String) -> Unit,
    onChange: (Deposit) -> Unit,
) {
    if (deposit.rateTiers.isEmpty()) {
        TextButton(
            onClick = {
                // Add first tier using current interestRate, then a second one for changing rate
                val firstTier = RateTier(deposit.startDate, deposit.interestRate)
                val secondTier = RateTier(deposit.startDate.plusMonths(1), deposit.interestRate)
                onChange(deposit.copy(rateTiers = listOf(firstTier, secondTier)))
            },
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(stringResource(R.string.deposit_add_rate_tier))
        }
        return
    }

    Text(stringResource(R.string.deposit_rate_tiers), style = MaterialTheme.typography.labelLarge)
    deposit.rateTiers.forEachIndexed { index, tier ->
        RateTierRow(
            tier = tier,
            isFirst = index == 0,
            onRateChange = { newRate ->
                val updated = deposit.rateTiers.toMutableList()
                updated[index] = tier.copy(ratePercent = newRate)
                onChange(deposit.copy(interestRate = if (index == 0) newRate else deposit.interestRate, rateTiers = updated))
            },
            onDateChange = { newDate ->
                val updated = deposit.rateTiers.toMutableList()
                updated[index] = tier.copy(fromDate = newDate)
                onChange(deposit.copy(rateTiers = updated.sortedBy { it.fromDate }))
            },
            onRemove = if (index > 0) {
                {
                    val updated = deposit.rateTiers.toMutableList().also { it.removeAt(index) }
                    onChange(deposit.copy(rateTiers = updated))
                }
            } else null,
        )
    }
    TextButton(
        onClick = {
            val lastTier = deposit.rateTiers.last()
            val newTier = RateTier(lastTier.fromDate.plusMonths(1), lastTier.ratePercent)
            onChange(deposit.copy(rateTiers = deposit.rateTiers + newTier))
        },
    ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Text(stringResource(R.string.deposit_add_rate_tier))
    }
    TextButton(onClick = {
        val baseRate = deposit.rateTiers.firstOrNull()?.ratePercent ?: deposit.interestRate
        onChange(deposit.copy(rateTiers = emptyList(), interestRate = baseRate))
        onRateTextChange(baseRate.stripTrailingZeros().toPlainString())
    }) {
        Text(stringResource(R.string.deposit_remove_rate_tiers))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RateTierRow(
    tier: RateTier,
    isFirst: Boolean,
    onRateChange: (BigDecimal) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onRemove: (() -> Unit)?,
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy").withLocale(AppLocale.current()) }
    var rateText by remember(tier) { mutableStateOf(tier.ratePercent.stripTrailingZeros().toPlainString()) }
    var showDatePicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            OutlinedTextField(
                value = tier.fromDate.format(dateFormatter),
                onValueChange = {},
                readOnly = true,
                label = { Text(if (isFirst) stringResource(R.string.deposit_tier_from_start) else stringResource(R.string.deposit_tier_from_date)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isFirst,
            )
            if (!isFirst) Box(Modifier.matchParentSize().clickable { showDatePicker = true })
        }
        OutlinedTextField(
            value = rateText,
            onValueChange = { v ->
                val filtered = v.replace(",", ".").filter { it.isDigit() || it == '.' }
                    .let { s ->
                        val d = s.indexOf('.')
                        if (d >= 0) s.substring(0, d + 1) + s.substring(d + 1).filter { it.isDigit() } else s
                    }
                rateText = filtered
                filtered.toBigDecimalOrNull()?.let { onRateChange(it) }
            },
            label = { Text(stringResource(R.string.deposit_rate)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = null)
            }
        }
    }

    if (showDatePicker) {
        LocalDatePickerDialog(
            initial = tier.fromDate,
            onConfirm = { date -> onDateChange(date); showDatePicker = false },
            onDismiss = { showDatePicker = false },
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
internal fun LocalDatePickerDialog(
    initial: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    minDate: LocalDate? = null,
) {
    val epochMilli = initial.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val minUtcMillis = minDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    val selectableDates = if (minUtcMillis != null) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= minUtcMillis
            override fun isSelectableYear(year: Int) = year >= minDate!!.year
        }
    } else {
        object : SelectableDates {}
    }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = epochMilli,
        selectableDates = selectableDates,
    )
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
    CapPeriod.DAILY     -> R.string.deposit_cap_daily
    CapPeriod.MONTHLY   -> R.string.deposit_cap_monthly
    CapPeriod.QUARTERLY -> R.string.deposit_cap_quarterly
    CapPeriod.YEARLY    -> R.string.deposit_cap_yearly
}
