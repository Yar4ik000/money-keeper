package com.moneykeeper.feature.transactions.domain

import com.moneykeeper.core.domain.model.RecurringRule

sealed interface RecurringUpdate {
    data object Keep : RecurringUpdate
    data class Set(val rule: RecurringRule) : RecurringUpdate
    /** Detach this transaction from its rule; keep the rule alive for other occurrences. */
    data object Clear : RecurringUpdate
    /** Detach this transaction AND delete the rule, stopping all future generation. */
    data class StopSeries(val ruleId: Long) : RecurringUpdate
}
