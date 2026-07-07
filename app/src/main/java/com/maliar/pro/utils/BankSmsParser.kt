package com.maliar.pro.utils

data class ParsedBankSms(
    val isDeposit: Boolean,
    val amountToman: Double,
    val balanceToman: Double?,
    val bankName: String,
    val lastDigits: String,
    val suggestedTitle: String
)

object BankSmsParser {

    private val DEPOSIT_WORDS = listOf(
        "واریز", "واریزی", "بستانکار", "افزایش موجودی", "دریافت", "انتقال به حساب"
    )

    private val WITHDRAWAL_WORDS = listOf(
        "برداشت", "خرید", "کسر", "انتقال", "پرداخت", "بدهکار", "پایانه", "pos"
    )

    private val KNOWN_BANKS = listOf(
        "ملت", "ملی", "صادرات", "تجارت", "سپه", "کشاورزی", "مسکن", "رفاه",
        "پست بانک", "پارسیان", "پاسارگاد", "سامان", "اقتصاد نوین", "سینا", "دی",
        "انصار", "شهر", "ایران زمین", "گردشگری", "حکمت ایرانیان", "خاورمیانه",
        "مهر ایران", "توسعه تعاون", "کارآفرین", "ملل", "آینده", "سرمایه"
    )

    private val AMOUNT_REGEX = Regex(
        "(?:مبلغ|مقدار|وجه)?\\s*[:：]?\\s*([0-9][0-9,،\\s]*[0-9]|[0-9])\\s*(ریال|تومان|ر)",
        RegexOption.IGNORE_CASE
    )
    private val BALANCE_REGEX = Regex(
        "(?:موجودی|مانده\\s*حساب|مانده|باقیمانده)\\D{0,12}([0-9][0-9,،\\s]*[0-9]|[0-9])\\s*(ریال|تومان|ر)?",
        RegexOption.IGNORE_CASE
    )
    private val LAST_DIGITS_REGEX = Regex("(?:کارت|حساب|سپرده)[^0-9]{0,24}([0-9]{4})(?![0-9])")

    fun looksLikeBankSms(body: String): Boolean {
        val normalized = normalizeDigits(body)
        val hasDirection = DEPOSIT_WORDS.any { normalized.contains(it, ignoreCase = true) } ||
            WITHDRAWAL_WORDS.any { normalized.contains(it, ignoreCase = true) }
        return hasDirection && AMOUNT_REGEX.containsMatchIn(normalized)
    }

    fun parse(sender: String, body: String): ParsedBankSms? {
        val normalized = normalizeDigits(body)
        if (!looksLikeBankSms(normalized)) return null

        val firstDepositIdx = DEPOSIT_WORDS
            .mapNotNull { word -> normalized.indexOf(word, ignoreCase = true).takeIf { it >= 0 } }
            .minOrNull() ?: Int.MAX_VALUE
        val firstWithdrawIdx = WITHDRAWAL_WORDS
            .mapNotNull { word -> normalized.indexOf(word, ignoreCase = true).takeIf { it >= 0 } }
            .minOrNull() ?: Int.MAX_VALUE
        val isDeposit = firstDepositIdx < firstWithdrawIdx

        val amountMatch = AMOUNT_REGEX.find(normalized) ?: return null
        val amountToman = toToman(amountMatch.groupValues[1], amountMatch.groupValues[2])
        if (amountToman <= 0) return null

        val balanceMatch = BALANCE_REGEX.find(normalized)
        val balanceToman = balanceMatch?.let {
            toToman(it.groupValues[1], it.groupValues.getOrNull(2).orEmpty())
        }

        val lastDigits = LAST_DIGITS_REGEX.find(normalized)?.groupValues?.get(1).orEmpty()
        val bankName = KNOWN_BANKS.firstOrNull {
            normalized.contains(it, ignoreCase = true) || sender.contains(it, ignoreCase = true)
        } ?: "بانک نامشخص"

        return ParsedBankSms(
            isDeposit = isDeposit,
            amountToman = amountToman,
            balanceToman = balanceToman,
            bankName = bankName,
            lastDigits = lastDigits,
            suggestedTitle = suggestTitle(normalized, bankName, isDeposit)
        )
    }

    private fun toToman(rawNumber: String, unit: String): Double {
        val value = rawNumber.replace(Regex("[,،\\s]"), "").toDoubleOrNull() ?: return 0.0
        return if (unit == "ریال" || unit == "ر") value / 10.0 else value
    }

    private fun normalizeDigits(text: String): String {
        val persian = "۰۱۲۳۴۵۶۷۸۹"
        val arabic = "٠١٢٣٤٥٦٧٨٩"
        var result = text
        for (i in 0..9) {
            result = result.replace(persian[i], ('0' + i))
            result = result.replace(arabic[i], ('0' + i))
        }
        return result
    }

    private fun suggestTitle(body: String, bankName: String, isDeposit: Boolean): String {
        return when {
            body.contains("شارژ") -> "خرید شارژ"
            body.contains("قبض") -> "پرداخت قبض"
            body.contains("کارت به کارت") -> "کارت به کارت"
            body.contains("حقوق") -> "واریز حقوق"
            body.contains("قسط") -> "پرداخت قسط"
            body.contains("پایانه") || body.contains("pos", ignoreCase = true) -> "خرید با کارت"
            isDeposit -> "واریز - $bankName"
            else -> "برداشت/خرید - $bankName"
        }
    }
}
