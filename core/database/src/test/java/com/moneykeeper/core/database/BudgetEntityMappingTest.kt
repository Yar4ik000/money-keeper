package com.moneykeeper.core.database

import com.moneykeeper.core.database.entity.BudgetEntity
import com.moneykeeper.core.database.entity.toDomain
import com.moneykeeper.core.database.entity.toEntity
import com.moneykeeper.core.domain.model.Budget
import com.moneykeeper.core.domain.model.BudgetPeriod
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

/**
 * Pure-JVM unit tests for BudgetEntity ↔ Budget domain mapping.
 *
 * Key invariants:
 *  - null CSV string  → emptySet() in domain (null = all categories/accounts)
 *  - emptySet() domain → null CSV in entity (round-trip stable)
 *  - non-empty set ↔ comma-separated long string
 */
class BudgetEntityMappingTest {

    private fun entity(
        id: Long = 0L,
        categoryIds: String? = null,
        accountIds: String? = null,
        amount: BigDecimal = BigDecimal("5000"),
        period: BudgetPeriod = BudgetPeriod.MONTHLY,
        currency: String = "RUB",
        warning: Int? = null,
        critical: Int? = null,
    ) = BudgetEntity(
        id = id, categoryIds = categoryIds, accountIds = accountIds,
        amount = amount, period = period, currency = currency,
        warningThreshold = warning, criticalThreshold = critical,
    )

    private fun domain(
        id: Long = 0L,
        categoryIds: Set<Long> = emptySet(),
        accountIds: Set<Long> = emptySet(),
        amount: BigDecimal = BigDecimal("5000"),
        period: BudgetPeriod = BudgetPeriod.MONTHLY,
        currency: String = "RUB",
        warning: Int? = null,
        critical: Int? = null,
    ) = Budget(
        id = id, categoryIds = categoryIds, accountIds = accountIds,
        amount = amount, period = period, currency = currency,
        warningThreshold = warning, criticalThreshold = critical,
    )

    // ── toDomain: categoryIds ─────────────────────────────────────────────────

    @Test
    fun toDomain_nullCategoryIds_yieldsEmptySet() {
        assertEquals(emptySet<Long>(), entity(categoryIds = null).toDomain().categoryIds)
    }

    @Test
    fun toDomain_singleCategoryId_yieldsSetOfOne() {
        assertEquals(setOf(42L), entity(categoryIds = "42").toDomain().categoryIds)
    }

    @Test
    fun toDomain_multipleCategoryIds_yieldsFullSet() {
        assertEquals(setOf(1L, 2L, 3L), entity(categoryIds = "1,2,3").toDomain().categoryIds)
    }

    @Test
    fun toDomain_unorderedIds_preservedAsSet() {
        val result = entity(categoryIds = "7,3,11,5").toDomain().categoryIds
        assertEquals(setOf(7L, 3L, 11L, 5L), result)
    }

    // ── toDomain: accountIds ──────────────────────────────────────────────────

    @Test
    fun toDomain_nullAccountIds_yieldsEmptySet() {
        assertEquals(emptySet<Long>(), entity(accountIds = null).toDomain().accountIds)
    }

    @Test
    fun toDomain_multipleAccountIds_yieldsFullSet() {
        assertEquals(setOf(10L, 20L, 30L), entity(accountIds = "10,20,30").toDomain().accountIds)
    }

    // ── toDomain: scalar fields ───────────────────────────────────────────────

    @Test
    fun toDomain_idAmountPeriodCurrency_preserved() {
        val e = entity(id = 99L, amount = BigDecimal("1234.56"),
            period = BudgetPeriod.WEEKLY, currency = "USD")
        val d = e.toDomain()
        assertEquals(99L, d.id)
        assertEquals(0, BigDecimal("1234.56").compareTo(d.amount))
        assertEquals(BudgetPeriod.WEEKLY, d.period)
        assertEquals("USD", d.currency)
    }

    @Test
    fun toDomain_thresholds_preserved() {
        val d = entity(warning = 75, critical = 95).toDomain()
        assertEquals(75, d.warningThreshold)
        assertEquals(95, d.criticalThreshold)
    }

    @Test
    fun toDomain_nullThresholds_remainNull() {
        val d = entity(warning = null, critical = null).toDomain()
        assertNull(d.warningThreshold)
        assertNull(d.criticalThreshold)
    }

    // ── toEntity: categoryIds ─────────────────────────────────────────────────

    @Test
    fun toEntity_emptyCategoryIds_yieldsNullCsv() {
        assertNull(domain(categoryIds = emptySet()).toEntity().categoryIds)
    }

    @Test
    fun toEntity_singleCategoryId_yieldsSingleString() {
        assertEquals("42", domain(categoryIds = setOf(42L)).toEntity().categoryIds)
    }

    @Test
    fun toEntity_multipleCategoryIds_yieldsCsv() {
        val csv = domain(categoryIds = setOf(1L, 2L, 3L)).toEntity().categoryIds!!
        // Order may vary since Set is unordered — check both ways
        val parts = csv.split(",").map { it.toLong() }.toSet()
        assertEquals(setOf(1L, 2L, 3L), parts)
    }

    // ── toEntity: accountIds ──────────────────────────────────────────────────

    @Test
    fun toEntity_emptyAccountIds_yieldsNullCsv() {
        assertNull(domain(accountIds = emptySet()).toEntity().accountIds)
    }

    @Test
    fun toEntity_multipleAccountIds_yieldsCsv() {
        val csv = domain(accountIds = setOf(10L, 20L)).toEntity().accountIds!!
        val parts = csv.split(",").map { it.toLong() }.toSet()
        assertEquals(setOf(10L, 20L), parts)
    }

    // ── full roundtrips ───────────────────────────────────────────────────────

    @Test
    fun roundtrip_allCategories_nullPreserved() {
        val original = entity(categoryIds = null, accountIds = null)
        val roundtripped = original.toDomain().toEntity().copy(id = original.id)
        assertNull(roundtripped.categoryIds)
        assertNull(roundtripped.accountIds)
    }

    @Test
    fun roundtrip_specificCategories_setPreserved() {
        val original = entity(categoryIds = "5,10,15", accountIds = "1,2")
        val domain = original.toDomain()
        val backToEntity = domain.toEntity()

        assertEquals(setOf(5L, 10L, 15L),
            backToEntity.categoryIds!!.split(",").map { it.toLong() }.toSet())
        assertEquals(setOf(1L, 2L),
            backToEntity.accountIds!!.split(",").map { it.toLong() }.toSet())
    }

    @Test
    fun roundtrip_domainToEntity_emptySetStaysNull() {
        val original = domain(categoryIds = emptySet(), accountIds = emptySet())
        val entity = original.toEntity()
        val backToDomain = entity.toDomain()
        assertEquals(emptySet<Long>(), backToDomain.categoryIds)
        assertEquals(emptySet<Long>(), backToDomain.accountIds)
    }
}
