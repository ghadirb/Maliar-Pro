package com.maliar.pro.support

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Opens a [SupportChannel]'s public support link, preferring the person's own installed
 * app over the browser (spec section 2): first try targeting [SupportChannel.appPackage]
 * directly so the link opens inside Telegram/Eitaa/Rubika itself, and only fall back to
 * a plain browser Intent if that fails for any reason (not installed, package-visibility
 * restricted, or anything else) - every path is wrapped so this can never crash the
 * app, and the person always gets *something* to look at (either the app/browser opens,
 * or a clear on-screen message telling them it couldn't).
 */
object SupportLauncher {

    fun open(context: Context, channel: SupportChannel) {
        val uri = Uri.parse(channel.publicUrl)
        val appIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(channel.appPackage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val opened = runCatching { context.startActivity(appIntent) }.isSuccess
        if (opened) return

        val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val openedInBrowser = runCatching { context.startActivity(browserIntent) }.isSuccess
        if (!openedInBrowser) {
            Toast.makeText(
                context,
                "امکان باز کردن پشتیبانی ${channel.displayName} وجود ندارد؛ لطفاً از مرورگر یا برنامهٔ پیام‌رسان به‌صورت دستی به آدرس ${channel.publicUrl} مراجعه کنید.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** Copies [text] to the clipboard and shows a short confirmation - used before
     *  opening a messenger for "گزارش مشکل"/"پیشنهاد قابلیت", since no public support
     *  link can pre-fill a message into an existing chat on its own (spec section 7:
     *  no bot/API - just public links), so the person pastes it themselves once the
     *  chat opens. */
    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "متن کپی شد؛ آن را در چت پشتیبانی paste کنید.", Toast.LENGTH_LONG).show()
    }
}
