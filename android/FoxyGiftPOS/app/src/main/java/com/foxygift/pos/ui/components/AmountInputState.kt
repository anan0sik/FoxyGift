package com.foxygift.pos.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.util.Locale

class AmountInputState(initialText: String = "0.00") {
    var rawText by mutableStateOf(initialText)
        private set

    private var hasCustomInput = false

    fun onDigit(digit: String) {
        if (!hasCustomInput || rawText == "0" || rawText == "0.00" || rawText == "0.0") {
            rawText = digit
            hasCustomInput = true
            return
        }

        val dotIdx = rawText.indexOf('.')
        if (dotIdx != -1) {
            val decimals = rawText.length - dotIdx - 1
            if (decimals < 2) {
                rawText += digit
            }
        } else {
            if (rawText.length < 5) {
                rawText += digit
            }
        }
    }

    fun onDot() {
        if (!hasCustomInput) {
            rawText = "0."
            hasCustomInput = true
            return
        }
        if (!rawText.contains('.')) {
            rawText = if (rawText.isBlank()) "0." else "$rawText."
        }
    }

    fun onDelete() {
        if (!hasCustomInput || rawText.length <= 1) {
            rawText = "0.00"
            hasCustomInput = false
        } else {
            rawText = rawText.dropLast(1)
            if (rawText.isEmpty() || rawText == "0.") {
                rawText = "0.00"
                hasCustomInput = false
            }
        }
    }

    fun setQuickAmount(euros: String) {
        rawText = "$euros.00"
        hasCustomInput = true
    }

    val amountCents: Long
        get() {
            val clean = rawText.replace(',', '.').trim()
            val parts = clean.split('.')
            val whole = parts[0].toLongOrNull() ?: 0L
            val frac = if (parts.size > 1) {
                parts[1].padEnd(2, '0').take(2).toLongOrNull() ?: 0L
            } else 0L
            return whole * 100L + frac
        }

    val displayAmount: String
        get() {
            if (!hasCustomInput && rawText == "0.00") return "0.00"
            val cents = amountCents
            val whole = cents / 100
            val frac = (cents % 100).toString().padStart(2, '0')
            return if (rawText.endsWith(".")) {
                "$whole."
            } else if (rawText.contains('.') && rawText.substringAfter('.').length == 1) {
                "$whole.${rawText.substringAfter('.')}"
            } else {
                "$whole.$frac"
            }
        }

    fun formattedWithCurrency(currency: String): String {
        return "$displayAmount $currency"
    }
}

@Composable
fun rememberAmountInputState(initialText: String = "0.00"): AmountInputState {
    return remember { AmountInputState(initialText) }
}
