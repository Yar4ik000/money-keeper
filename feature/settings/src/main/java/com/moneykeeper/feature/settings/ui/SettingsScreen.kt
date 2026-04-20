package com.moneykeeper.feature.settings.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneykeeper.feature.settings.R
import com.moneykeeper.feature.settings.ui.security.SecurityViewModel

private val SUPPORTED_CURRENCIES = listOf("RUB", "USD", "EUR", "GBP", "CNY", "BYN", "KZT")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onCategories: () -> Unit,
    onBackup: () -> Unit,
    onChangePassword: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    securityViewModel: SecurityViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isBiometricEnrolled by securityViewModel.isBiometricEnrolled.collectAsStateWithLifecycle()
    val enrollError by securityViewModel.enrollError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(enrollError) {
        enrollError?.let {
            snackbarHostState.showSnackbar(it)
            securityViewModel.clearEnrollError()
        }
    }

    var showTimePicker by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showAutoLockDialog by remember { mutableStateOf(false) }

    val themeLabel = when (settings.themeMode) {
        "light" -> stringResource(R.string.theme_light)
        "dark" -> stringResource(R.string.theme_dark)
        else -> stringResource(R.string.theme_system)
    }
    val appVersion = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—" }.getOrDefault("—")
    }

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(stringResource(R.string.settings_section_general))

            ListItem(
                leadingContent = { Icon(Icons.Outlined.CurrencyExchange, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_currency)) },
                trailingContent = { Text(settings.currencyCode, style = MaterialTheme.typography.bodyMedium) },
                modifier = Modifier.clickable { showCurrencyDialog = true },
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Palette, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_theme)) },
                trailingContent = { Text(themeLabel, style = MaterialTheme.typography.bodyMedium) },
                modifier = Modifier.clickable { showThemeDialog = true },
            )

            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_section_notifications))

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
            SectionHeader(stringResource(R.string.settings_section_data))

            ListItem(
                leadingContent = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_categories)) },
                supportingContent = { Text(stringResource(R.string.settings_categories_subtitle)) },
                modifier = Modifier.clickable(onClick = onCategories),
            )

            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_section_security))

            ListItem(
                leadingContent = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_change_password)) },
                supportingContent = { Text(stringResource(R.string.settings_change_password_subtitle)) },
                modifier = Modifier.clickable(onClick = onChangePassword),
            )

            val autoLockLabel = when (settings.autoLockTimeoutMinutes) {
                -1   -> stringResource(R.string.settings_autolock_never)
                0    -> stringResource(R.string.settings_autolock_immediate)
                1    -> stringResource(R.string.settings_autolock_minutes, 1)
                else -> stringResource(R.string.settings_autolock_minutes, settings.autoLockTimeoutMinutes)
            }
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_autolock)) },
                supportingContent = { Text(stringResource(R.string.settings_autolock_subtitle)) },
                trailingContent = { Text(autoLockLabel, style = MaterialTheme.typography.bodyMedium) },
                modifier = Modifier.clickable { showAutoLockDialog = true },
            )

            if (securityViewModel.isBiometricAvailable) {
                val activity = context as? FragmentActivity
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Fingerprint, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.settings_biometric)) },
                    supportingContent = { Text(stringResource(R.string.settings_biometric_subtitle)) },
                    trailingContent = {
                        Switch(
                            checked = isBiometricEnrolled,
                            onCheckedChange = { enabled ->
                                if (enabled && activity != null) securityViewModel.enrollBiometric(activity)
                                else securityViewModel.disableBiometric()
                            },
                        )
                    },
                )
            }

            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_section_backup))

            ListItem(
                leadingContent = { Icon(Icons.Outlined.Backup, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_backup)) },
                supportingContent = { Text(stringResource(R.string.settings_backup_subtitle)) },
                modifier = Modifier.clickable(onClick = onBackup),
            )

            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_section_about))

            ListItem(
                leadingContent = { Icon(Icons.Outlined.BugReport, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_share_logs)) },
                supportingContent = { Text(stringResource(R.string.settings_share_logs_subtitle)) },
                modifier = Modifier.clickable {
                    val logDir = File(context.filesDir, "crash_logs")
                    val files = logDir.takeIf { it.exists() }?.listFiles()?.toList() ?: emptyList()
                    if (files.isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.settings_no_logs), Toast.LENGTH_SHORT).show()
                        return@clickable
                    }
                    val uris = files.map { file ->
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    }
                    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "text/plain"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                },
            )

            ListItem(
                leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_version)) },
                trailingContent = { Text(appVersion, style = MaterialTheme.typography.bodyMedium) },
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
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }

    if (showThemeDialog) {
        val options = listOf(
            "system" to stringResource(R.string.theme_system),
            "light" to stringResource(R.string.theme_light),
            "dark" to stringResource(R.string.theme_dark),
        )
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(R.string.settings_theme)) },
            text = {
                Column {
                    options.forEach { (key, label) ->
                        ListItem(
                            headlineContent = { Text(label) },
                            leadingContent = {
                                RadioButton(
                                    selected = settings.themeMode == key,
                                    onClick = { viewModel.setThemeMode(key); showThemeDialog = false },
                                )
                            },
                            modifier = Modifier.clickable { viewModel.setThemeMode(key); showThemeDialog = false },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }

    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text(stringResource(R.string.settings_currency)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SUPPORTED_CURRENCIES.forEach { code ->
                        ListItem(
                            headlineContent = { Text(code) },
                            supportingContent = {
                                val currency = runCatching { java.util.Currency.getInstance(code) }.getOrNull()
                                if (currency != null) Text("${currency.getDisplayName(java.util.Locale("ru"))} ${currency.symbol}")
                            },
                            leadingContent = {
                                RadioButton(
                                    selected = settings.currencyCode == code,
                                    onClick = { viewModel.setCurrency(code); showCurrencyDialog = false },
                                )
                            },
                            modifier = Modifier.clickable { viewModel.setCurrency(code); showCurrencyDialog = false },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCurrencyDialog = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }

    if (showAutoLockDialog) {
        val autoLockOptions = listOf(
            -1 to stringResource(R.string.settings_autolock_never),
            0  to stringResource(R.string.settings_autolock_immediate),
            1  to stringResource(R.string.settings_autolock_minutes, 1),
            5  to stringResource(R.string.settings_autolock_minutes, 5),
            15 to stringResource(R.string.settings_autolock_minutes, 15),
            30 to stringResource(R.string.settings_autolock_minutes, 30),
        )
        AlertDialog(
            onDismissRequest = { showAutoLockDialog = false },
            title = { Text(stringResource(R.string.settings_autolock)) },
            text = {
                Column {
                    autoLockOptions.forEach { (minutes, label) ->
                        ListItem(
                            headlineContent = { Text(label) },
                            leadingContent = {
                                RadioButton(
                                    selected = settings.autoLockTimeoutMinutes == minutes,
                                    onClick = { viewModel.setAutoLockTimeout(minutes); showAutoLockDialog = false },
                                )
                            },
                            modifier = Modifier.clickable { viewModel.setAutoLockTimeout(minutes); showAutoLockDialog = false },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAutoLockDialog = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
