package com.maliar.pro.support

/** One frequently-asked question and its answer. */
data class FaqItem(val question: String, val answer: String)

/**
 * Static FAQ list for the support screen (spec section 5: "ساختار FAQ را طوری طراحی کن
 * که بعداً بتوانم سوالات و پاسخ‌های جدید اضافه کنم"). Adding a question later is just
 * adding one more [FaqItem] to the list below - the screen renders whatever this
 * returns, in order, with no other change needed. If FAQs ever need to come from a
 * server instead of being hardcoded, only this object's body would change (e.g. to an
 * HTTP call with this same list as a bundled fallback) - [SupportFragment] would be
 * unaffected either way.
 */
object FaqRepository {
    fun getAll(): List<FaqItem> = listOf(
        FaqItem(
            "چگونه هزینه ثبت کنم؟",
            "از تب «حسابداری» گزینهٔ «ثبت هزینه» را بزنید، یا از دستیار هوشمند به‌صورت صوتی یا نوشتاری بگویید (مثلاً «۵۰ هزار تومان برای ناهار خرج کردم»)."
        ),
        FaqItem(
            "چگونه درآمد ثبت کنم؟",
            "از تب «حسابداری» گزینهٔ «ثبت درآمد» را بزنید و مبلغ، منبع و حساب مقصد را وارد کنید."
        ),
        FaqItem(
            "چگونه یادآور ایجاد کنم؟",
            "از تب «یادآوری‌ها» روی دکمهٔ افزودن بزنید، عنوان و زمان را مشخص کنید و در صورت نیاز تکرار روزانه/هفتگی را فعال کنید."
        ),
        FaqItem(
            "چگونه اطلاعاتم را پشتیبان‌گیری کنم؟",
            "از «تنظیمات» گزینهٔ «تهیه پشتیبان» را بزنید و مقصد ذخیره (حافظهٔ گوشی یا گوگل‌درایو) را انتخاب کنید؛ پشتیبان‌گیری خودکار روزانه هم قابل فعال‌سازی است."
        ),
        FaqItem(
            "چگونه اشتراک تهیه کنم؟",
            "از «تنظیمات» گزینهٔ «مدیریت اشتراک» را باز کنید و پلن موردنظر را انتخاب کنید."
        ),
        FaqItem(
            "چگونه با پشتیبانی تماس بگیرم؟",
            "از همین صفحه، یکی از گزینه‌های تلگرام، ایتا یا روبیکا را انتخاب کنید تا مستقیم به چت پشتیبانی مالیار پرو وصل شوید."
        )
    )
}
