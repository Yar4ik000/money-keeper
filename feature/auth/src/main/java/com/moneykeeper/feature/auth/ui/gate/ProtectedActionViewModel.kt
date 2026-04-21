package com.moneykeeper.feature.auth.ui.gate

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.feature.auth.domain.BiometricAuthenticator
import com.moneykeeper.feature.auth.domain.BiometricEnrollment
import com.moneykeeper.feature.auth.domain.PinVerifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProtectedActionViewModel @Inject constructor(
    private val biometricAuthenticator: BiometricAuthenticator,
    private val biometricEnrollment: BiometricEnrollment,
    private val pinVerifier: PinVerifier,
) : ViewModel() {

    private val _state = MutableStateFlow<ProtectedActionState>(ProtectedActionState.Idle)
    val state: StateFlow<ProtectedActionState> = _state.asStateFlow()

    val hasBiometric: Boolean
        get() = biometricAuthenticator.isAvailable() && biometricEnrollment.isEnrolled()

    fun start(activity: FragmentActivity) = viewModelScope.launch {
        if (hasBiometric) {
            val confirmed = biometricAuthenticator.confirmIdentity(activity)
            if (confirmed) { _state.value = ProtectedActionState.Granted(); return@launch }
        }
        _state.value = ProtectedActionState.AwaitingPin()
    }

    fun verifyPin(pin: CharArray) = viewModelScope.launch {
        val ok = pinVerifier.verify(pin)
        pin.fill(0.toChar())
        _state.value = if (ok) ProtectedActionState.Granted()
                       else ProtectedActionState.AwaitingPin(wrongPin = true)
    }

    fun cancel() { _state.value = ProtectedActionState.Idle }
    fun reset()  { _state.value = ProtectedActionState.Idle }
}

sealed interface ProtectedActionState {
    data object Idle : ProtectedActionState
    data class AwaitingPin(val wrongPin: Boolean = false) : ProtectedActionState
    // nonce ensures two rapid consecutive grants are distinct values in the StateFlow,
    // preventing the second from being deduplicated and silently dropped.
    data class Granted(val nonce: Long = System.nanoTime()) : ProtectedActionState
}
