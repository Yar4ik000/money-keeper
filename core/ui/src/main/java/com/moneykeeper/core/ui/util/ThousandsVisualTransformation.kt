package com.moneykeeper.core.ui.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Adds a narrow no-break space (U+202F) as thousands separator in the integer part.
 * Handles an optional decimal point; supports "." as decimal separator.
 * The underlying value is stored unchanged (no separators) — only the display changes.
 */
object ThousandsVisualTransformation : VisualTransformation {

    private const val SEP = '\u202F' // narrow no-break space

    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        if (original.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val dotIndex = original.indexOf('.')
        val intPart = if (dotIndex >= 0) original.substring(0, dotIndex) else original
        val fracPart = if (dotIndex >= 0) original.substring(dotIndex + 1) else ""
        val hasDot = dotIndex >= 0
        val n = intPart.length

        // Build transformed string and origToTrans mapping simultaneously
        val sb = StringBuilder()
        val origToTrans = IntArray(original.length + 1)

        for (i in intPart.indices) {
            val remaining = n - i
            if (i > 0 && remaining % 3 == 0) sb.append(SEP)
            origToTrans[i] = sb.length
            sb.append(intPart[i])
        }

        var origPos = n
        if (hasDot) {
            origToTrans[origPos++] = sb.length
            sb.append('.')
            for (c in fracPart) {
                origToTrans[origPos++] = sb.length
                sb.append(c)
            }
        }
        origToTrans[origPos] = sb.length // end cursor

        val transformed = sb.toString()

        // Build transToOrig: for separator chars map to same orig as next char
        val transToOrig = IntArray(transformed.length + 1) { -1 }
        for (o in 0..original.length) {
            transToOrig[origToTrans[o]] = o
        }
        // Fill gaps (separator positions) by scanning forward
        for (t in transformed.indices) {
            if (transToOrig[t] < 0) transToOrig[t] = transToOrig[t + 1].coerceAtLeast(0)
        }
        transToOrig[transformed.length] = original.length

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) =
                origToTrans[offset.coerceIn(0, original.length)]
            override fun transformedToOriginal(offset: Int) =
                transToOrig[offset.coerceIn(0, transformed.length)]
        }

        return TransformedText(AnnotatedString(transformed), mapping)
    }
}
