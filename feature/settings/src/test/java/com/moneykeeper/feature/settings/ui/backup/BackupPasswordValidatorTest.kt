package com.moneykeeper.feature.settings.ui.backup

import com.moneykeeper.feature.settings.ui.backup.BackupPasswordValidator.Error
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupPasswordValidatorTest {

    private fun validate(p: String, c: String = p) = BackupPasswordValidator.validate(p, c)

    @Test fun tooShort_returns_TOO_SHORT() {
        assertEquals(Error.TOO_SHORT, validate("Abc1xyz"))   // 7 chars
    }

    @Test fun exactly7Chars_is_tooShort() {
        assertEquals(Error.TOO_SHORT, validate("Abcde1X"))   // 7 chars
    }

    @Test fun exactly8Chars_passesLengthCheck() {
        // length OK, has upper+lower+digit → valid
        assertNull(validate("Abcde1Xy"))
    }

    @Test fun noUppercase_returns_NO_UPPER() {
        assertEquals(Error.NO_UPPER, validate("abcdefg1"))   // 8 chars, lower+digit, no upper
    }

    @Test fun noLowercase_returns_NO_LOWER() {
        assertEquals(Error.NO_LOWER, validate("ABCDEFG1"))   // 8 chars, upper+digit, no lower
    }

    @Test fun noDigit_returns_NO_DIGIT() {
        assertEquals(Error.NO_DIGIT, validate("Abcdefgh"))   // 8 chars, upper+lower, no digit
    }

    @Test fun mismatch_returns_MISMATCH() {
        assertEquals(Error.MISMATCH, validate("ValidPass1", "DifferentP1"))
    }

    @Test fun validPassword_matching_returns_null() {
        assertNull(validate("ValidPass1", "ValidPass1"))
    }

    @Test fun validPassword_withSpecialChars_returns_null() {
        assertNull(validate("P@ssword1!", "P@ssword1!"))
    }

    @Test fun priority_tooShort_beforeNoUpper() {
        // "a1" is both too short and has no uppercase — TOO_SHORT wins
        assertEquals(Error.TOO_SHORT, validate("a1", "a1"))
    }

    @Test fun priority_noUpper_beforeNoLower() {
        // All lowercase + digit — NO_UPPER reported first
        assertEquals(Error.NO_UPPER, validate("abcdefg1"))
    }

    @Test fun priority_noLower_beforeNoDigit() {
        // All uppercase + has digit — NO_LOWER reported before NO_DIGIT
        assertEquals(Error.NO_LOWER, validate("ABCDEFG1"))
    }

    @Test fun priority_noDigit_beforeMismatch() {
        // No digit, and passwords differ — NO_DIGIT reported first
        assertEquals(Error.NO_DIGIT, validate("Abcdefgh", "Xbcdefgh"))
    }

    @Test fun emptyPassword_returns_TOO_SHORT() {
        assertEquals(Error.TOO_SHORT, validate("", ""))
    }
}
