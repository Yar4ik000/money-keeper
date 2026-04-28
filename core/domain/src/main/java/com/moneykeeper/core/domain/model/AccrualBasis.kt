package com.moneykeeper.core.domain.model

enum class AccrualBasis {
    /** Interest computed on the actual daily balance — accrues proportionally to real balance each day. */
    DAILY,
    /** Interest computed on the balance at the START of each period — mid-period deposits don't count until next period. */
    PERIOD_START,
}
