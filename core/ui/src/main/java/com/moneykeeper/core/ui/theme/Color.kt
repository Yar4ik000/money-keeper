package com.moneykeeper.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val Green80 = Color(0xFF81C784)   // lighter green, readable on dark surfaces
private val Green40 = Color(0xFF2E7D32)   // forest green primary

internal val LightColorScheme = lightColorScheme(
    primary            = Green40,
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF003909),
    secondary          = Color(0xFF52634F),
    background         = Color(0xFFF8FAF8),   // near-white, neutral (no green tint)
    onBackground       = Color(0xFF1A1C1A),
    surface            = Color(0xFFFFFFFF),
    onSurface          = Color(0xFF1A1C1A),
    surfaceVariant     = Color(0xFFDDE5DA),
    onSurfaceVariant   = Color(0xFF424940),
    outline            = Color(0xFF727970),
)

internal val DarkColorScheme = darkColorScheme(
    primary            = Green80,
    onPrimary          = Color(0xFF003910),
    primaryContainer   = Color(0xFF005319),
    onPrimaryContainer = Color(0xFFA5D6A7),
    secondary          = Color(0xFFB8CCB3),
    background         = Color(0xFF121212),   // standard dark neutral (no swamp tint)
    onBackground       = Color(0xFFE2E3DD),
    surface            = Color(0xFF1E201E),
    onSurface          = Color(0xFFE2E3DD),
    surfaceVariant     = Color(0xFF424940),
    onSurfaceVariant   = Color(0xFFC1C9BE),
    outline            = Color(0xFF8B9389),
)
