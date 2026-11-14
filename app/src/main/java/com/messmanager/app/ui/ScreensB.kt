@file:OptIn(ExperimentalMaterial3Api::class)

package com.messmanager.app.ui

import android.content.Context
import android.content.Intent
import android.print.PrintManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.messmanager.app.data.*
import com.messmanager.app.domain.*
import com.messmanager.app.report.PdfPrintAdapter
import com.messmanager.app.report.PdfReports
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun shareFile(ctx: Context, f: File) {
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(i, "Share report / রিপোর্ট শেয়ার"))
}

fun printFile(ctx: Context, f: File) {
    val pm = ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager
    pm.print(f.name, PdfPrintAdapter(f, f.name), null)
}

@Composable
fun MembersScreen(vm: AppVm, host: SheetHost) {
    val snap by vm.snapshot.collectAsState()
    val sum by vm.summary.collectAsState()
    val balances = sum.lines.associate { it.member.id to it.balance }
    val activeCount = snap.members.count { it.isActive() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 160.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Members", style = MaterialTheme.typography.headlineMedium)
            MonthBar(vm)
            Text("$activeCount active of max 10 (recommended 5–10)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { PressButton("Add member / সদস্য যোগ", { host.member(null) }, Modifier.fillMaxWidth(), icon = Icons.Default.Add) }
        if (snap.members.isEmpty()) item { EmptyState("No members", "Add your mess members to start.") }
        items(snap.members, key = { it.id }) { m ->
            val b = balances[m.id] ?: 0L
            TiltCard(Modifier.fillMaxWidth(), if (b < 0) Danger else Emerald, onClick = { host.openMember(m.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            (if (m.isActive()) "Active" else "Inactive from ${m.inactiveFrom}") + " · joined ${Dates.fmtShort(m.joinDay)}" + if (m.phone.isNotBlank()) " · ${m.phone}" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    BalanceBadge(b)
                }
            }
        }
    }
}

@Composable
private fun BreakRow(label: String, value: Long, color: Color = Color.Unspecified, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text("৳ " + Money.format(value), color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun MemberDetail(vm: AppVm, id: Long, host: SheetHost, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val snap by vm.snapshot.collectAsState()
    val sum by vm.summary.collectAsState()
    val boot by vm.boot.collectAsState()
    val month by vm.month.collectAsState()
    val member = snap.members.firstOrNull { it.id == id }
    if (member == null) { LaunchedEffect(Unit) { onBack() }; return }
    val line = sum.lines.firstOrNull { it.member.id == id }
    var delDeposit by remember { mutableStateOf<Deposit?>(null) }
    var delPayment by remember { mutableStateOf<Payment?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 160.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text(member.name, style = MaterialTheme.typography.titleLarge)
                    Text(if (member.phone.isBlank()) "No phone" else member.phone, style = MaterialTheme.typography.bodySmall)
                }
                IconButton({ host.member(member) }) { Icon(Icons.Default.Edit, "Edit member") }
            }
            MonthBar(vm)
        }
        item {
            TiltCard(Modifier.fillMaxWidth(), if ((line?.balance ?: 0) < 0) Danger else Emerald) {
                Text("Calculation breakdown", style = MaterialTheme.typography.titleMedium)
                if (line == null) Text("Not part of this month.", style = MaterialTheme.typography.bodySmall)
                else {
                    BreakRow("Carry forward", line.carry)
                    BreakRow("+ Deposits", line.deposits)
                    BreakRow("+ Settlement payments", line.payments)
                    BreakRow("− Refunds", line.refunds)
                    BreakRow("+ Paid from pocket", line.paidOut)
                    BreakRow("= Total credited", line.totalCredit, bold = true)
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    BreakRow("− Expense share", line.expenseShare)
                    BreakRow("− Bazar share (equal)", line.bazarShare)
                    BreakRow("= Total share", line.totalShare, bold = true)
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Balance", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        BalanceBadge(line.balance)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PressButton("Deposit", { host.money("DEPOSIT", id, null) }, Modifier.weight(1f))
                PressButton("Pay due", { host.money("PAYMENT", id, line?.balance?.takeIf { it < 0 }?.let { -it }) }, Modifier.weight(1f), container = Electric, contentColor = Color.White)
                PressButton("Refund", { host.money("REFUND", id, line?.balance?.takeIf { it > 0 }) }, Modifier.weight(1f), container = Amber, contentColor = Color.Black)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PressButton("Statement PDF", {
                    val mess = boot.mess ?: return@PressButton
                    vm.run {
                        val f = withContext(Dispatchers.Default) { PdfReports.memberReport(ctx, mess, snap, sum, id) }
                        shareFile(ctx, f)
                    }
                }, Modifier.weight(1f), icon = Icons.Default.Share, enabled = line != null)
                PressButton("Print", {
                    val mess = boot.mess ?: return@PressButton
                    vm.run {
                        val f = withContext(Dispatchers.Default) { PdfReports.memberReport(ctx, mess, snap, sum, id) }
                        printFile(ctx, f)
                    }
                }, Modifier.weight(1f), container = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface, enabled = line != null)
                PressButton("Note", { host.note(null) }, Modifier.weight(1f), container = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)
            }
        }
        item { SectionTitle("Transaction history · ${Dates.monthLabel(month)}") }
        val deps = snap.deposits.filter { it.month == month && it.memberId == id }
        val pays = snap.payments.filter { it.month == month && it.memberId == id }
        val splitExp = snap.splits.filter { it.memberId == id }.mapNotNull { s -> snap.expenses.firstOrNull { it.id == s.expenseId && it.month == month }?.let { it to s.amount } }
        val buys = snap.bazar.filter { it.month == month && it.purchasedById == id }
        if (deps.isEmpty() && pays.isEmpty() && splitExp.isEmpty() && buys.isEmpty()) item { EmptyState("No transactions", "Nothing recorded for this member this month.") }
        items(deps, key = { "d${it.id}" }) { d ->
            HistoryRow("Deposit · ${Dates.fmtShort(d.day)}", d.note, d.amount, true) { delDeposit = d }
        }
        items(pays, key = { "p${it.id}" }) { p ->
            HistoryRow("${if (p.kind == "REFUND") "Refund" else "Payment"} · ${Dates.fmtShort(p.day)}", p.note, p.amount, p.kind == "PAYMENT") { delPayment = p }
        }
        items(buys, key = { "b${it.id}" }) { b -> HistoryRow("Bazar bought · ${Dates.fmtShort(b.day)}", "credited to member", snap.entryTotal(b), true, null) }
        items(splitExp, key = { "s${it.first.id}" }) { (e, a) -> HistoryRow("Share · ${e.description}", "${e.category} · ${Dates.fmtShort(e.day)}", a, false, null) }
    }
    delDeposit?.let { d -> ConfirmDialog("Delete deposit?", "৳ ${Money.format(d.amount)} will be removed. / জমা মুছে যাবে।", "Delete", { vm.run("Deleted") { vm.repo.deleteDeposit(d) } }, { delDeposit = null }) }
    delPayment?.let { p -> ConfirmDialog("Delete ${p.kind.lowercase()}?", "৳ ${Money.format(p.amount)} will be removed.", "Delete", { vm.run("Deleted") { vm.repo.deletePayment(p) } }, { delPayment = null }) }
}

@Composable
private fun HistoryRow(title: String, sub: String, amount: Long, plus: Boolean, onDelete: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)).padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text((if (plus) "+ " else "− ") + Money.format(amount), color = if (plus) Emerald else Danger, fontWeight = FontWeight.Bold)
        if (onDelete != null) IconButton(onDelete) { Icon(Icons.Default.Delete, "Delete") } else Spacer(Modifier.width(12.dp))
    }
}

@Composable
fun ReportsScreen(vm: AppVm) {
    val ctx = LocalContext.current
    val snap by vm.snapshot.collectAsState()
    val sum by vm.summary.collectAsState()
    val boot by vm.boot.collectAsState()
    val month by vm.month.collectAsState()
    var memberPick by remember { mutableStateOf<Long?>(null) }
    var confirmClose by remember { mutableStateOf(false) }
    var reopen by remember { mutableStateOf(false) }
    val blockers = remember(snap, month) { Accounting.closingBlockers(snap, month) }
    val cycle = snap.cycles.firstOrNull { it.month == month }

    fun make(kind: PdfReports.Kind, then: (File) -> Unit) {
        val mess = boot.mess ?: return
        vm.run {
            val f = withContext(Dispatchers.Default) { PdfReports.generate(ctx, mess, snap, sum, kind) }
            then(f)
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 160.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Reports", style = MaterialTheme.typography.headlineMedium); MonthBar(vm) }
        item {
            TiltCard(Modifier.fillMaxWidth(), if (sum.closed) Amber else Emerald) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (sum.closed) Icons.Default.Lock else Icons.Default.Info, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (sum.closed) "Month closed (read-only)" else "Month open", style = MaterialTheme.typography.titleMedium)
                }
                if (sum.closed) {
                    Text("Closed balances are carried forward to ${Dates.monthLabel(Dates.plus(month, 1))}. Reopened ${cycle?.reopenCount ?: 0} time(s).", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    PressButton("Reopen month / পুনরায় খুলুন", { reopen = true }, Modifier.fillMaxWidth(), container = Amber, contentColor = Color.Black)
                } else {
                    Text("Review: expense ৳ ${Money.format(sum.totalExpense)} · due ৳ ${Money.format(sum.totalDue)} · advance ৳ ${Money.format(sum.totalAdvance)}", style = MaterialTheme.typography.bodySmall)
                    blockers.forEach { Text("⚠ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(8.dp))
                    PressButton("Review & close month / মাস বন্ধ করুন", { confirmClose = true }, Modifier.fillMaxWidth(), icon = Icons.Default.Lock, enabled = blockers.isEmpty())
                }
            }
        }
        item { SectionTitle("PDF reports (A4)") }
        PdfReports.Kind.values().forEach { k ->
            item(key = k.name) {
                TiltCard(Modifier.fillMaxWidth(), Electric) {
                    Text(k.label, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PressButton("Share", { make(k) { shareFile(ctx, it) } }, Modifier.weight(1f), icon = Icons.Default.Share)
                        PressButton("Print", { make(k) { printFile(ctx, it) } }, Modifier.weight(1f), container = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        item {
            TiltCard(Modifier.fillMaxWidth(), Emerald) {
                Text("Individual member report", style = MaterialTheme.typography.titleMedium)
                val opts = sum.lines.map { it.member.id to it.member.name }
                if (opts.isEmpty()) Text("No members in this month.", style = MaterialTheme.typography.bodySmall)
                else {
                    val sel = memberPick?.takeIf { p -> opts.any { it.first == p } } ?: opts.first().first
                    DropdownField("Member", opts, sel, { memberPick = it })
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PressButton("Share", {
                            val mess = boot.mess ?: return@PressButton
                            vm.run { shareFile(ctx, withContext(Dispatchers.Default) { PdfReports.memberReport(ctx, mess, snap, sum, sel) }) }
                        }, Modifier.weight(1f), icon = Icons.Default.Share)
                        PressButton("Print", {
                            val mess = boot.mess ?: return@PressButton
                            vm.run { printFile(ctx, withContext(Dispatchers.Default) { PdfReports.memberReport(ctx, mess, snap, sum, sel) }) }
                        }, Modifier.weight(1f), container = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        item { SectionTitle("Category breakdown") }
        if (sum.categoryTotals.isEmpty()) item { EmptyState("No data", "Add expenses to see the breakdown.") }
        items(sum.categoryTotals.entries.sortedByDescending { it.value }.toList(), key = { "c" + it.key }) { e ->
            Column {
                Row { Text(e.key, Modifier.weight(1f)); Text("৳ ${Money.format(e.value)}", fontWeight = FontWeight.Bold) }
                ProgressBar(e.value.toFloat() / sum.totalExpense.coerceAtLeast(1), color = categoryColor(e.key))
            }
        }
    }

    if (confirmClose) ConfirmDialog(
        "Close ${Dates.monthLabel(month)}?",
        "Total ৳ ${Money.format(sum.totalExpense)}. Balances (due ৳ ${Money.format(sum.totalDue)}, advance ৳ ${Money.format(sum.totalAdvance)}) carry forward. The month becomes read-only. / মাস বন্ধ হলে পরিবর্তন করা যাবে না।",
        "Close month", { vm.run("Month closed / মাস বন্ধ") { vm.repo.closeMonth(month) } }, { confirmClose = false },
    )
    if (reopen) {
        var pin by remember { mutableStateOf("") }
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reopen = false }, title = { Text("Reopen ${Dates.monthLabel(month)}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Manager PIN and a reason are required. The change is recorded in the audit log. / পিন ও কারণ দিন।", style = MaterialTheme.typography.bodySmall)
                    AppField(pin, { pin = it.filter { c -> c.isDigit() } }, "PIN", password = true)
                    AppField(reason, { reason = it }, "Reason / কারণ")
                }
            },
            confirmButton = {
                TextButton({
                    vm.verifyPinThen(pin, { vm.toast("Wrong PIN / ভুল পিন") }) {
                        vm.run("Month reopened / মাস খোলা হয়েছে", { reopen = false }) { vm.repo.reopenMonth(month, reason.trim()) }
                    }
                }) { Text("Reopen") }
            },
            dismissButton = { TextButton({ reopen = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun NotesScreen(vm: AppVm, host: SheetHost, onBack: () -> Unit) {
    val notes by vm.notes.collectAsState()
    val snap by vm.snapshot.collectAsState()
    var q by remember { mutableStateOf("") }
    val names = snap.members.associate { it.id to it.name }
    val shown = notes.filter { n ->
        q.isBlank() || n.title.contains(q, true) || n.body.contains(q, true) || (n.memberId?.let { names[it] }?.contains(q, true) == true)
    }
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 160.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                Text("Notes / নোট", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                IconButton({ host.note(null) }) { Icon(Icons.Default.Add, "New note") }
            }
        }
        item {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), label = { Text("Search notes / খুঁজুন") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(14.dp))
        }
        if (shown.isEmpty()) item { EmptyState(if (notes.isEmpty()) "No notes yet" else "No matches", "Tap + to write a quick note.") }
        items(shown, key = { it.id }) { n ->
            TiltCard(Modifier.fillMaxWidth(), if (n.pinned) Amber else Electric, onClick = { host.note(n) }) {
                Column(Modifier.drawBehind {
                    var y = 26.dp.toPx()
                    while (y < size.height) { drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 1f); y += 24.dp.toPx() }
                    drawLine(Color(0x55F43F5E), Offset(14.dp.toPx(), 0f), Offset(14.dp.toPx(), size.height), 2f)
                }.padding(start = 24.dp)) {
                    Row {
                        Text(n.title.ifBlank { "Untitled" }, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (n.pinned) Text("📌")
                    }
                    Text(Dates.fmt(n.day) + listOfNotNull(
                        n.memberId?.let { " · ${names[it] ?: "member"}" }, n.expenseId?.let { " · expense" }, n.month?.let { " · ${Dates.monthLabel(it)}" },
                    ).joinToString(""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(n.body, maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
