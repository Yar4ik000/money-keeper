package com.moneykeeper.feature.auth.ui.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneykeeper.feature.auth.R

@Composable
fun SetupPasswordScreen(
    onPasswordSet: () -> Unit,
    viewModel: SetupPasswordViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.done) {
        if (uiState.done) onPasswordSet()
    }

    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var understood by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.setup_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.setup_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; viewModel.clearError() },
            label = { Text(stringResource(R.string.setup_password_label)) },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
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
                .semantics { contentType = ContentType.NewPassword },
            isError = uiState.error != null,
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = confirmation,
            onValueChange = { confirmation = it; viewModel.clearError() },
            label = { Text(stringResource(R.string.setup_confirm_label)) },
            singleLine = true,
            visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (understood && !uiState.isLoading)
                    viewModel.onSubmit(password.toCharArray(), confirmation.toCharArray())
            }),
            trailingIcon = {
                IconButton(onClick = { confirmVisible = !confirmVisible }) {
                    Icon(
                        imageVector = if (confirmVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = null,
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.NewPassword },
            isError = uiState.error is SetupError.Mismatch,
        )

        uiState.error?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = when (error) {
                    SetupError.Mismatch       -> stringResource(R.string.setup_error_mismatch)
                    SetupError.TooShort       -> stringResource(R.string.setup_error_too_short)
                    is SetupError.Unknown     -> error.message ?: stringResource(R.string.setup_error_unknown)
                },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Checkbox(checked = understood, onCheckedChange = { understood = it })
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.setup_understand_checkbox),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { viewModel.onSubmit(password.toCharArray(), confirmation.toCharArray()) },
            enabled = understood && !uiState.isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.setup_button_create))
            }
        }
    }
}
