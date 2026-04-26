package com.moneykeeper.core.ui.util

import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class CurrencyFormatterTest {

    @Test
    fun `RUB uses ruble sign`() {
        val result = BigDecimal("100").formatAsCurrency("RUB")
        assertTrue("Expected ₽ in '$result'", result.contains("₽") || result.contains("руб"))
    }

    @Test
    fun `USD uses dollar sign`() {
        val result = BigDecimal("100").formatAsCurrency("USD")
        assertTrue("Expected $ in '$result'", result.contains("$"))
    }

    @Test
    fun `EUR uses euro sign`() {
        val result = BigDecimal("100").formatAsCurrency("EUR")
        assertTrue("Expected € in '$result'", result.contains("€"))
    }

    @Test
    fun `GBP uses pound sign`() {
        val result = BigDecimal("100").formatAsCurrency("GBP")
        assertTrue("Expected £ in '$result'", result.contains("£"))
    }

    @Test
    fun `CNY uses yuan sign`() {
        val result = BigDecimal("100").formatAsCurrency("CNY")
        assertTrue("Expected ¥ in '$result'", result.contains("¥"))
    }

    @Test
    fun `KZT uses tenge sign`() {
        val result = BigDecimal("100").formatAsCurrency("KZT")
        assertTrue("Expected ₸ in '$result'", result.contains("₸"))
    }

    @Test
    fun `currencySymbol returns correct symbols`() {
        assertTrue(currencySymbol("RUB").let { it.contains("₽") || it.contains("руб") })
        assertTrue(currencySymbol("USD").contains("$"))
        assertTrue(currencySymbol("EUR").contains("€"))
        assertTrue(currencySymbol("GBP").contains("£"))
        assertTrue(currencySymbol("KZT").contains("₸"))
    }
}
