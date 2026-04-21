package com.moneykeeper.feature.auth.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneykeeper.core.database.DatabaseProvider
import com.moneykeeper.core.database.security.DatabaseKeyStorage
import com.moneykeeper.feature.auth.domain.MasterKeyHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class AuthGateViewModel @Inject constructor(
    private val keyStorage: DatabaseKeyStorage,
    private val masterKeyHolder: MasterKeyHolder,
    private val databaseProvider: DatabaseProvider,
) : ViewModel() {

    private val _state = MutableStateFlow<AuthState>(computeInitialState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        // When AppAutoLockObserver clears the key and closes the DB, DatabaseProvider.state
        // transitions to Idle. Detect that and move back to Locked automatically.
        databaseProvider.state
            .onEach { dbState ->
                if (dbState == DatabaseProvider.State.Idle && _state.value == AuthState.Unlocked) {
                    _state.value = AuthState.Locked
                }
            }
            .launchIn(viewModelScope)
    }

    private fun computeInitialState(): AuthState = when {
        keyStorage.isV2Initialized() -> {
            if (masterKeyHolder.isSet()) AuthState.Unlocked else AuthState.Locked
        }
        keyStorage.isInitialized()   -> AuthState.PinSetupRequired  // v1 data, needs migration
        else                         -> AuthState.Uninitialized
    }

    fun onPasswordSet()     { _state.value = AuthState.Unlocked }
    fun onMigrated()        { _state.value = AuthState.Unlocked }
    fun onUnlocked()        { _state.value = AuthState.Unlocked }
    fun onPasswordChanged() { /* остаёмся Unlocked */ }
    fun onCorrupted(message: String) { _state.value = AuthState.DataCorrupted(message) }
    fun onWiped()           { _state.value = AuthState.Uninitialized }
}
