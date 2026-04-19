package com.moneykeeper.core.ui.theme

/**
 * Режим темы приложения (§1.10). Хранится в `SettingsRepository` (§9.2),
 * читается в корне `MainActivity.setContent` и передаётся в [MoneyKeeperTheme].
 */
enum class ThemeMode { LIGHT, DARK, SYSTEM }
