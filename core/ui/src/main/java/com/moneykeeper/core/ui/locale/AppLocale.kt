package com.moneykeeper.core.ui.locale

import java.util.Locale

/**
 * Единая точка выбора локали для всех `DateTimeFormatter.withLocale(...)` и
 * `NumberFormat.getInstance(...)` (§1.13). В v1 всегда `ru`; при добавлении
 * второго языка — читать из `SettingsRepository`.
 */
object AppLocale {
    fun current(): Locale = Locale.forLanguageTag("ru")
}
