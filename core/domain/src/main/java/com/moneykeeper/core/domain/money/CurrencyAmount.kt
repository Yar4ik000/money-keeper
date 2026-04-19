package com.moneykeeper.core.domain.money

import java.math.BigDecimal

data class CurrencyAmount(val currency: String, val amount: BigDecimal) {
    companion object {
        fun zero(currency: String) = CurrencyAmount(currency, BigDecimal.ZERO)
    }
}

@JvmInline
value class MultiCurrencyTotal(val entries: List<CurrencyAmount>) {
    val isSingleCurrency: Boolean get() = entries.size <= 1
    fun forCurrency(code: String): BigDecimal =
        entries.firstOrNull { it.currency == code }?.amount ?: BigDecimal.ZERO
}
