package com.moneykeeper.core.ui.util

import com.moneykeeper.core.ui.locale.AppLocale
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

private fun localeFor(currency: String): Locale = when (currency) {
    "RUB" -> Locale.forLanguageTag("ru-RU")
    "USD" -> Locale.forLanguageTag("en-US")
    "EUR" -> Locale.forLanguageTag("de-DE")
    "GBP" -> Locale.forLanguageTag("en-GB")
    "CNY" -> Locale.forLanguageTag("zh-CN")
    "KZT" -> Locale.forLanguageTag("kk-KZ")
    else  -> AppLocale.current()
}

fun BigDecimal.formatAsCurrency(currency: String = "RUB"): String =
    NumberFormat.getCurrencyInstance(localeFor(currency)).format(this)

fun currencySymbol(currency: String): String =
    Currency.getInstance(currency).getSymbol(localeFor(currency))
