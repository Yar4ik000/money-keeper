package com.moneykeeper.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneykeeper.feature.settings.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onCategories: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showTimePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_deposit_notifications)) },
                supportingContent = { Text(stringResource(R.string.settings_deposit_notifications_subtitle)) },
                trailingContent = {
                    Switch(
                        checked = settings.depositNotificationsEnabled,
                        onCheckedChange = { viewModel.toggleDepositNotifications(it) },
                    )
                },
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Repeat, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_recurring_reminders)) },
                supportingContent = { Text(stringResource(R.string.settings_recurring_reminders_subtitle)) },
                trailingContent = {
                    Switch(
                        checked = settings.recurringRemindersEnabled,
                        onCheckedChange = { viewModel.toggleRecurringReminders(it) },
                    )
                },
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.AccessTime, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_notification_time)) },
                supportingContent = {
                    Text(
                        stringResource(
                            R.string.settings_notification_time_value,
                            settings.notificationHour,
                            settings.notificationMinute,
                        )
                    )
                },
                modifier = Modifier.clickable { showTimePicker = true },
            )
            HorizontalDivider()
            ListItem(
                leadingContent = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_categories)) },
                supportingContent = { Text(stringResource(R.string.settings_categories_subtitle)) },
                modifier = Modifier.clickable(onClick = onCategories),
            )
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = settings.notificationHour,
            initialMinute = settings.notificationMinute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.settings_notification_time)) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateNotificationTime(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}
