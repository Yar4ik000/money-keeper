package com.moneykeeper.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.moneykeeper.core.ui.theme.MoneyKeeperTheme
import com.moneykeeper.core.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint

/**
 * Временный скелет. Полная реализация с `pendingIntent` + auth-гейтингом `AuthState` — в §1.8/§10.
 * До тех пор, пока `:feature:auth` не реализован, показываем заглушку, чтобы модуль собирался
 * и на устройство ставилось работающее (пусть и пустое) приложение.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MoneyKeeperTheme(themeMode = ThemeMode.SYSTEM) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text("Money Keeper — skeleton")
                    }
                }
            }
        }
    }
}
