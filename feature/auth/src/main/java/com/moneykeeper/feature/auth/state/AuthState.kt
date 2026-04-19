package com.moneykeeper.feature.auth.state

sealed interface AuthState {
    data object Uninitialized : AuthState
    data object Locked : AuthState
    data object Unlocked : AuthState
    data class DataCorrupted(val message: String) : AuthState
}
