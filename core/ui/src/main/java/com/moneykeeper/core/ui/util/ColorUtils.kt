package com.moneykeeper.core.ui.util

import androidx.compose.ui.graphics.Color

fun parseHexColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex"))
} catch (_: IllegalArgumentException) {
    Color.Gray
}
