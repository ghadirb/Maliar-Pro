package com.maliar.pro.utils

import java.util.Locale
import kotlin.math.abs

/**
 * A single, locale-safe money formatter for the whole app.
 *
 * Every screen used to call its own `String.format("%,.0f تومان", amount)` with no
 * explicit Locale. That relies on the *device's* default Locale for both digit
 * grouping and the negative-number pattern - and on some Persian (fa-IR) ICU
 * configurations, formatting a negative Double with "%,.0f" silently drops the
 * minus sign (a known ICU/Persian-locale quirk on some Android builds), so a
 * negative balance rendered identically to a positive one - exactly the "expenses
 * were higher than income but the balance didn't show as negative" bug reported.
 *
 * Fix: always format the digits with Locale.US (guaranteed correct, Latin-numeral
 * grouping) and prepend the sign ourselves, so the result no longer depends on the
 * device's configured locale at all.
 */
object CurrencyFormatter {
    fun format(amount: Double, suffix: String = "تومان"): String {
        val sign = if (amount < 0) "-" else ""
        val magnitude = String.format(Locale.US, "%,.0f", abs(amount))
        return if (suffix.isEmpty()) "$sign$magnitude" else "$sign$magnitude $suffix"
    }

    /** Formats a plain (non-currency) quantity, e.g. grams of gold: grouped digits, up to
     *  two decimal places, trailing zeros/decimal point trimmed (so "2.00" -> "2" but
     *  "2.50" -> "2.5"). No suffix and no currency assumptions. */
    fun formatPlainNumber(amount: Double): String {
        val sign = if (amount < 0) "-" else ""
        val magnitude = String.format(Locale.US, "%,.2f", abs(amount))
            .trimEnd('0').trimEnd('.')
        return "$sign$magnitude"
    }
}
