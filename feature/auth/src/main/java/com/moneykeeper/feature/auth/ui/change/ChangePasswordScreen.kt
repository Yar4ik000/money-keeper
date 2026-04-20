package com.moneykeeper.feature.auth.ui.change

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneykeeper.feature.auth.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(
    onBack: () -> Unit,
    onChanged: () -> Unit,
    viewModel: ChangePasswordViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.done) {
        if (uiState.done) onChanged()
    }

    if (uiState.showOldBackupWarning) {
        AlertDialog(
            onDismissRequest = viewModel::dismissOldBackupDialog,
            title = { Text(stringResource(R.string.change_backup_warning_title)) },
            text  = { Text(stringResource(R.string.change_backup_warning_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissOldBackupDialog) {
                    Text(stringResource(R.string.change_backup_warning_ok))
                }
            },
        )
    }

    var oldPw   by remember { mutableStateOf("") }
    var newPw   by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    val pwOptions = KeyboardOptions(keyboardType = KeyboardType.Password)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.change_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            OutlinedTextField(
                value = oldPw, onValueChange = { oldPw = it; viewModel.clearError() },
                label = { Text(stringResource(R.string.change_old_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = pwOptions, modifier = Modifier.fillMaxWidth(),
                isError = uiState.error == ChangeError.WrongOldPassword,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = newPw, onValueChange = { newPw = it; viewModel.clearError() },
                label = { Text(stringResource(R.string.change_new_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = pwOptions, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = confirm, onValueChange = { confirm = it; viewModel.clearError() },
                label = { Text(stringResource(R.string.change_confirm_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = pwOptions, modifier = Modifier.fillMaxWidth(),
                isError = uiState.error == ChangeError.Mismatch,
            )

            uiState.error?.let { error ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when (error) {
                        ChangeError.Mismatch         -> stringResource(R.string.setup_error_mismatch)
                        ChangeError.TooShort         -> stringResource(R.string.setup_error_too_short)
                        ChangeError.WrongOldPassword -> stringResource(R.string.change_error_wrong_old)
                        ChangeError.DataCorrupted    -> stringResource(R.string.change_error_corrupted)
                        is ChangeError.Unknown       -> error.message ?: stringResource(R.string.setup_error_unknown)
                    },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { viewModel.onSubmit(oldPw.toCharArray(), newPw.toCharArray(), confirm.toCharArray()) },
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.change_button))
                }
            }
        }
    }
}
