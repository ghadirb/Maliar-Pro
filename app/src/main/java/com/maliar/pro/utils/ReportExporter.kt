package com.maliar.pro.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.maliar.pro.database.FinancialReport
import java.io.OutputStreamWriter

/**
 * Exports a [FinancialReport] either as CSV (opens directly in Excel/Google Sheets - no
 * external library needed, CSV is a plain-text format every spreadsheet app already reads)
 * or as a simple one-page PDF built with Android's own android.graphics.pdf.PdfDocument
 * (also no new dependency). Persian text on the PDF is drawn with [StaticLayout] rather
 * than a raw Canvas.drawText call, since StaticLayout is what actually applies correct
 * bidi/shaping for RTL scripts - drawText alone renders Persian text reversed/disconnected.
 */
object ReportExporter {

    fun exportCsv(context: Context, uri: Uri, report: FinancialReport) {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                // UTF-8 BOM so Excel (which otherwise guesses the wrong encoding for
                // non-Latin text) opens the Persian text correctly instead of as garbled.
                writer.write("\uFEFF")
                writer.appendLine("خلاصه گزارش مالی")
                writer.appendLine("مجموع درآمد,${report.totalIncome}")
                writer.appendLine("مجموع هزینه,${report.totalExpense}")
                writer.appendLine("خالص,${report.net}")
                writer.appendLine()
                writer.appendLine("بیشترین هزینه‌ها")
                writer.appendLine("توضیحات,دسته,مبلغ")
                report.topExpenses.forEach { writer.appendLine("${csv(it.description)},${csv(it.category)},${it.amount}") }
                writer.appendLine()
                writer.appendLine("بیشترین درآمدها")
                writer.appendLine("توضیحات,دسته,مبلغ")
                report.topIncomes.forEach { writer.appendLine("${csv(it.description)},${csv(it.category)},${it.amount}") }
                if (report.topExpenseCategory != null) {
                    writer.appendLine()
                    writer.appendLine("پرهزینه‌ترین دسته,${csv(report.topExpenseCategory.category)},${report.topExpenseCategory.total}")
                }
            }
        }
    }

    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    fun exportPdf(context: Context, uri: Uri, report: FinancialReport, periodLabel: String) {
        val pageWidth = 595 // A4 at 72dpi
        val pageHeight = 842
        val margin = 40f

        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
        val canvas = page.canvas

        var y = margin
        y = drawParagraph(canvas, "گزارش مالی مالیار پرو - $periodLabel", pageWidth, margin, y, 18f, true)
        y += 10f
        y = drawParagraph(canvas, "مجموع درآمد: ${format(report.totalIncome)} تومان", pageWidth, margin, y, 13f)
        y = drawParagraph(canvas, "مجموع هزینه: ${format(report.totalExpense)} تومان", pageWidth, margin, y, 13f)
        y = drawParagraph(canvas, "خالص: ${format(report.net)} تومان", pageWidth, margin, y, 13f, true)
        if (report.topExpenseCategory != null) {
            y = drawParagraph(
                canvas,
                "پرهزینه‌ترین دسته: ${report.topExpenseCategory.category} (${format(report.topExpenseCategory.total)} تومان)",
                pageWidth, margin, y, 12f
            )
        }
        y += 14f

        y = drawParagraph(canvas, "بیشترین هزینه‌ها:", pageWidth, margin, y, 14f, true)
        report.topExpenses.forEach {
            y = drawParagraph(canvas, "- ${it.description.ifBlank { "بدون توضیح" }}: ${format(it.amount)} تومان", pageWidth, margin, y, 11f)
        }
        y += 14f

        y = drawParagraph(canvas, "بیشترین درآمدها:", pageWidth, margin, y, 14f, true)
        report.topIncomes.forEach {
            y = drawParagraph(canvas, "- ${it.description.ifBlank { "بدون توضیح" }}: ${format(it.amount)} تومان", pageWidth, margin, y, 11f)
        }

        document.finishPage(page)
        context.contentResolver.openOutputStream(uri)?.use { document.writeTo(it) }
        document.close()
    }

    private fun format(amount: Double): String = com.maliar.pro.utils.CurrencyFormatter.format(amount, "")

    /** Draws one right-aligned, RTL-correct paragraph and returns the Y position right
     *  after it, so callers can just keep chaining calls downward. */
    private fun drawParagraph(
        canvas: Canvas,
        text: String,
        pageWidth: Int,
        margin: Float,
        startY: Float,
        textSizeSp: Float,
        bold: Boolean = false
    ): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizeSp * 2.2f // rough sp->px at typical density for this fixed-size PDF canvas
            isFakeBoldText = bold
        }
        val width = (pageWidth - margin * 2).toInt()
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(android.text.TextDirectionHeuristics.RTL)
            .build()

        canvas.save()
        canvas.translate(margin, startY)
        layout.draw(canvas)
        canvas.restore()

        return startY + layout.height + 6f
    }
}
