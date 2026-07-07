package com.maliar.pro.utils

data class ParsedBankSms(
    val isDeposit: Boolean,
    val amountToman: Double,
    val balanceToman: Double?,
    val bankName: String,
    val lastDigits: String,
    val suggestedTitle: String
)

data class BankSmsSignalAnalysis(
    val score: Double,
    val definitelyNotBank: Boolean,
    val shouldAskOnline: Boolean
)

object BankSmsParser {

    private const val RIAL = "\u0631\u06cc\u0627\u0644"
    private const val TOMAN = "\u062a\u0648\u0645\u0627\u0646"
    private const val BANK_UNKNOWN = "\u0628\u0627\u0646\u06a9 \u0646\u0627\u0645\u0634\u062e\u0635"
    private const val WEPOD_BANK = "\u0648\u06cc\u067e\u0627\u062f (\u067e\u0627\u0633\u0627\u0631\u06af\u0627\u062f)"

    private val DEPOSIT_WORDS = listOf(
        "\u0648\u0627\u0631\u06cc\u0632",
        "\u0648\u0627\u0631\u06cc\u0632\u06cc",
        "\u0628\u0633\u062a\u0627\u0646\u06a9\u0627\u0631",
        "\u0627\u0641\u0632\u0627\u06cc\u0634 \u0645\u0648\u062c\u0648\u062f\u06cc",
        "\u062f\u0631\u06cc\u0627\u0641\u062a",
        "deposit",
        "credit",
        "credited"
    )

    private val WITHDRAWAL_WORDS = listOf(
        "\u0628\u0631\u062f\u0627\u0634\u062a",
        "\u062e\u0631\u06cc\u062f",
        "\u06a9\u0633\u0631",
        "\u0627\u0646\u062a\u0642\u0627\u0644",
        "\u067e\u0631\u062f\u0627\u062e\u062a",
        "\u0628\u062f\u0647\u06a9\u0627\u0631",
        "\u067e\u0627\u06cc\u0627\u0646\u0647",
        "pos",
        "withdraw",
        "withdrawal",
        "debit",
        "debited",
        "purchase",
        "paid",
        "payment",
        "transfer"
    )

    private val BANK_SIGNAL_WORDS = DEPOSIT_WORDS + WITHDRAWAL_WORDS + listOf(
        "\u0633\u0627\u062a\u0646\u0627",
        "\u067e\u0627\u06cc\u0627",
        "\u0631\u0645\u0632 \u067e\u0648\u06cc\u0627",
        "\u0631\u0645\u0632 \u06cc\u06a9\u0628\u0627\u0631",
        "\u062a\u0631\u0627\u06a9\u0646\u0634",
        "\u0645\u0627\u0646\u062f\u0647",
        "\u0645\u0648\u062c\u0648\u062f\u06cc",
        "\u0634\u0645\u0627\u0631\u0647 \u06a9\u0627\u0631\u062a",
        "\u06a9\u0627\u0631\u062a",
        "\u062d\u0633\u0627\u0628",
        "\u0634\u0628\u0627",
        "iban",
        "pos",
        "atm",
        "transaction",
        "balance",
        "card",
        "account",
        "debit",
        "credit"
    )

    private val BANK_SENDER_HINTS = listOf(
        "bank", "bki", "bmi", "bsi", "bpi", "sepah", "mellat", "melli", "pasargad",
        "saman", "tejarat", "saderat", "parsian", "wepod", "vbank", "otp",
        "\u0628\u0627\u0646\u06a9", "\u0648\u06cc\u067e\u0627\u062f", "\u067e\u0627\u0633\u0627\u0631\u06af\u0627\u062f"
    )

    private val KNOWN_BANKS = listOf(
        "\u0645\u0644\u062a", "\u0645\u0644\u06cc", "\u0635\u0627\u062f\u0631\u0627\u062a", "\u062a\u062c\u0627\u0631\u062a",
        "\u0633\u067e\u0647", "\u06a9\u0634\u0627\u0648\u0631\u0632\u06cc", "\u0645\u0633\u06a9\u0646", "\u0631\u0641\u0627\u0647",
        "\u067e\u0627\u0631\u0633\u06cc\u0627\u0646", "\u067e\u0627\u0633\u0627\u0631\u06af\u0627\u062f", "\u0633\u0627\u0645\u0627\u0646",
        "\u0627\u0642\u062a\u0635\u0627\u062f \u0646\u0648\u06cc\u0646", "\u0633\u06cc\u0646\u0627", "\u062f\u06cc",
        "\u0634\u0647\u0631", "\u0622\u06cc\u0646\u062f\u0647", "\u0633\u0631\u0645\u0627\u06cc\u0647", "\u0648\u06cc\u067e\u0627\u062f"
    )

    private val AMOUNT_WITH_UNIT_REGEX = Regex(
        "(?:\u0645\u0628\u0644\u063a|\u0645\u0642\u062f\u0627\u0631|\u0648\u062c\u0647|amount)?\\s*[:\u003a]?\\s*([+-]?\\s*[0-9][0-9,\u060c\\s]*[0-9]|[+-]?\\s*[0-9])\\s*($RIAL|$TOMAN|\u0631|rial|rials|irr|toman)",
        RegexOption.IGNORE_CASE
    )
    private val SIGNED_AMOUNT_LINE_REGEX = Regex("(?m)^\\s*([+-])\\s*([0-9][0-9,\u060c\\s]*[0-9]|[0-9])\\s*$")
    private val BALANCE_REGEX = Regex(
        "(?:\u0645\u0648\u062c\u0648\u062f\u06cc|\u0645\u0627\u0646\u062f\u0647\\s*\u062d\u0633\u0627\u0628|\u0645\u0627\u0646\u062f\u0647|\u0628\u0627\u0642\u06cc\u0645\u0627\u0646\u062f\u0647|balance)\\D{0,16}([0-9][0-9,\u060c\\s]*[0-9]|[0-9])\\s*($RIAL|$TOMAN|\u0631|rial|rials|irr|toman)?",
        RegexOption.IGNORE_CASE
    )
    private val EXPLICIT_LAST_DIGITS_REGEX = Regex("(?:\u06a9\u0627\u0631\u062a|\u062d\u0633\u0627\u0628|\u0633\u067e\u0631\u062f\u0647|card|account)[^0-9]{0,24}([0-9]{4})(?![0-9])", RegexOption.IGNORE_CASE)
    private val LONG_NUMBER_REGEX = Regex("[0-9]{6,}")
    private val ANY_MONEY_LIKE_NUMBER_REGEX = Regex("[+-]?\\s*[0-9][0-9,\u060c\\s]{2,}[0-9]")

    fun analyze(sender: String, body: String): BankSmsSignalAnalysis {
        val normalizedBody = normalizeDigits(body)
        val normalizedSender = normalizeDigits(sender)
        var score = 0.0

        val senderLooksBank = BANK_SENDER_HINTS.any { normalizedSender.contains(it, ignoreCase = true) }
        val hasDirectionWord = DEPOSIT_WORDS.any { normalizedBody.contains(it, ignoreCase = true) } ||
            WITHDRAWAL_WORDS.any { normalizedBody.contains(it, ignoreCase = true) }
        val hasBankSignalWord = BANK_SIGNAL_WORDS.any { normalizedBody.contains(it, ignoreCase = true) }
        val hasSignedAmountLine = SIGNED_AMOUNT_LINE_REGEX.containsMatchIn(normalizedBody)
        val hasAmountWithUnit = AMOUNT_WITH_UNIT_REGEX.containsMatchIn(normalizedBody)
        val hasMoneyLikeNumber = ANY_MONEY_LIKE_NUMBER_REGEX.containsMatchIn(normalizedBody)
        val hasBalance = BALANCE_REGEX.containsMatchIn(normalizedBody)
        val hasExplicitLastDigits = EXPLICIT_LAST_DIGITS_REGEX.containsMatchIn(normalizedBody)
        val hasLongNumber = LONG_NUMBER_REGEX.containsMatchIn(normalizedBody)

        if (senderLooksBank) score += 2.0
        if (hasDirectionWord) score += 2.5
        if (hasBankSignalWord) score += 1.5
        if (hasSignedAmountLine) score += 3.0
        if (hasAmountWithUnit) score += 2.5
        if (hasMoneyLikeNumber) score += 1.0
        if (hasBalance) score += 2.0
        if (hasExplicitLastDigits) score += 1.5
        if (hasLongNumber) score += 0.5

        val definitelyNotBank = score < 2.0 && !hasBankSignalWord && !senderLooksBank
        val shouldAskOnline = !definitelyNotBank && score in 2.0..6.0
        return BankSmsSignalAnalysis(score, definitelyNotBank, shouldAskOnline)
    }

    fun shouldAskOnline(sender: String, body: String): Boolean = analyze(sender, body).shouldAskOnline

    fun looksLikeBankSms(body: String): Boolean {
        val normalized = normalizeDigits(body)
        val hasDirectionWord = DEPOSIT_WORDS.any { normalized.contains(it, ignoreCase = true) } ||
            WITHDRAWAL_WORDS.any { normalized.contains(it, ignoreCase = true) }
        val hasSignedAmountLine = SIGNED_AMOUNT_LINE_REGEX.containsMatchIn(normalized)
        val hasBalance = BALANCE_REGEX.containsMatchIn(normalized)
        val hasAmount = AMOUNT_WITH_UNIT_REGEX.containsMatchIn(normalized) || hasSignedAmountLine

        return hasAmount && (hasDirectionWord || hasSignedAmountLine || hasBalance)
    }

    fun parse(sender: String, body: String): ParsedBankSms? {
        val normalized = normalizeDigits(body)
        if (!looksLikeBankSms(normalized)) return null

        val signedAmount = SIGNED_AMOUNT_LINE_REGEX.find(normalized)
        val amountToman = if (signedAmount != null) {
            parseNumber(signedAmount.groupValues[2])
        } else {
            val amountMatch = AMOUNT_WITH_UNIT_REGEX.find(normalized) ?: return null
            toToman(amountMatch.groupValues[1], amountMatch.groupValues[2])
        }
        if (amountToman <= 0) return null

        val isDeposit = when {
            signedAmount?.groupValues?.get(1) == "+" -> true
            signedAmount?.groupValues?.get(1) == "-" -> false
            else -> firstDirectionIsDeposit(normalized)
        }

        val balanceToman = BALANCE_REGEX.find(normalized)?.let {
            toToman(it.groupValues[1], it.groupValues.getOrNull(2).orEmpty())
        }
        val bankName = detectBankName(sender, normalized)
        val lastDigits = detectLastDigits(normalized)

        return ParsedBankSms(
            isDeposit = isDeposit,
            amountToman = amountToman,
            balanceToman = balanceToman,
            bankName = bankName,
            lastDigits = lastDigits,
            suggestedTitle = suggestTitle(normalized, bankName, isDeposit)
        )
    }

    private fun firstDirectionIsDeposit(body: String): Boolean {
        val firstDepositIdx = DEPOSIT_WORDS
            .mapNotNull { word -> body.indexOf(word, ignoreCase = true).takeIf { it >= 0 } }
            .minOrNull() ?: Int.MAX_VALUE
        val firstWithdrawIdx = WITHDRAWAL_WORDS
            .mapNotNull { word -> body.indexOf(word, ignoreCase = true).takeIf { it >= 0 } }
            .minOrNull() ?: Int.MAX_VALUE
        return firstDepositIdx < firstWithdrawIdx
    }

    private fun detectBankName(sender: String, body: String): String {
        val combined = "$sender\n$body"
        if (combined.contains("wepod", ignoreCase = true) ||
            combined.contains("vbank", ignoreCase = true) ||
            combined.contains("\u0648\u06cc\u067e\u0627\u062f", ignoreCase = true)
        ) {
            return WEPOD_BANK
        }
        return KNOWN_BANKS.firstOrNull {
            combined.contains(it, ignoreCase = true)
        }?.let { if (it == "\u0648\u06cc\u067e\u0627\u062f") WEPOD_BANK else it } ?: BANK_UNKNOWN
    }

    private fun detectLastDigits(body: String): String {
        EXPLICIT_LAST_DIGITS_REGEX.find(body)?.groupValues?.get(1)?.let { return it }
        val firstLine = body.lineSequence().firstOrNull().orEmpty()
        val longest = LONG_NUMBER_REGEX.findAll(firstLine).maxByOrNull { it.value.length }?.value
            ?: LONG_NUMBER_REGEX.findAll(body).maxByOrNull { it.value.length }?.value
        return longest?.takeLast(4).orEmpty()
    }

    private fun toToman(rawNumber: String, unit: String): Double {
        val value = parseNumber(rawNumber)
        val normalizedUnit = unit.lowercase()
        return if (unit == RIAL || unit == "\u0631" || normalizedUnit == "rial" || normalizedUnit == "rials" || normalizedUnit == "irr") value / 10.0 else value
    }

    private fun parseNumber(rawNumber: String): Double {
        return rawNumber.replace(Regex("[+\\-,\u060c\\s]"), "").toDoubleOrNull() ?: 0.0
    }

    private fun normalizeDigits(text: String): String {
        val persian = "\u06f0\u06f1\u06f2\u06f3\u06f4\u06f5\u06f6\u06f7\u06f8\u06f9"
        val arabic = "\u0660\u0661\u0662\u0663\u0664\u0665\u0666\u0667\u0668\u0669"
        var result = text
        for (i in 0..9) {
            result = result.replace(persian[i], ('0' + i))
            result = result.replace(arabic[i], ('0' + i))
        }
        return result
    }

    private fun suggestTitle(body: String, bankName: String, isDeposit: Boolean): String {
        return when {
            body.contains("\u0634\u0627\u0631\u0698") -> "\u062e\u0631\u06cc\u062f \u0634\u0627\u0631\u0698"
            body.contains("\u0642\u0628\u0636") -> "\u067e\u0631\u062f\u0627\u062e\u062a \u0642\u0628\u0636"
            body.contains("\u06a9\u0627\u0631\u062a \u0628\u0647 \u06a9\u0627\u0631\u062a") -> "\u06a9\u0627\u0631\u062a \u0628\u0647 \u06a9\u0627\u0631\u062a"
            body.contains("\u062d\u0642\u0648\u0642") -> "\u0648\u0627\u0631\u06cc\u0632 \u062d\u0642\u0648\u0642"
            body.contains("\u0642\u0633\u0637") -> "\u067e\u0631\u062f\u0627\u062e\u062a \u0642\u0633\u0637"
            body.contains("\u067e\u0627\u06cc\u0627\u0646\u0647") || body.contains("pos", ignoreCase = true) -> "\u062e\u0631\u06cc\u062f \u0628\u0627 \u06a9\u0627\u0631\u062a"
            isDeposit -> "\u0648\u0627\u0631\u06cc\u0632 - $bankName"
            else -> "\u0628\u0631\u062f\u0627\u0634\u062a/\u062e\u0631\u06cc\u062f - $bankName"
        }
    }
}
