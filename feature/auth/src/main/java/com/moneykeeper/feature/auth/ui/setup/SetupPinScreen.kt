package com.moneykeeper.feature.auth.ui.setup

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneykeeper.feature.auth.R
import com.moneykeeper.feature.auth.ui.components.PinDots
import com.moneykeeper.feature.auth.ui.components.PinKeypad

@Composable
fun SetupPinScreen(
    onPinSet: () -> Unit,
    viewModel: SetupPinViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.done) { if (uiState.done) onPinSet() }

    var pin by remember { mutableStateOf("") }
    var confirmMode by remember { mutableStateOf(false) }
    var firstPin by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // ── Info block — anchored to top ──────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 72.dp, start = 32.dp, end = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (confirmMode) stringResource(R.string.setup_pin_confirm_title)
                       else stringResource(R.string.setup_pin_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (confirmMode) stringResource(R.string.setup_pin_confirm_subtitle)
                       else stringResource(R.string.setup_pin_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(40.dp))
            PinDots(count = pin.length)

            // Fixed-height slot for error text — dots never shift
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .padding(top = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    uiState.isLoading -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    uiState.error != null -> Text(
                        text = when (val e = uiState.error) {
                            SetupPinError.Mismatch   -> stringResource(R.string.setup_pin_error_mismatch)
                            SetupPinError.TooShort   -> stringResource(R.string.setup_pin_error_too_short)
                            is SetupPinError.Unknown -> e.message ?: stringResource(R.string.setup_error_unknown)
                            else                     -> ""
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        // ── Keypad block — anchored to bottom ─────────────────────────────────
        PinKeypad(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
            onDigit = { digit ->
                if (pin.length < 4) {
                    pin += digit
                    viewModel.clearError()
                    if (pin.length == 4) {
                        if (!confirmMode) {
                            firstPin = pin
                            pin = ""
                            confirmMode = true
                        } else {
                            viewModel.onSubmit(firstPin.toCharArray(), pin.toCharArray())
                            firstPin = ""
                            pin = ""
                        }
                    }
                }
            },
            onDelete = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
        )
    }
}
