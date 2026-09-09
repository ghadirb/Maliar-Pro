package com.maliar.pro.utils

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

/** Converts the compact sound value stored in Room into a playable URI. */
object ReminderSound {
    const val DEFAULT_ALARM = "DEFAULT_ALARM"

    data class BuiltIn(val value: String, val label: String)

    val builtIns = listOf(
        BuiltIn(DEFAULT_ALARM, "هشدار پیش‌فرض گوشی"),
        BuiltIn("RAW:reminder_voice_01", "امروز موعد پرداخت بدهی شماست"),
        BuiltIn("RAW:reminder_voice_02", "امروز موعد پرداخت قسط شماست"),
        BuiltIn("RAW:reminder_voice_03", "امروز موعد دریافت طلب شماست"),
        BuiltIn("RAW:reminder_voice_04", "امروز یک پرداخت مهم دارید"),
        BuiltIn("RAW:reminder_voice_05", "بخش زیادی از بودجه شما مصرف شده است"),
        BuiltIn("RAW:reminder_voice_06", "برنامه غذایی امروز آماده است"),
        BuiltIn("RAW:reminder_voice_07", "برنامه غذایی این هفته آماده است"),
        BuiltIn("RAW:reminder_voice_08", "به سقف بودجه این بخش نزدیک شده‌اید"),
        BuiltIn("RAW:reminder_voice_09", "بودجه این بخش به پایان رسیده است"),
        BuiltIn("RAW:reminder_voice_10", "تراکنش مالی با موفقیت ثبت شد"),
        BuiltIn("RAW:reminder_voice_11", "درآمد جدید ثبت شد"),
        BuiltIn("RAW:reminder_voice_12", "درآمد شما ثبت شد"),
        BuiltIn("RAW:reminder_voice_13", "زمان بررسی برنامه غذایی امروز است"),
        BuiltIn("RAW:reminder_voice_14", "زمان بررسی وضعیت مالی شماست"),
        BuiltIn("RAW:reminder_voice_15", "زمان سرویس خودرو نزدیک است"),
        BuiltIn("RAW:reminder_voice_16", "قسط شما سررسید شده است"),
        BuiltIn("RAW:reminder_voice_17", "گزارش مالی امروز آماده است"),
        BuiltIn("RAW:reminder_voice_18", "گزارش مالی این ماه آماده است"),
        BuiltIn("RAW:reminder_voice_19", "گزارش مالی این هفته آماده است"),
        BuiltIn("RAW:reminder_voice_20", "گزارش مالی شما آماده است"),
        BuiltIn("RAW:reminder_voice_21", "موعد بررسی خودرو فرا رسیده است"),
        BuiltIn("RAW:reminder_voice_22", "موعد پرداخت بدهی شما نزدیک است"),
        BuiltIn("RAW:reminder_voice_23", "موعد پرداخت قسط شما نزدیک است"),
        BuiltIn("RAW:reminder_voice_24", "موعد تعویض روغن خودرو نزدیک است"),
        BuiltIn("RAW:reminder_voice_25", "موعد دریافت طلب شما نزدیک است"),
        BuiltIn("RAW:reminder_voice_26", "موعد یک پرداخت نزدیک است"),
        BuiltIn("RAW:reminder_voice_27", "هزینه تقریبی برنامه غذایی امروز آماده است"),
        BuiltIn("RAW:reminder_voice_28", "هزینه جدید ثبت شد"),
        BuiltIn("RAW:reminder_voice_29", "هزینه شما ثبت شد"),
        BuiltIn("RAW:reminder_voice_30", "هزینه‌های شما از بودجه تعیین‌شده بیشتر شده است"),
        BuiltIn("RAW:reminder_voice_31", "هشدار بودجه"),
        BuiltIn("RAW:reminder_voice_32", "یادآوری بدهی"),
        BuiltIn("RAW:reminder_voice_33", "یادآوری پرداخت"),
        BuiltIn("RAW:reminder_voice_34", "یادآوری سرویس خودرو"),
        BuiltIn("RAW:reminder_voice_35", "یادآوری طلب"),
        BuiltIn("RAW:reminder_voice_36", "یادآوری قسط"),
        BuiltIn("RAW:reminder_voice_37", "یادآوری مالی"),
        BuiltIn("RAW:reminder_voice_38", "یک بدهی شما سررسید شده است"),
        BuiltIn("RAW:reminder_voice_39", "یک پرداخت شما سررسید شده است"),
        BuiltIn("RAW:reminder_voice_40", "یک پرداخت مهم در پیش دارید"),
        BuiltIn("RAW:reminder_voice_41", "یک طلب شما سررسید شده است"),
        BuiltIn("RAW:reminder_voice_42", "یک قسط پرداخت‌نشده دارید"),
        BuiltIn("RAW:reminder_voice_43", "یک هزینه مربوط به خودرو ثبت نشده است")
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
        ?: if (value.isNullOrBlank() || value == DEFAULT_ALARM) "هشدار پیش‌فرض گوشی" else "فایل انتخاب‌شده از گوشی"
}
