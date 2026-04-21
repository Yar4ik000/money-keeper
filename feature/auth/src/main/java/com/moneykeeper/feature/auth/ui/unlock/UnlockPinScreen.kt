package com.moneykeeper.feature.auth.ui.unlock

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneykeeper.feature.auth.R
import com.moneykeeper.feature.auth.ui.components.PinDots
import com.moneykeeper.feature.auth.ui.components.PinKeypad

@Composable
fun UnlockPinScreen(
    onUnlocked: () -> Unit,
    onCorrupted: (String) -> Unit,
    viewModel: UnlockPinViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current as FragmentActivity

    LaunchedEffect(uiState.unlocked) { if (uiState.unlocked) onUnlocked() }
    LaunchedEffect(uiState.corruptedMessage) { uiState.corruptedMessage?.let { onCorrupted(it) } }

    var pin by remember { mutableStateOf("") }
    val isLockedOut = uiState.error is UnlockPinError.LockedOut

    LaunchedEffect(isLockedOut) { if (!isLockedOut) pin = "" }

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
                text = stringResource(R.string.unlock_pin_title),
                style = MaterialTheme.typography.headlineMedium,
            )

            // Fixed-height row for failed-attempt warning — avoids layout shift
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .padding(top = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (uiState.failedAttempts > 0 && !isLockedOut) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            stringResource(R.string.unlock_pin_failed_attempts, uiState.failedAttempts),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(Modifier.height(36.dp))
            PinDots(count = pin.length)

            // Fixed-height slot for error text or loading indicator — dots never shift
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
                            is UnlockPinError.WrongPin       -> stringResource(R.string.unlock_pin_error_wrong)
                            is UnlockPinError.BiometricStale -> stringResource(R.string.unlock_error_biometric_stale)
                            is UnlockPinError.LockedOut      -> stringResource(R.string.unlock_error_locked_out, e.secondsRemaining)
                            else                             -> ""
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        // ── Keypad block — anchored to bottom ─────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PinKeypad(
                onDigit = { digit ->
                    if (!isLockedOut && pin.length < 4) {
                        pin += digit
                        if (pin.length == 4) {
                            viewModel.onPinSubmit(pin.toCharArray())
                            pin = ""
                        }
                    }
                },
                onDelete = { if (!isLockedOut && pin.isNotEmpty()) pin = pin.dropLast(1) },
            )
            if (viewModel.isBiometricAvailable) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { viewModel.onBiometricClick(activity) },
                    enabled = !uiState.isLoading && !isLockedOut,
                ) {
                    Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.unlock_button_biometric))
                }
            }
        }
    }
}
