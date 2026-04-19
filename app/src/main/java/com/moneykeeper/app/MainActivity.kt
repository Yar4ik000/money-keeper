package com.moneykeeper.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.moneykeeper.app.navigation.MoneyKeeperNavHost
import com.moneykeeper.core.ui.theme.MoneyKeeperTheme
import com.moneykeeper.core.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint

/**
 * Временный скелет. Полная реализация §1.8 с `pendingIntent` + auth-гейтингом
 * (`AuthState` → `SetupPassword`/`Unlock`/`NavHost`) придёт вместе с §10. Сейчас
 * `MoneyKeeperNavHost` компонуется безусловно, чтобы APK собирался и запускался
 * до реализации `:feature:auth`. `ThemeMode.SYSTEM` — жёстко до §9.10, где режим
 * придёт из `SettingsRepository`.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MoneyKeeperTheme(themeMode = ThemeMode.SYSTEM) {
                MoneyKeeperNavHost()
            }
        }
    }
}
