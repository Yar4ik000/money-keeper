package com.moneykeeper.feature.auth.state

sealed interface AuthState {
    data object Uninitialized : AuthState
    /** v1 credentials exist but v2 (Keystore + PIN) migration hasn't run yet. */
    data object PinSetupRequired : AuthState
    data object Locked : AuthState
    data object Unlocked : AuthState
    data class DataCorrupted(val message: String) : AuthState
}
