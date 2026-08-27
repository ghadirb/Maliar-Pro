package com.maliar.pro.utils

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import com.maliar.pro.R

/** Converts the compact sound value stored in Room into a playable URI. */
object ReminderSound {
    const val DEFAULT_ALARM = "DEFAULT_ALARM"

    data class BuiltIn(val value: String, val label: String)

    val builtIns = listOf(
        BuiltIn(DEFAULT_ALARM, "هشدار پیش فرض گوشی"),
        BuiltIn("RAW:reminder_voice_01", "امروز زمان ثبت درآمد شما است"),
        BuiltIn("RAW:reminder_voice_02", "امروز زمان ثبت هزینه های شما است"),
        BuiltIn("RAW:reminder_voice_03", "برای رفتن آماده شوید"),
        BuiltIn("RAW:reminder_voice_04", "پرداخت شما از زمان مقرر گذشته است"),
        BuiltIn("RAW:reminder_voice_05", "توجه، زمان یادآوری فرا رسیده است"),
        BuiltIn("RAW:reminder_voice_06", "توجه،یادآوری"),
        BuiltIn("RAW:reminder_voice_07", "ثبت اطلاعات انجام شد"),
        BuiltIn("RAW:reminder_voice_08", "زمان بیدار شدن است"),
        BuiltIn("RAW:reminder_voice_09", "زمان پرداخت شما فرا رسیده است"),
        BuiltIn("RAW:reminder_voice_10", "زمان پیگیری طلب شما فرا رسیده است"),
        BuiltIn("RAW:reminder_voice_11", "زمان تماس رسیده است"),
        BuiltIn("RAW:reminder_voice_12", "زمان خوردن قرض شما فرا رسیده است"),
        BuiltIn("RAW:reminder_voice_13", "زمان شروع حرکت است"),
        BuiltIn("RAW:reminder_voice_14", "زمان یادآوری شما فرا رسیده است"),
        BuiltIn("RAW:reminder_voice_15", "عملیات انجام شد"),
        BuiltIn("RAW:reminder_voice_16", "قسط امروز سررسید می شود"),
        BuiltIn("RAW:reminder_voice_17", "قسط تا روز دیگر سررسید می شود"),
        BuiltIn("RAW:reminder_voice_18", "قسط شما امروز سررسید می شود"),
        BuiltIn("RAW:reminder_voice_19", "قسط شما فردا سررسید می شود"),
        BuiltIn("RAW:reminder_voice_20", "یادآوری پرداخت"),
        BuiltIn("RAW:reminder_voice_21", "یادآوری ثبت درآمد"),
        BuiltIn("RAW:reminder_voice_22", "یادآوری ثبت هزینه"),
        BuiltIn("RAW:reminder_voice_23", "یادآوری شما فرا رسیده است"),
        BuiltIn("RAW:reminder_voice_24", "یادآوری شما"),
        BuiltIn("RAW:reminder_voice_25", "یادآوری طلب"),
        BuiltIn("RAW:reminder_voice_26", "یادآوری عقب افتاده"),
        BuiltIn("RAW:reminder_voice_27", "یادآوری قبض"),
        BuiltIn("RAW:reminder_voice_28", "یادآوری قسط"),
        BuiltIn("RAW:reminder_voice_29", "یک بدهی در انتظار پرداخت شما است"),
        BuiltIn("RAW:reminder_voice_30", "یک پرداخت در انتظار شما است"),
        BuiltIn("RAW:reminder_voice_31", "یک پرداخت عقب افتاده دارید"),
        BuiltIn("RAW:reminder_voice_32", "یک طلب در انتظار دریافت شما است"),
        BuiltIn("RAW:reminder_voice_33", "یک قسط در انتظار پرداخت شما است"),
        BuiltIn("RAW:reminder_voice_34", "یک یادآوری برای شما داریم")
    )

    fun toUri(context: Context, value: String?): Uri? {
        val selected = value.orEmpty()
        if (selected.isBlank() || selected == DEFAULT_ALARM) {
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
        if (selected.startsWith("RAW:")) {
            val id = context.resources.getIdentifier(selected.removePrefix("RAW:"), "raw", context.packageName)
            return if (id != 0) Uri.parse("android.resource://${context.packageName}/$id") else null
        }
        return runCatching { Uri.parse(selected) }.getOrNull()
    }

    fun labelFor(value: String?): String = builtIns.firstOrNull { it.value == value }?.label
        ?: if (value.isNullOrBlank() || value == DEFAULT_ALARM) "هشدار پیش فرض گوشی" else "فایل انتخاب شده از گوشی"
}
