package com.moneykeeper.core.ui.util

import com.moneykeeper.core.ui.locale.AppLocale
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

fun BigDecimal.formatAsCurrency(currency: String = "RUB"): String {
    val format = NumberFormat.getCurrencyInstance(
        when (currency) {
            "RUB" -> Locale.forLanguageTag("ru-RU")
            "USD" -> Locale.forLanguageTag("en-US")
            "EUR" -> Locale.forLanguageTag("de-DE")
            else  -> AppLocale.current()
        }
    )
    return format.format(this)
}
