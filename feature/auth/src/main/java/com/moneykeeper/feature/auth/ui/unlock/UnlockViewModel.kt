package com.moneykeeper.feature.auth.ui.unlock

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.feature.auth.domain.BiometricAuthenticator
import com.moneykeeper.feature.auth.domain.BiometricEnrollment
import com.moneykeeper.feature.auth.domain.UnlockController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val unlockController: UnlockController,
    private val biometricAuthenticator: BiometricAuthenticator,
    private val biometricEnrollment: BiometricEnrollment,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UnlockUiState())
    val uiState = _uiState.asStateFlow()

    val isBiometricAvailable: Boolean
        get() = biometricAuthenticator.isAvailable() && biometricEnrollment.isEnrolled()

    fun onPasswordSubmit(password: CharArray) = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, error = null) }
        when (val result = unlockController.unlockWithPassword(password)) {
            UnlockController.UnlockResult.Success          -> _uiState.update { it.copy(isLoading = false, unlocked = true) }
            UnlockController.UnlockResult.WrongPassword    -> _uiState.update { it.copy(isLoading = false, error = UnlockError.WrongPassword) }
            is UnlockController.UnlockResult.DataCorrupted -> _uiState.update { it.copy(isLoading = false, corruptedMessage = result.message) }
            is UnlockController.UnlockResult.LockedOut     -> _uiState.update { it.copy(isLoading = false, error = UnlockError.LockedOut(result.secondsRemaining)) }
            else -> _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun onBiometricClick(activity: FragmentActivity) = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, error = null) }
        when (val result = unlockController.unlockWithBiometric(activity)) {
            UnlockController.UnlockResult.Success           -> _uiState.update { it.copy(isLoading = false, unlocked = true) }
            UnlockController.UnlockResult.BiometricCancelled -> _uiState.update { it.copy(isLoading = false) }
            UnlockController.UnlockResult.BiometricStale    -> _uiState.update { it.copy(isLoading = false, error = UnlockError.BiometricStale) }
            is UnlockController.UnlockResult.DataCorrupted  -> _uiState.update { it.copy(isLoading = false, corruptedMessage = result.message) }
            else -> _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}

data class UnlockUiState(
    val isLoading: Boolean = false,
    val error: UnlockError? = null,
    val unlocked: Boolean = false,
    val corruptedMessage: String? = null,
)

sealed interface UnlockError {
    data object WrongPassword : UnlockError
    data object BiometricStale : UnlockError
    data class LockedOut(val secondsRemaining: Long) : UnlockError
}
