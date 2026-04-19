package com.moneykeeper.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Стартовая палитра — стандартная M3 с акцентом зелёного (финансы). Детализация при реализации UI.
private val Green80 = Color(0xFFA5D6A7)
private val Green40 = Color(0xFF2E7D32)

internal val LightColorScheme = lightColorScheme(
    primary = Green40
)

internal val DarkColorScheme = darkColorScheme(
    primary = Green80
)
