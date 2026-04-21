package com.moneykeeper.feature.auth.ui.unlock

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.feature.auth.domain.BiometricAuthenticator
import com.moneykeeper.feature.auth.domain.BiometricEnrollment
import com.moneykeeper.feature.auth.domain.UnlockController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UnlockPinViewModel @Inject constructor(
    private val unlockController: UnlockController,
    private val biometricAuthenticator: BiometricAuthenticator,
    private val biometricEnrollment: BiometricEnrollment,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UnlockPinUiState())
    val uiState: StateFlow<UnlockPinUiState> = _uiState.asStateFlow()

    val isBiometricAvailable: Boolean
        get() = biometricAuthenticator.isAvailable() && biometricEnrollment.isEnrolled()

    fun onPinSubmit(pin: CharArray) = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, error = null) }
        when (val result = unlockController.unlockWithPin(pin)) {
            is UnlockController.UnlockResult.Success ->
                _uiState.update { it.copy(isLoading = false, unlocked = true) }
            is UnlockController.UnlockResult.WrongPassword ->
                _uiState.update { it.copy(isLoading = false, error = UnlockPinError.WrongPin) }
            is UnlockController.UnlockResult.LockedOut ->
                _uiState.update { it.copy(isLoading = false, error = UnlockPinError.LockedOut(result.secondsRemaining)) }
            is UnlockController.UnlockResult.DataCorrupted ->
                _uiState.update { it.copy(isLoading = false, corruptedMessage = result.message) }
            else ->
                _uiState.update { it.copy(isLoading = false, error = UnlockPinError.WrongPin) }
        }
    }

    fun onBiometricClick(activity: FragmentActivity) = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, error = null) }
        when (val result = unlockController.unlockWithBiometric(activity)) {
            is UnlockController.UnlockResult.Success ->
                _uiState.update { it.copy(isLoading = false, unlocked = true) }
            is UnlockController.UnlockResult.BiometricCancelled ->
                _uiState.update { it.copy(isLoading = false) }
            is UnlockController.UnlockResult.BiometricStale ->
                _uiState.update { it.copy(isLoading = false, error = UnlockPinError.BiometricStale) }
            is UnlockController.UnlockResult.DataCorrupted ->
                _uiState.update { it.copy(isLoading = false, corruptedMessage = result.message) }
            else ->
                _uiState.update { it.copy(isLoading = false) }
        }
    }
}

data class UnlockPinUiState(
    val isLoading: Boolean = false,
    val error: UnlockPinError? = null,
    val unlocked: Boolean = false,
    val corruptedMessage: String? = null,
)

sealed interface UnlockPinError {
    data object WrongPin : UnlockPinError
    data object BiometricStale : UnlockPinError
    data class LockedOut(val secondsRemaining: Long) : UnlockPinError
}
