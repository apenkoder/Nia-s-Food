package com.apenkoder.niasfood.data.export

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.apenkoder.niasfood.R
import com.apenkoder.niasfood.core.Money
import com.apenkoder.niasfood.core.NairobiTime
import com.apenkoder.niasfood.data.repo.ZReportData
import com.apenkoder.niasfood.domain.ExpenseCategory
import java.io.File

/** Writes a one-page Z-report PDF into the app cache and builds a share intent (no network permission). */
object ZReportPdfWriter {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f

    fun write(context: Context, report: ZReportData): File {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val z = report.shift.zNumber ?: 0
        val file = File(dir, "z-report-${report.shift.businessDate}-$z.pdf")

        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        val canvas = page.canvas
        val title = Paint().apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD }
        val heading = Paint().apply { textSize = 12f; typeface = Typeface.DEFAULT_BOLD }
        val body = Paint().apply { textSize = 11f }
        val small = Paint().apply { textSize = 9f }

        var y = MARGIN + 10f
        fun line(text: String, paint: Paint, gap: Float = 16f) {
            canvas.drawText(text, MARGIN, y, paint)
            y += gap
        }
        fun pair(label: String, value: String) {
            canvas.drawText(label, MARGIN, y, body)
            val width = body.measureText(value)
            canvas.drawText(value, PAGE_WIDTH - MARGIN - width, y, body)
            y += 15f
        }

        val s = report.shift
        val sum = report.summary
        line(context.getString(R.string.pdf_title, report.outletName), title, 24f)
        line(context.getString(R.string.pdf_subtitle, z, NairobiTime.shortDate(s.businessDate)), body)
        line(context.getString(R.string.pdf_manager, s.closedByName ?: s.openedByName), body)
        val opened = NairobiTime.time(s.openedAt)
        val closed = s.closedAt?.let { NairobiTime.time(it) } ?: "-"
        line(context.getString(R.string.pdf_hours, opened, closed), body, 24f)

        line(context.getString(R.string.pdf_section_sales), heading)
        pair(context.getString(R.string.pdf_tickets), sum.tickets.toString())
        pair(context.getString(R.string.pdf_cash), Money(sum.cashCents).format())
        pair(context.getString(R.string.pdf_mm_verified), Money(sum.mmVerifiedCents).format())
        pair(context.getString(R.string.pdf_mm_unverified), Money(sum.mmUnverifiedCents).format())
        pair(context.getString(R.string.pdf_gross), Money(sum.grossCents).format())
        pair(context.getString(R.string.pdf_voids, sum.voidedCount), Money(sum.voidedCents).format())
        y += 10f

        line(context.getString(R.string.pdf_section_expenses), heading)
        for (category in ExpenseCategory.values()) {
            val amount = sum.expensesByCategory[category] ?: continue
            pair(categoryLabel(context, category), Money(amount).format())
        }
        pair(context.getString(R.string.pdf_expenses_total), Money(sum.expensesCents).format())
        y += 10f

        line(context.getString(R.string.pdf_section_cash), heading)
        pair(context.getString(R.string.pdf_opening_float), Money(s.openingFloatCents).format())
        pair(context.getString(R.string.pdf_expected_cash), Money(report.expectedCashCents).format())
        pair(context.getString(R.string.pdf_counted_cash), Money(s.countedCashCents ?: 0L).format())
        pair(context.getString(R.string.pdf_variance), Money(s.varianceCents ?: 0L).format())
        val varianceReason = s.varianceReason
        if (varianceReason != null) line(context.getString(R.string.pdf_variance_reason, varianceReason), small)
        pair(context.getString(R.string.pdf_flagged), report.flaggedCount.toString())
        y += 10f

        line(context.getString(R.string.pdf_net), heading)
        pair(context.getString(R.string.pdf_net_label), Money(sum.netCashflowCents).format())
        y += 20f
        line(context.getString(R.string.pdf_footer), small)

        doc.finishPage(page)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun shareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, context.getString(R.string.share_report))
    }

    fun categoryLabel(context: Context, category: ExpenseCategory): String = context.getString(
        when (category) {
            ExpenseCategory.INGREDIENTS -> R.string.cat_ingredients
            ExpenseCategory.GAS_CHARCOAL -> R.string.cat_gas
            ExpenseCategory.WAGES -> R.string.cat_wages
            ExpenseCategory.RENT -> R.string.cat_rent
            ExpenseCategory.UTILITIES -> R.string.cat_utilities
            ExpenseCategory.TRANSPORT -> R.string.cat_transport
            ExpenseCategory.OTHER -> R.string.cat_other
        }
    )
}
