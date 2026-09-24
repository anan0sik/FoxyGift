package com.foxygift.pos.core.nfc

import java.math.BigInteger

/**
 * Converts a raw 7-byte NTAG factory UID byte array into a strictly decimal string.
 *
 * The hex UID (e.g., 04:A2:3B:01:23:45:67) is NEVER exposed outside this module.
 * All card references use the decimal representation.
 *
 * Display format example: "1304 2898 7123 4560"
 */
object DecimalUidConverter {

    /**
     * Converts a 7-byte UID array to a decimal string.
     * Uses BigInteger to handle the full 56-bit unsigned integer range.
     */
    fun toDecimalString(uidBytes: ByteArray): String {
        require(uidBytes.isNotEmpty()) { "UID byte array must not be empty" }
        return BigInteger(1, uidBytes).toString(10)
    }

    /**
     * Formats a decimal card number string for display with spaces every 4 digits
     * reading from right-to-left (like a credit card number).
     *
     * e.g., "1304289871234560" → "1304 2898 7123 4560"
     */
    fun formatForDisplay(decimalString: String): String {
        val digits = decimalString.filter { it.isDigit() }
        return buildString {
            var i = 0
            val remainder = digits.length % 4
            if (remainder != 0) {
                append(digits.substring(0, remainder))
                i = remainder
            }
            while (i < digits.length) {
                if (isNotEmpty()) append(' ')
                append(digits.substring(i, i + 4))
                i += 4
            }
        }
    }

    /**
     * Strips display formatting spaces and returns raw decimal digits.
     */
    fun normalize(displayString: String): String =
        displayString.filter { it.isDigit() }
}
