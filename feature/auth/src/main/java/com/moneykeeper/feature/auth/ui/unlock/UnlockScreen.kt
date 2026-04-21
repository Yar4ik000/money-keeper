package com.moneykeeper.feature.auth.ui.unlock

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneykeeper.feature.auth.R

@Composable
fun UnlockScreen(
    onUnlocked: () -> Unit,
    onCorrupted: (String) -> Unit,
    viewModel: UnlockViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.unlocked) {
        if (uiState.unlocked) onUnlocked()
    }
    LaunchedEffect(uiState.corruptedMessage) {
        uiState.corruptedMessage?.let { onCorrupted(it) }
    }

    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.unlock_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; viewModel.clearError() },
            label = { Text(stringResource(R.string.unlock_password_label)) },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (!uiState.isLoading) viewModel.onPasswordSubmit(password.toCharArray())
            }),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = null,
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.Password },
            isError = uiState.error == UnlockError.WrongPassword,
        )

        uiState.error?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = when (error) {
                    UnlockError.WrongPassword   -> stringResource(R.string.unlock_error_wrong_password)
                    UnlockError.BiometricStale  -> stringResource(R.string.unlock_error_biometric_stale)
                    is UnlockError.LockedOut    -> stringResource(R.string.unlock_error_locked_out, error.secondsRemaining)
                },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        val isLockedOut = uiState.error is UnlockError.LockedOut
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { viewModel.onPasswordSubmit(password.toCharArray()) },
            enabled = !uiState.isLoading && !isLockedOut,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.unlock_button))
            }
        }

        if (viewModel.isBiometricAvailable) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    val activity = context as? FragmentActivity ?: return@OutlinedButton
                    viewModel.onBiometricClick(activity)
                },
                enabled = !uiState.isLoading && !isLockedOut,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.unlock_button_biometric))
            }
        }
    }
}
