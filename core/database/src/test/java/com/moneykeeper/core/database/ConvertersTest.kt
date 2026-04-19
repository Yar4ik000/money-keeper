package com.moneykeeper.core.database

import com.moneykeeper.core.database.converter.Converters
import com.moneykeeper.core.domain.model.AccountType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ConvertersTest {

    private val c = Converters()

    @Test
    fun bigDecimal_roundtrip_preserves_precision() {
        val original = BigDecimal("12345.67890")
        assertEquals(original, c.toBigDecimal(c.fromBigDecimal(original)))
    }

    @Test
    fun bigDecimal_plainString_no_scientific_notation() {
        // toPlainString гарантирует "150000" вместо "1.5E+5"
        assertEquals("150000", c.fromBigDecimal(BigDecimal("1.5E+5")))
    }

    @Test
    fun localDate_roundtrip_iso_format() {
        val original = LocalDate.of(2026, 4, 19)
        assertEquals("2026-04-19", c.fromLocalDate(original))
        assertEquals(original, c.toLocalDate("2026-04-19"))
    }

    @Test
    fun accountType_enum_roundtrip() {
        AccountType.entries.forEach { type ->
            assertEquals(type, c.toAccountType(c.fromAccountType(type)))
        }
    }

    @Test
    fun nulls_pass_through_nullable_converters() {
        assertNull(c.fromLocalDate(null))
        assertNull(c.toLocalDate(null))
        assertNull(c.fromBigDecimal(null))
        assertNull(c.toBigDecimal(null))
        assertNull(c.fromCapPeriod(null))
        assertNull(c.toCapPeriod(null))
    }
}
