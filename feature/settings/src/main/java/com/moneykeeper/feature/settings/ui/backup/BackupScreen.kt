package com.moneykeeper.feature.settings.ui.backup

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneykeeper.feature.settings.R
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessage() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    val today = LocalDate.now().toString()

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {
        it?.let { viewModel.exportCsv(it) }
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
        it?.let { viewModel.onBackupUriPicked(it) }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { viewModel.onBackupFilePicked(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
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
            modifier = Modifier.padding(padding).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (state.isLoading) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(16.dp))
            }

            ListItem(
                leadingContent = { Icon(Icons.Outlined.TableChart, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.backup_export_csv)) },
                supportingContent = { Text(stringResource(R.string.backup_export_csv_desc)) },
                modifier = Modifier.then(
                    if (!state.isLoading) Modifier.padding(0.dp) else Modifier
                ),
            )
            TextButton(
                onClick = { csvLauncher.launch("money_keeper_$today.csv") },
                enabled = !state.isLoading,
                modifier = Modifier.align(Alignment.End).padding(end = 16.dp),
            ) { Text(stringResource(R.string.backup_export)) }

            ListItem(
                leadingContent = { Icon(Icons.Outlined.Backup, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.backup_create)) },
                supportingContent = { Text(stringResource(R.string.backup_create_desc)) },
            )
            TextButton(
                onClick = { backupLauncher.launch("money_keeper_backup_$today.mkbak") },
                enabled = !state.isLoading,
                modifier = Modifier.align(Alignment.End).padding(end = 16.dp),
            ) { Text(stringResource(R.string.backup_create_action)) }

            ListItem(
                leadingContent = { Icon(Icons.Outlined.Restore, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.backup_restore)) },
                supportingContent = { Text(stringResource(R.string.backup_restore_desc)) },
            )
            TextButton(
                onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                enabled = !state.isLoading,
                modifier = Modifier.align(Alignment.End).padding(end = 16.dp),
            ) { Text(stringResource(R.string.backup_restore_action)) }
        }
    }

    if (state.pendingBackupUri != null) {
        ExportPasswordDialog(
            onSubmit = { viewModel.submitBackupPassword(it) },
            onDismiss = { viewModel.cancelBackup() },
        )
    }

    if (state.pendingRestoreUri != null) {
        RestorePasswordDialog(
            createdAt = state.pendingRestoreCreatedAt,
            backupVersion = state.pendingRestoreBackupVersion,
            wrongPassword = state.wrongPassword,
            onSubmit = { viewModel.submitRestorePassword(it) },
            onDismiss = { viewModel.cancelRestore() },
        )
    }

    if (state.restoreCompleted) {
        val activity = LocalContext.current as Activity
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.restore_done_title)) },
            text = { Text(stringResource(R.string.restore_done_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.restartApp(activity) }) {
                    Text(stringResource(R.string.restart_app))
                }
            },
        )
    }
}

@Composable
internal fun ExportPasswordDialog(
    onSubmit: (CharArray) -> Unit,
    onDismiss: () -> Unit,
) {
    // Use remember (not rememberSaveable) so passwords never land in the savedInstanceState
    // bundle, which the system may write to disk.
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(Unit) {
        onDispose { password = ""; confirm = "" }
    }

    val passwordErrors = setOf(
        R.string.backup_password_too_short,
        R.string.backup_password_no_upper,
        R.string.backup_password_no_lower,
        R.string.backup_password_no_digit,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_password_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup_password_save_reminder))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.backup_password_offline_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text(stringResource(R.string.backup_password_hint)) },
                    isError = error in passwordErrors,
                    supportingText = if (error in passwordErrors) {
                        { Text(stringResource(error!!)) }
                    } else null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentType = ContentType.NewPassword },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it; error = null },
                    label = { Text(stringResource(R.string.backup_password_confirm_hint)) },
                    isError = error == R.string.backup_password_mismatch,
                    supportingText = if (error == R.string.backup_password_mismatch) {
                        { Text(stringResource(R.string.backup_password_mismatch)) }
                    } else null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentType = ContentType.NewPassword },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (BackupPasswordValidator.validate(password, confirm)) {
                        BackupPasswordValidator.Error.TOO_SHORT -> error = R.string.backup_password_too_short
                        BackupPasswordValidator.Error.NO_UPPER  -> error = R.string.backup_password_no_upper
                        BackupPasswordValidator.Error.NO_LOWER  -> error = R.string.backup_password_no_lower
                        BackupPasswordValidator.Error.NO_DIGIT  -> error = R.string.backup_password_no_digit
                        BackupPasswordValidator.Error.MISMATCH  -> error = R.string.backup_password_mismatch
                        null -> { onSubmit(password.toCharArray()); password = ""; confirm = "" }
                    }
                },
                enabled = password.isNotEmpty(),
            ) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun RestorePasswordDialog(
    createdAt: String?,
    backupVersion: Int,
    wrongPassword: Boolean,
    onSubmit: (CharArray) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose { password = "" }
    }

    val hintRes = if (backupVersion >= 2) R.string.restore_password_hint_v2
                  else R.string.restore_password_hint_v1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (createdAt != null) {
                    Text(stringResource(R.string.restore_backup_date, createdAt))
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(hintRes)) },
                    isError = wrongPassword,
                    supportingText = if (wrongPassword) {
                        { Text(stringResource(R.string.restore_wrong_password)) }
                    } else null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentType = ContentType.Password },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(password.toCharArray()); password = "" },
                enabled = password.isNotEmpty(),
            ) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
