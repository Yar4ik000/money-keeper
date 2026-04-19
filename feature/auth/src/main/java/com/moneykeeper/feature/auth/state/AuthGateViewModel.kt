package com.moneykeeper.feature.auth.state

import androidx.lifecycle.ViewModel
import com.moneykeeper.core.database.security.DatabaseKeyStorage
import com.moneykeeper.feature.auth.domain.MasterKeyHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class AuthGateViewModel @Inject constructor(
    private val keyStorage: DatabaseKeyStorage,
    private val masterKeyHolder: MasterKeyHolder,
) : ViewModel() {

    private val _state = MutableStateFlow<AuthState>(computeInitialState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private fun computeInitialState(): AuthState = when {
        !keyStorage.isInitialized() -> AuthState.Uninitialized
        masterKeyHolder.isSet()     -> AuthState.Unlocked
        else                        -> AuthState.Locked
    }

    fun onPasswordSet()   { _state.value = AuthState.Unlocked }
    fun onUnlocked()      { _state.value = AuthState.Unlocked }
    fun onPasswordChanged() { /* остаёмся Unlocked */ }
    fun onCorrupted(message: String) { _state.value = AuthState.DataCorrupted(message) }
    fun onWiped()         { _state.value = AuthState.Uninitialized }
}
