package com.moneykeeper.core.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

val ACCOUNT_ICON_OPTIONS: List<Pair<String, ImageVector>> = listOf(
    "bank"       to Icons.Default.AccountBalance,
    "wallet"     to Icons.Default.AccountBalanceWallet,
    "card"       to Icons.Default.CreditCard,
    "cash"       to Icons.Default.Payments,
    "savings"    to Icons.Default.Savings,
    "trending"   to Icons.Default.TrendingUp,
    "chart"      to Icons.Default.ShowChart,
    "home"       to Icons.Default.Home,
    "star"       to Icons.Default.Star,
    "work"       to Icons.Default.Work,
    "business"   to Icons.Default.Business,
    "store"      to Icons.Default.Storefront,
    "shopping"   to Icons.Default.ShoppingCart,
    "phone"      to Icons.Default.Smartphone,
    "atm"        to Icons.Default.LocalAtm,
    "safe"       to Icons.Default.Lock,
    "family"     to Icons.Default.Group,
    "trophy"     to Icons.Default.EmojiEvents,
)

fun accountIconVector(iconName: String): ImageVector =
    ACCOUNT_ICON_OPTIONS.firstOrNull { it.first == iconName }?.second
        ?: Icons.Default.AccountBalance
