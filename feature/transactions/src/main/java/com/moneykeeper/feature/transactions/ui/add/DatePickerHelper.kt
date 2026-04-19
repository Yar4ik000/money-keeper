package com.moneykeeper.feature.transactions.ui.add

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.moneykeeper.feature.transactions.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalDatePickerDialog(
    initial: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    /** Ограничение сверху: null — без ограничения (для дат окончания повторов и т.п.) */
    maxDate: LocalDate? = null,
) {
    val epochMilli = initial.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val maxUtcMillis = maxDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()

    val selectableDates = if (maxUtcMillis != null) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= maxUtcMillis
            override fun isSelectableYear(year: Int) = year <= maxDate!!.year
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
                onConfirm(
                    Instant.ofEpochMilli(millis)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                )
            }) {
                Text(stringResource(R.string.dialog_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    ) {
        DatePicker(state = pickerState)
    }
}
