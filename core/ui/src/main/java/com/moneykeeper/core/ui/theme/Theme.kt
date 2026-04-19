package com.moneykeeper.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Корневая тема приложения (§1.10). Оборачивает **всю** `setContent`-ветку, включая
 * lock-экраны: это гарантирует, что SetupPassword/Unlock тоже рендерятся в правильной теме.
 *
 * `ThemeMode.SYSTEM` маппится на [isSystemInDarkTheme] — Compose сам рекомпонует тему при
 * смене системного режима, никаких ручных обработчиков не нужно.
 */
@Composable
fun MoneyKeeperTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT  -> false
        ThemeMode.DARK   -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
