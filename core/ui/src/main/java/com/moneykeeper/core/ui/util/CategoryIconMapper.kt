package com.moneykeeper.core.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nature
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

val CATEGORY_ICON_OPTIONS: List<Pair<String, ImageVector>> = listOf(
    "other"         to Icons.Default.Category,
    "food"          to Icons.Default.Restaurant,
    "transport"     to Icons.Default.DirectionsCar,
    "shopping"      to Icons.Default.ShoppingCart,
    "entertainment" to Icons.Default.SportsEsports,
    "health"        to Icons.Default.LocalHospital,
    "home"          to Icons.Default.Home,
    "education"     to Icons.Default.School,
    "travel"        to Icons.Default.Flight,
    "work"          to Icons.Default.Work,
    "gift"          to Icons.Default.CardGiftcard,
    "coffee"        to Icons.Default.LocalCafe,
    "sport"         to Icons.Default.FitnessCenter,
    "pets"          to Icons.Default.Pets,
    "bills"         to Icons.Default.Receipt,
    "savings"       to Icons.Default.Savings,
    "bank"          to Icons.Default.AccountBalance,
    "star"          to Icons.Default.Star,
    "bar"           to Icons.Default.LocalBar,
    "clothes"       to Icons.Default.Checkroom,
    "fuel"          to Icons.Default.LocalGasStation,
    "kids"          to Icons.Default.ChildCare,
    "music"         to Icons.Default.MusicNote,
    "cinema"        to Icons.Default.Theaters,
    "beauty"        to Icons.Default.Spa,
    "pharmacy"      to Icons.Default.LocalPharmacy,
    "internet"      to Icons.Default.Wifi,
    "nature"        to Icons.Default.Nature,
    "charity"       to Icons.Default.Favorite,
    "repair"        to Icons.Default.Build,
    "subscriptions" to Icons.Default.Subscriptions,
    "moto"          to Icons.Default.TwoWheeler,
)

fun categoryIconVector(iconName: String): ImageVector =
    CATEGORY_ICON_OPTIONS.firstOrNull { it.first == iconName }?.second
        ?: Icons.Default.Category
