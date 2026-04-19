package com.moneykeeper.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneykeeper.app.navigation.MoneyKeeperNavHost
import com.moneykeeper.core.ui.theme.MoneyKeeperTheme
import com.moneykeeper.core.ui.theme.ThemeMode
import com.moneykeeper.feature.auth.state.AuthGateViewModel
import com.moneykeeper.feature.auth.state.AuthState
import com.moneykeeper.feature.auth.ui.corrupted.DataCorruptedScreen
import com.moneykeeper.feature.auth.ui.setup.SetupPasswordScreen
import com.moneykeeper.feature.auth.ui.unlock.UnlockScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val authGateViewModel: AuthGateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MoneyKeeperTheme(themeMode = ThemeMode.SYSTEM) {
                val authState by authGateViewModel.state.collectAsStateWithLifecycle()

                when (authState) {
                    AuthState.Uninitialized -> SetupPasswordScreen(
                        onPasswordSet = authGateViewModel::onPasswordSet,
                    )
                    AuthState.Locked -> UnlockScreen(
                        onUnlocked  = authGateViewModel::onUnlocked,
                        onCorrupted = authGateViewModel::onCorrupted,
                    )
                    AuthState.Unlocked -> MoneyKeeperNavHost()
                    is AuthState.DataCorrupted -> DataCorruptedScreen(
                        message = (authState as AuthState.DataCorrupted).message,
                        onWiped = authGateViewModel::onWiped,
                    )
                }
            }
        }
    }
}
