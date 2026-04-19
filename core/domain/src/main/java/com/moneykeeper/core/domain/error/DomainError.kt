package com.moneykeeper.core.domain.error

sealed class DomainError : Exception() {
    data object InvalidDepositAccountType : DomainError()
    data object DepositAccountNotTransferable : DomainError()
    data object TypeChangeBlocked : DomainError()
    data object CapitalizationPeriodRequired : DomainError()
}
