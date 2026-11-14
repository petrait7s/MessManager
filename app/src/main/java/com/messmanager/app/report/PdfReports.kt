package com.messmanager.app.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import com.messmanager.app.data.Mess
import com.messmanager.app.domain.Dates
import com.messmanager.app.domain.Money
import com.messmanager.app.domain.MonthSummary
import com.messmanager.app.domain.Snapshot
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Collects drawing ops per A4 page, then renders with "Page x of N" footers. */
class ReportBuilder(private val mess: String, private val title: String, private val subtitle: String) {
    private val W = 595; private val H = 842; private val M = 36f
    private val pages = mutableListOf<MutableList<(Canvas) -> Unit>>()
    private var y = 0f
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.rgb(20, 24, 36); typeface = Typeface.DEFAULT }
    private val bold = Paint(body).apply { typeface = Typeface.DEFAULT_BOLD }
    private val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(6, 95, 70) }
    private val line = Paint().apply { color = Color.rgb(200, 205, 215); strokeWidth = 0.6f }
    private val fill = Paint().apply { color = Color.rgb(230, 244, 238) }
    private val stamp = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.ENGLISH).format(Date())

    init { newPage() }

    private fun ops() = pages.last()

    private fun newPage() {
        pages.add(mutableListOf()); y = M + 56f
    }

    private fun ensure(h: Float) { if (y + h > H - M - 20f) newPage() }

    fun heading(t: String) {
        ensure(30f); y += 8f
        val yy = y + 10f
        ops().add { c -> c.drawText(t, M, yy, head) }
        y += 16f
    }

    fun text(t: String, boldText: Boolean = false) {
        ensure(14f)
        val yy = y + 9f
        ops().add { c -> c.drawText(t.take(120), M, yy, if (boldText) bold else body) }
        y += 13f
    }

    fun table(headers: List<String>, rows: List<List<String>>, widths: List<Float>, right: Set<Int> = emptySet(), total: List<String>? = null) {
        val tw = W - 2 * M
        val xs = widths.runningFold(0f) { a, b -> a + b * tw }
        fun row(cells: List<String>, p: Paint, shade: Boolean) {
            ensure(16f)
            val top = y; val base = y + 11f
            ops().add { c ->
                if (shade) c.drawRect(M, top, M + tw, top + 15f, fill)
                cells.forEachIndexed { i, s ->
                    val w = widths[i] * tw - 6f
                    val n = p.breakText(s, true, w, null)
                    val t = if (n < s.length) s.substring(0, maxOf(0, n - 1)) + "…" else s
                    if (i in right) c.drawText(t, M + xs[i] + widths[i] * tw - 3f - p.measureText(t), base, p)
                    else c.drawText(t, M + xs[i] + 3f, base, p)
                }
                c.drawLine(M, top + 15f, M + tw, top + 15f, line)
            }
            y += 15f
        }
        row(headers, bold, true)
        if (rows.isEmpty()) { text("— none / কিছু নেই —") }
        rows.forEach { row(it, body, false) }
        if (total != null) row(total, bold, true)
        y += 4f
    }

    fun build(file: File) {
        val doc = PdfDocument()
        val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = Color.GRAY }
        val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(6, 95, 70) }
        pages.forEachIndexed { i, ops ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, i + 1).create())
            val c = page.canvas
            c.drawText(mess, M, M + 14f, big)
            c.drawText(title, M, M + 30f, bold)
            c.drawText(subtitle, M, M + 42f, body)
            c.drawLine(M, M + 48f, W - M, M + 48f, line)
            ops.forEach { it(c) }
            c.drawLine(M, H - M, W - M, H - M, line)
            c.drawText("Generated $stamp · MessManager", M, H - M + 12f, footer)
            val pn = "Page ${i + 1} of ${pages.size}"
            c.drawText(pn, W - M - footer.measureText(pn), H - M + 12f, footer)
            doc.finishPage(page)
        }
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
    }
}

object PdfReports {
    enum class Kind(val label: String) { MONTHLY("Full monthly report"), BAZAR("Daily bazar report"), CATEGORY("Category breakdown"), DUES("Due & advance report") }

    private fun tk(p: Long) = Money.format(p)

    fun reportsDir(ctx: Context) = File(ctx.cacheDir, "reports")

    fun generate(ctx: Context, mess: Mess, snap: Snapshot, sum: MonthSummary, kind: Kind): File {
        val label = Dates.monthLabel(sum.month)
        val b = ReportBuilder(mess.name, kind.label, "$label · Manager: ${mess.managerName}" + if (sum.closed) " · CLOSED" else "")
        val names = snap.members.associate { it.id to it.name }
        when (kind) {
            Kind.MONTHLY -> {
                b.heading("Summary")
                b.table(listOf("Item", "Amount (Tk)"), listOf(
                    listOf("Bazar (itemised)", tk(sum.bazarTotal)), listOf("Other expenses", tk(sum.expenseOnly)),
                    listOf("  of which fixed bills", tk(sum.fixedBills)), listOf("Member deposits", tk(sum.deposits)),
                    listOf("Settlement payments", tk(sum.payments)), listOf("Refunds", tk(sum.refunds)),
                ), listOf(0.7f, 0.3f), setOf(1), listOf("Total expense", tk(sum.totalExpense)))
                categoryTable(b, sum)
                memberTable(b, sum)
                b.heading("Expenses")
                b.table(listOf("Date", "Category", "Description", "Paid by", "Amount"),
                    snap.expenses.filter { it.month == sum.month }.sortedBy { it.day }.map {
                        listOf(Dates.fmtShort(it.day), it.category, it.description, it.payerId?.let { p -> names[p] } ?: "Mess fund", tk(it.amount))
                    }, listOf(0.12f, 0.17f, 0.4f, 0.16f, 0.15f), setOf(4), listOf("", "", "", "Total", tk(sum.expenseOnly)))
                bazarSection(b, snap, sum, names)
                b.heading("Deposits, payments & refunds")
                val tx = snap.deposits.filter { it.month == sum.month }.map { listOf(it.day, "Deposit", names[it.memberId] ?: "?", it.note, it.amount) } +
                    snap.payments.filter { it.month == sum.month }.map { listOf(it.day, if (it.kind == "REFUND") "Refund" else "Payment", names[it.memberId] ?: "?", it.note, it.amount) }
                b.table(listOf("Date", "Type", "Member", "Note", "Amount"),
                    tx.sortedBy { it[0] as Long }.map { listOf(Dates.fmtShort(it[0] as Long), it[1] as String, it[2] as String, it[3] as String, tk(it[4] as Long)) },
                    listOf(0.13f, 0.15f, 0.25f, 0.30f, 0.17f), setOf(4))
            }
            Kind.BAZAR -> bazarSection(b, snap, sum, names)
            Kind.CATEGORY -> categoryTable(b, sum)
            Kind.DUES -> {
                b.heading("Due and advance")
                b.table(listOf("Member", "Phone", "Due (Tk)", "Advance (Tk)"),
                    sum.lines.map { listOf(it.member.name, it.member.phone, if (it.balance < 0) tk(-it.balance) else "-", if (it.balance > 0) tk(it.balance) else "-") },
                    listOf(0.35f, 0.25f, 0.2f, 0.2f), setOf(2, 3), listOf("Total", "", tk(sum.totalDue), tk(sum.totalAdvance)))
                b.text("Due = member owes the mess. Advance = mess owes the member (carried to next month).")
            }
        }
        val f = File(reportsDir(ctx), "${kind.name.lowercase()}-${sum.month}.pdf")
        b.build(f); return f
    }

    private fun categoryTable(b: ReportBuilder, sum: MonthSummary) {
        b.heading("Category breakdown")
        val tot = sum.totalExpense.coerceAtLeast(1)
        b.table(listOf("Category", "Amount (Tk)", "Share"),
            sum.categoryTotals.entries.sortedByDescending { it.value }.map { listOf(it.key, tk(it.value), String.format(Locale.US, "%.1f%%", it.value * 100.0 / tot)) },
            listOf(0.5f, 0.3f, 0.2f), setOf(1, 2), listOf("Total", tk(sum.totalExpense), ""))
    }

    private fun memberTable(b: ReportBuilder, sum: MonthSummary) {
        b.heading("Member accounts")
        b.table(listOf("Member", "Carry", "Deposit", "Paid out", "Share", "Balance"),
            sum.lines.map { listOf(it.member.name, tk(it.carry), tk(it.deposits + it.payments - it.refunds), tk(it.paidOut), tk(it.totalShare), tk(it.balance) + if (it.balance < 0) " DUE" else if (it.balance > 0) " ADV" else "") },
            listOf(0.22f, 0.14f, 0.15f, 0.15f, 0.15f, 0.19f), setOf(1, 2, 3, 4, 5),
            listOf("Total", "", tk(sum.collected), "", tk(sum.lines.sumOf { it.totalShare }), ""))
    }

    private fun bazarSection(b: ReportBuilder, snap: Snapshot, sum: MonthSummary, names: Map<Long, String>) {
        b.heading("Daily bazar")
        val entries = snap.bazar.filter { it.month == sum.month }.sortedBy { it.day }
        if (entries.isEmpty()) b.text("No bazar entries / কোনো বাজার নেই")
        entries.groupBy { it.day }.forEach { (day, list) ->
            b.text("${Dates.fmt(day)} — total ${tk(list.sumOf { snap.entryTotal(it) })}", true)
            val rows = list.flatMap { e ->
                snap.itemsByEntry[e.id].orEmpty().map {
                    listOf(it.name, Money.qtyText(it.qtyMilli) + " " + it.unit, tk(it.unitPrice), tk(it.total), e.purchasedById?.let { p -> names[p] } ?: "Mess fund")
                }
            }
            b.table(listOf("Item", "Qty", "Unit price", "Total", "Bought by"), rows, listOf(0.32f, 0.16f, 0.16f, 0.16f, 0.20f), setOf(2, 3))
        }
        if (entries.isNotEmpty()) b.text("Monthly bazar total: ${tk(sum.bazarTotal)}", true)
    }

    fun memberReport(ctx: Context, mess: Mess, snap: Snapshot, sum: MonthSummary, memberId: Long): File {
        val line = sum.lines.firstOrNull { it.member.id == memberId } ?: throw IllegalStateException("Member not in month")
        val b = ReportBuilder(mess.name, "Member statement: ${line.member.name}", "${Dates.monthLabel(sum.month)} · Manager: ${mess.managerName}")
        b.heading("Calculation breakdown")
        b.table(listOf("Line", "Amount (Tk)"), listOf(
            listOf("Carry forward from previous month", tk(line.carry)),
            listOf("+ Deposits", tk(line.deposits)), listOf("+ Settlement payments", tk(line.payments)),
            listOf("- Refunds", tk(line.refunds)), listOf("+ Paid from pocket (expenses & bazar)", tk(line.paidOut)),
            listOf("= Total credited", tk(line.totalCredit)),
            listOf("- Expense share", tk(line.expenseShare)), listOf("- Bazar share (equal split)", tk(line.bazarShare)),
            listOf("= Total share", tk(line.totalShare)),
        ), listOf(0.75f, 0.25f), setOf(1), listOf(if (line.balance < 0) "DUE (member owes)" else "ADVANCE (mess owes)", tk(line.balance)))
        val expIds = snap.splits.filter { it.memberId == memberId }.associateBy { it.expenseId }
        b.heading("Expense shares")
        b.table(listOf("Date", "Category", "Description", "Total", "Your share"),
            snap.expenses.filter { it.id in expIds && it.month == sum.month }.sortedBy { it.day }.map { listOf(Dates.fmtShort(it.day), it.category, it.description, tk(it.amount), tk(expIds[it.id]!!.amount)) },
            listOf(0.13f, 0.17f, 0.34f, 0.18f, 0.18f), setOf(3, 4))
        b.heading("Deposits and payments")
        val tx = snap.deposits.filter { it.month == sum.month && it.memberId == memberId }.map { Triple(it.day, "Deposit", it.amount) } +
            snap.payments.filter { it.month == sum.month && it.memberId == memberId }.map { Triple(it.day, if (it.kind == "REFUND") "Refund" else "Payment", it.amount) }
        b.table(listOf("Date", "Type", "Amount"), tx.sortedBy { it.first }.map { listOf(Dates.fmt(it.first), it.second, tk(it.third)) }, listOf(0.4f, 0.3f, 0.3f), setOf(2))
        val f = File(reportsDir(ctx), "member-${line.member.id}-${sum.month}.pdf")
        b.build(f); return f
    }
}

class PdfPrintAdapter(private val file: File, private val name: String) : PrintDocumentAdapter() {
    override fun onLayout(old: PrintAttributes?, new: PrintAttributes?, cancel: CancellationSignal?, cb: LayoutResultCallback, extras: Bundle?) {
        if (cancel?.isCanceled == true) { cb.onLayoutCancelled(); return }
        cb.onLayoutFinished(PrintDocumentInfo.Builder(name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build(), true)
    }

    override fun onWrite(pages: Array<out PageRange>?, dest: ParcelFileDescriptor, cancel: CancellationSignal?, cb: WriteResultCallback) {
        try {
            FileInputStream(file).use { i -> FileOutputStream(dest.fileDescriptor).use { o -> i.copyTo(o) } }
            cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            cb.onWriteFailed(e.message)
        }
    }
}
