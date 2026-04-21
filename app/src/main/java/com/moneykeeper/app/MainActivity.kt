package com.moneykeeper.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneykeeper.app.navigation.MoneyKeeperNavHost
import com.moneykeeper.app.navigation.Screen
import com.moneykeeper.core.ui.theme.MoneyKeeperTheme
import com.moneykeeper.core.ui.theme.ThemeMode
import com.moneykeeper.feature.auth.state.AuthGateViewModel
import com.moneykeeper.feature.auth.state.AuthState
import com.moneykeeper.feature.auth.ui.corrupted.DataCorruptedScreen
import com.moneykeeper.feature.auth.ui.setup.SetupPasswordScreen
import com.moneykeeper.feature.auth.ui.unlock.UnlockScreen
import com.moneykeeper.feature.settings.ui.onboarding.OnboardingScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val authGateViewModel: AuthGateViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            val settings by mainViewModel.settings.collectAsStateWithLifecycle()
            LaunchedEffect(settings.allowScreenshots) {
                if (settings.allowScreenshots) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE,
                    )
                }
            }
            val themeMode = when (settings.themeMode) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }

            MoneyKeeperTheme(themeMode = themeMode) {
                val authState by authGateViewModel.state.collectAsStateWithLifecycle()

                when (authState) {
                    AuthState.Uninitialized -> SetupPasswordScreen(
                        onPasswordSet = authGateViewModel::onPasswordSet,
                    )
                    AuthState.Locked -> UnlockScreen(
                        onUnlocked  = authGateViewModel::onUnlocked,
                        onCorrupted = authGateViewModel::onCorrupted,
                    )
                    AuthState.Unlocked -> {
                        if (!settings.onboardingCompleted) {
                            OnboardingScreen(onFinished = { mainViewModel.completeOnboarding() })
                        } else {
                            MoneyKeeperNavHost()
                        }
                    }
                    is AuthState.DataCorrupted -> DataCorruptedScreen(
                        message = (authState as AuthState.DataCorrupted).message,
                        onWiped = authGateViewModel::onWiped,
                    )
                }
            }
        }
    }
}
