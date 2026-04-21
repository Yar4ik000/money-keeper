package com.moneykeeper.feature.settings.ui.backup

internal object BackupPasswordValidator {

    enum class Error { TOO_SHORT, NO_UPPER, NO_LOWER, NO_DIGIT, MISMATCH }

    fun validate(password: String, confirm: String): Error? = when {
        password.length < 8               -> Error.TOO_SHORT
        !password.any { it.isUpperCase() } -> Error.NO_UPPER
        !password.any { it.isLowerCase() } -> Error.NO_LOWER
        !password.any { it.isDigit() }     -> Error.NO_DIGIT
        password != confirm                -> Error.MISMATCH
        else                               -> null
    }
}
