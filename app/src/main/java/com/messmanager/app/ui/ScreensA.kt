@file:OptIn(ExperimentalMaterial3Api::class)

package com.messmanager.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messmanager.app.data.*
import com.messmanager.app.domain.*
import java.time.LocalDate

class SheetHost(
    val expense: (Expense?) -> Unit,
    val bazar: (BazarEntry?) -> Unit,
    val money: (String, Long?, Long?) -> Unit, // kind, memberId, amount
    val member: (Member?) -> Unit,
    val note: (Note?) -> Unit,
    val duty: (Long) -> Unit,
    val openMember: (Long) -> Unit,
    val settings: () -> Unit,
    val notes: () -> Unit,
)

@Composable
fun MonthBar(vm: AppVm) {
    val month by vm.month.collectAsState()
    val sum by vm.summary.collectAsState()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton({ vm.shiftMonth(-1) }) { Icon(Icons.Default.ArrowBack, "Previous month") }
        Text(Dates.monthLabel(month), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        if (sum.closed) Icon(Icons.Default.Lock, "Month closed", Modifier.size(18.dp), tint = Amber)
        IconButton({ vm.shiftMonth(1) }) { Icon(Icons.Default.ArrowForward, "Next month") }
    }
}

private data class Tx(val title: String, val sub: String, val amount: Long, val plus: Boolean, val day: Long, val ts: Long)

@Composable
fun HomeScreen(vm: AppVm, host: SheetHost) {
    val boot by vm.boot.collectAsState()
    val sum by vm.summary.collectAsState()
    val snap by vm.snapshot.collectAsState()
    val month by vm.month.collectAsState()
    val duties by vm.duties.collectAsState()
    val names = snap.members.associate { it.id to it.name }
    val today = Dates.today()
    val daysIn = Dates.daysIn(month)
    val progress = when {
        month == Dates.monthOf(today) -> Dates.date(today).dayOfMonth.toFloat() / daysIn
        month < Dates.monthOf(today) -> 1f else -> 0f
    }
    val txs = remember(snap, month) {
        val l = mutableListOf<Tx>()
        snap.expenses.filter { it.month == month }.forEach { l.add(Tx(it.description, "${it.category} · ${Dates.fmtShort(it.day)}", it.amount, false, it.day, it.createdAt)) }
        snap.bazar.filter { it.month == month }.forEach { l.add(Tx("Bazar", "${it.purchasedById?.let { p -> names[p] } ?: "Mess fund"} · ${Dates.fmtShort(it.day)}", snap.entryTotal(it), false, it.day, it.createdAt)) }
        snap.deposits.filter { it.month == month }.forEach { l.add(Tx("Deposit · ${names[it.memberId] ?: ""}", Dates.fmtShort(it.day), it.amount, true, it.day, it.createdAt)) }
        snap.payments.filter { it.month == month }.forEach { l.add(Tx("${if (it.kind == "REFUND") "Refund" else "Payment"} · ${names[it.memberId] ?: ""}", Dates.fmtShort(it.day), it.amount, it.kind == "PAYMENT", it.day, it.createdAt)) }
        l.sortedWith(compareByDescending<Tx> { it.day }.thenByDescending { it.ts }).take(8)
    }
    val nextDuty = duties.filter { it.day >= maxOf(today, Dates.firstDay(month)) && it.status == "PENDING" && it.month == month }.minByOrNull { it.day }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 160.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(boot.mess?.name ?: "MessManager", style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Manager: ${boot.mess?.managerName ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(host.notes) { Icon(Icons.Default.Create, "Notes") }
                IconButton(host.settings) { Icon(Icons.Default.Settings, "Settings") }
            }
        }
        item { MonthBar(vm) }
        item {
            TiltCard(Modifier.fillMaxWidth(), Emerald) {
                Text("Total expenses this month", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MoneyText(sum.totalExpense, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 34.sp))
                Spacer(Modifier.height(10.dp))
                ProgressBar(progress)
                Text("Month progress: ${(progress * 100).toInt()}%${if (sum.closed) " · CLOSED" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) { Text("Per-member due", style = MaterialTheme.typography.bodySmall); MoneyText(sum.totalDue, style = MaterialTheme.typography.titleMedium, color = Danger) }
                    Column(Modifier.weight(1f)) { Text("Per-member advance", style = MaterialTheme.typography.bodySmall); MoneyText(sum.totalAdvance, style = MaterialTheme.typography.titleMedium, color = Emerald) }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStat("Bazar", sum.bazarTotal, Emerald, Modifier.weight(1f))
                MiniStat("Fixed bills", sum.fixedBills, Electric, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStat("Member deposits", sum.collected, Amber, Modifier.weight(1f))
                MiniStat("Other expenses", sum.expenseOnly - sum.fixedBills, Color(0xFF8B5CF6), Modifier.weight(1f))
            }
        }
        item {
            SectionTitle("Quick actions")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PressButton("Expense", { host.expense(null) }, Modifier.weight(1f), icon = Icons.Default.Add)
                PressButton("Bazar", { host.bazar(null) }, Modifier.weight(1f), icon = Icons.Default.ShoppingCart, container = Electric, contentColor = Color.White)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PressButton("Deposit", { host.money("DEPOSIT", null, null) }, Modifier.weight(1f), icon = Icons.Default.Person, container = Amber, contentColor = Color.Black)
                PressButton("Note", { host.note(null) }, Modifier.weight(1f), icon = Icons.Default.Create, container = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)
            }
        }
        item {
            SectionTitle("Daily spending")
            TiltCard(Modifier.fillMaxWidth(), Electric) {
                if (sum.dailyTotals.isEmpty()) EmptyState("No spending yet", "Add an expense or bazar entry to see the chart.")
                else BarChart((1..daysIn).map { d -> d.toString() to (sum.dailyTotals[Dates.firstDay(month) + d - 1] ?: 0L) })
            }
        }
        item {
            SectionTitle("Next bazar duty")
            TiltCard(Modifier.fillMaxWidth(), Amber, onClick = { nextDuty?.let { host.duty(it.day) } }) {
                if (nextDuty == null) Text("No pending duty. Generate the rotation in the Bazar tab. / বাজার ট্যাবে রোটেশন তৈরি করুন।", style = MaterialTheme.typography.bodyMedium)
                else {
                    Text(names[nextDuty.memberId] ?: "?", style = MaterialTheme.typography.titleLarge)
                    Text(Dates.fmt(nextDuty.day), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { SectionTitle("Member balances") }
        if (sum.lines.isEmpty()) item { EmptyState("No members", "Add members in the Members tab.") }
        items(sum.lines, key = { it.member.id }) { l ->
            TiltCard(Modifier.fillMaxWidth(), if (l.balance < 0) Danger else Emerald, onClick = { host.openMember(l.member.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(l.member.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BalanceBadge(l.balance)
                }
            }
        }
        item { SectionTitle("Recent transactions") }
        if (txs.isEmpty()) item { EmptyState("No transactions", "Everything you add this month appears here.") }
        items(txs) { t ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(t.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text((if (t.plus) "+ " else "- ") + Money.format(t.amount), color = if (t.plus) Emerald else Danger, fontWeight = FontWeight.Bold)
            }
        }
    }
}


@Composable
fun BazarScreen(vm: AppVm, host: SheetHost) {
    var tab by remember { mutableStateOf(0) }
    val sum by vm.summary.collectAsState()
    val snap by vm.snapshot.collectAsState()
    val month by vm.month.collectAsState()
    val names = snap.members.associate { it.id to it.name }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Bazar", style = MaterialTheme.typography.headlineMedium)
            MonthBar(vm)
        }
        TabRow(tab, containerColor = Color.Transparent) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Entries") })
            Tab(tab == 1, { tab = 1 }, text = { Text("Duty calendar") })
        }
        if (tab == 0) {
            val entries = snap.bazar.filter { it.month == month }.sortedWith(compareByDescending<BazarEntry> { it.day }.thenByDescending { it.id })
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 160.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    TiltCard(Modifier.fillMaxWidth(), Emerald) {
                        Text("Monthly bazar total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MoneyText(sum.bazarTotal, style = MaterialTheme.typography.headlineMedium)
                        Text("${entries.size} entries", style = MaterialTheme.typography.bodySmall)
                    }
                }
                item { PressButton("Add bazar / বাজার যোগ করুন", { host.bazar(null) }, Modifier.fillMaxWidth(), icon = Icons.Default.Add) }
                if (entries.isEmpty()) item { EmptyState("No bazar entries", "Tap Add bazar to record today's shopping.") }
                entries.groupBy { it.day }.forEach { (day, list) ->
                    item(key = "h$day") {
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            Text(Dates.fmt(day), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Text("Day total ৳ ${Money.format(list.sumOf { snap.entryTotal(it) })}", color = Emerald, fontWeight = FontWeight.Bold)
                        }
                    }
                    items(list, key = { "e${it.id}" }) { e ->
                        TiltCard(Modifier.fillMaxWidth(), Emerald, onClick = { host.bazar(e) }) {
                            Row {
                                Text(e.purchasedById?.let { names[it] } ?: "Mess fund", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Text("৳ ${Money.format(snap.entryTotal(e))}", fontWeight = FontWeight.Bold)
                            }
                            snap.itemsByEntry[e.id].orEmpty().forEach {
                                Text("• ${it.name} — ${Money.qtyText(it.qtyMilli)} ${it.unit} × ${Money.format(it.unitPrice)} = ${Money.format(it.total)}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (e.receipt != null) Text("Receipt attached", style = MaterialTheme.typography.bodySmall, color = Electric)
                            if (e.note.isNotBlank()) Text(e.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else DutyCalendar(vm, host)
    }
}

@Composable
fun DutyCalendar(vm: AppVm, host: SheetHost) {
    val month by vm.month.collectAsState()
    val duties by vm.duties.collectAsState()
    val snap by vm.snapshot.collectAsState()
    val names = snap.members.associate { it.id to it.name }
    val first = Dates.firstDay(month)
    val offset = Dates.date(first).dayOfWeek.value - 1
    val days = Dates.daysIn(month)
    val byDay = duties.filter { it.month == month }.associateBy { it.day }
    val today = Dates.today()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 160.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            PressButton("Auto-generate rotation / স্বয়ংক্রিয় রোটেশন", { vm.run("Rotation generated / রোটেশন তৈরি") { vm.repo.generateDuties(month) } }, Modifier.fillMaxWidth(), icon = Icons.Default.Refresh)
            Text("Fills only days without a duty; manual changes are kept. Tap a day to reassign or mark Completed / Skipped.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        item {
            Row(Modifier.fillMaxWidth()) { listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall) } }
        }
        val rows = (offset + days + 6) / 7
        items(rows) { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0 until 7) {
                    val dn = r * 7 + c - offset + 1
                    if (dn < 1 || dn > days) { Spacer(Modifier.weight(1f)); continue }
                    val day = first + dn - 1
                    val d = byDay[day]
                    val col = when (d?.status) { "COMPLETED" -> Emerald; "SKIPPED" -> Amber; "PENDING" -> Electric; else -> MaterialTheme.colorScheme.outline }
                    Column(
                        Modifier.weight(1f).height(62.dp).clip(RoundedCornerShape(10.dp))
                            .background(col.copy(alpha = if (d == null) 0.06f else 0.2f))
                            .border(if (day == today) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, col.copy(alpha = .5f)), RoundedCornerShape(10.dp))
                            .clickable { host.duty(day) }
                            .semantics { contentDescription = "Day $dn ${d?.let { names[it.memberId] + " " + it.status } ?: "no duty"}" }
                            .padding(3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(dn.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        Text(d?.let { names[it.memberId]?.substringBefore(" ") } ?: "—", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(when (d?.status) { "COMPLETED" -> "✓"; "SKIPPED" -> "skip"; else -> "" }, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        item {
            SectionTitle("Duty summary")
            val mine = byDay.values.groupBy { it.memberId }
            if (mine.isEmpty()) Text("No duties yet.", style = MaterialTheme.typography.bodySmall)
            mine.forEach { (id, l) ->
                Text("${names[id]}: ${l.size} duties · ${l.count { it.status == "COMPLETED" }} done · ${l.count { it.status == "SKIPPED" }} skipped", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun ExpensesScreen(vm: AppVm, host: SheetHost) {
    val snap by vm.snapshot.collectAsState()
    val month by vm.month.collectAsState()
    val sum by vm.summary.collectAsState()
    var filter by remember { mutableStateOf<String?>(null) }
    val names = snap.members.associate { it.id to it.name }
    val list = snap.expenses.filter { it.month == month && (filter == null || it.category == filter) }.sortedWith(compareByDescending<Expense> { it.day }.thenByDescending { it.id })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 160.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Expenses", style = MaterialTheme.typography.headlineMedium); MonthBar(vm) }
        item {
            TiltCard(Modifier.fillMaxWidth(), Electric) {
                Text("Expenses (excluding itemised bazar)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MoneyText(sum.expenseOnly, style = MaterialTheme.typography.headlineMedium)
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(filter == null, { filter = null }, { Text("All") }) }
                items(CATEGORIES) { c -> FilterChip(filter == c, { filter = if (filter == c) null else c }, { Text(c) }) }
            }
        }
        if (list.isEmpty()) item { EmptyState("No expenses", "Use the + button to record rent, bills and more.") }
        items(list, key = { it.id }) { e ->
            TiltCard(Modifier.fillMaxWidth(), categoryColor(e.category), onClick = { host.expense(e) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(categoryColor(e.category)))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.description, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${e.category} · ${Dates.fmtShort(e.day)} · ${e.payerId?.let { names[it] } ?: "Mess fund"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("৳ ${Money.format(e.amount)}", fontWeight = FontWeight.Bold)
                }
                if (e.note.isNotBlank()) Text(e.note, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
