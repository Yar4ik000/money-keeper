package com.moneykeeper.feature.settings.domain

fun String.csvEscape(): String {
    val needsQuoting = any { it == ';' || it == '"' || it == '\n' || it == '\r' }
    return if (!needsQuoting) this
    else buildString(length + 2) {
        append('"')
        this@csvEscape.forEach { c ->
            if (c == '"') append('"')
            append(c)
        }
        append('"')
    }
}
