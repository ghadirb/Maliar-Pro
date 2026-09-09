package com.maliar.pro.utils

import androidx.biometric.BiometricManager

/**
 * Turns a [BiometricManager.canAuthenticate] result code into an accurate, specific
 * Persian message instead of a generic "not ready" - in particular distinguishing "this
 * device has no biometric sensor at all" (common on cheap/unofficial-import devices like
 * the G-Plus P10, where the vendor HAL doesn't register fingerprint hardware) from "a
 * sensor exists but nothing is enrolled" or "it's temporarily unavailable", since those
 * call for different user action (nothing to do vs. go enroll a fingerprint vs. try again
 * later).
 */
object BiometricAvailability {
    fun describe(code: Int): String = when (code) {
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
            "این دستگاه سنسور اثر انگشت یا قفل تصویری/الگو ندارد یا سیستم‌عامل آن را پشتیبانی نمی‌کند؛ قفل بیومتریک روی این گوشی قابل استفاده نیست."
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
            "سنسور بیومتریک این دستگاه موقتاً در دسترس نیست. کمی بعد دوباره امتحان کنید."
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
            "هنوز اثر انگشت یا قفل صفحه‌ای در تنظیمات گوشی ثبت نکرده‌اید. ابتدا از تنظیمات گوشی یک قفل تعریف کنید."
        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
            "برای استفاده از قفل بیومتریک، ابتدا به‌روزرسانی امنیتی گوشی را نصب کنید."
        BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED, BiometricManager.BIOMETRIC_STATUS_UNKNOWN ->
            "قفل بیومتریک روی این دستگاه پشتیبانی نمی‌شود."
        else -> "قفل بیومتریک روی این دستگاه در دسترس نیست."
    }
}
