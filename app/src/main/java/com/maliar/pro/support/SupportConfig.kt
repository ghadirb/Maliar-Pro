package com.maliar.pro.support

/**
 * Single, central place for every support-related link/ID (spec: "تمام لینک‌ها و
 * شناسه‌های پشتیبانی باید در یک محل مرکزی قابل تغییر باشند"). Public group/channel
 * links only; no bot or messaging API is used anywhere in this file or the support
 * screen it feeds (spec section 7).
 *
 * Every link below is a confirmed, real support account (Telegram, Eitaa and Rubika all
 * use the same "MaliarProSupport" handle). To point support at a different account
 * later, change the constants below - nothing else in the app needs to change.
 */
object SupportConfig {

    /** Public Telegram support link (also accepts a bare "@username" the same way). */
    const val LINK_TELEGRAM = "https://t.me/MaliarProSupport"

    /** Public Eitaa support link. */
    const val LINK_EITAA = "https://eitaa.com/MaliarProSupport"

    /** Public Rubika support link - username "@MaliarProSupport". */
    const val LINK_RUBIKA = "https://rubika.ir/MaliarProSupport"

    /** Where "گزارش مشکل"/"پیشنهاد قابلیت" open by default when the person hasn't
     *  picked a specific messenger from the report/suggestion dialog themselves. */
    val DEFAULT_CHANNEL = SupportChannel.TELEGRAM
}

/**
 * One support messenger option. [appPackage] is used to try opening the person's own
 * installed app directly (see [SupportLauncher.open]); when it's not installed (or on
 * some ROMs where package visibility blocks the check even though it's queried in the
 * manifest), [SupportLauncher.open] transparently falls back to the plain browser link -
 * the person is never shown a dead end.
 *
 * Adding a fourth channel later (e.g. WhatsApp) means adding one more entry here; the
 * screen, the report dialog and the launcher all iterate this enum and need no other
 * changes.
 */
enum class SupportChannel(
    val displayName: String,
    val emoji: String,
    val publicUrl: String,
    val appPackage: String
) {
    TELEGRAM("تلگرام", "\u2708\uFE0F", SupportConfig.LINK_TELEGRAM, "org.telegram.messenger"),
    EITAA("ایتا", "\uD83D\uDFE0", SupportConfig.LINK_EITAA, "ir.eitaa.messenger"),
    RUBIKA("روبیکا", "\uD83D\uDD35", SupportConfig.LINK_RUBIKA, "ir.resaneh1.iptv")
}
