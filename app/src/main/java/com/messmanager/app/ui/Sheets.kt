@file:OptIn(ExperimentalMaterial3Api::class)

package com.messmanager.app.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.messmanager.app.data.*
import com.messmanager.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun defaultDay(month: String): Long {
    val t = Dates.today()
    return if (Dates.monthOf(t) == month) t else Dates.lastDay(month).coerceAtMost(Dates.firstDay(month) + 14)
}

@Composable
fun ExpenseSheet(vm: AppVm, expense: Expense?, initialAmount: Long?, onDismiss: () -> Unit) {
    val snap by vm.snapshot.collectAsState()
    val viewMonth by vm.month.collectAsState()
    var amount by remember { mutableStateOf(expense?.amount?.let { Money.plain(it) } ?: initialAmount?.let { Money.plain(it) } ?: "") }
    var category by remember { mutableStateOf(expense?.category ?: "Other") }
    var day by remember { mutableStateOf(expense?.day ?: defaultDay(viewMonth)) }
    var payer by remember { mutableStateOf(expense?.payerId) }
    var desc by remember { mutableStateOf(expense?.description ?: "") }
    var note by remember { mutableStateOf(expense?.note ?: "") }
    var mode by remember { mutableStateOf(expense?.splitMode ?: "EQUAL") }
    val oldSplits = remember(expense?.id) { snap.splits.filter { it.expenseId == expense?.id } }
    val month = Dates.monthOf(day)
    val people = remember(snap.members, month, oldSplits) {
        val base = Accounting.participants(snap.members, month)
        val extra = snap.members.filter { m -> oldSplits.any { it.memberId == m.id } && base.none { it.id == m.id } }
        (base + extra).sortedBy { it.id }
    }
    val included = remember(expense?.id) { mutableStateMapOf<Long, Boolean>() }
    val custom = remember(expense?.id) { mutableStateMapOf<Long, String>() }
    val weights = remember(expense?.id) { mutableStateMapOf<Long, String>() }
    LaunchedEffect(people.map { it.id }) {
        people.forEach { m ->
            if (m.id !in included) {
                val os = oldSplits.firstOrNull { it.memberId == m.id }
                included[m.id] = if (expense == null) true else os != null
                custom[m.id] = os?.let { Money.plain(it.amount) } ?: ""
                weights[m.id] = (os?.shares ?: m.shareWeight).toString()
            }
        }
    }
    var confirmDelete by remember { mutableStateOf(false) }

    Sheet(if (expense == null) "Add expense / খরচ যোগ করুন" else "Edit expense / খরচ সম্পাদনা", onDismiss) {
        AppField(amount, { amount = it }, "Amount (৳) / পরিমাণ", numeric = true)
        DropdownField("Category / ধরন", CATEGORIES.map { it to it }, category, { category = it })
        DateField("Date / তারিখ", day, { day = it })
        DropdownField("Paid by / কে দিয়েছে", listOf<Pair<Long?, String>>(null to "Mess fund (মেস ফান্ড)") + snap.members.map { it.id to it.name }, payer, { payer = it })
        AppField(desc, { desc = it }, "Description / বিবরণ")
        AppField(note, { note = it }, "Note (optional) / নোট", multiline = true)
        DropdownField("Split / ভাগ", listOf("EQUAL" to "Equal split", "SHARES" to "Fixed shares (weights)", "CUSTOM" to "Custom amounts"), mode, { mode = it })
        people.forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(included[m.id] ?: false, { included[m.id] = it })
                Text(m.name, Modifier.weight(1f))
                when (mode) {
                    "SHARES" -> OutlinedTextField(weights[m.id] ?: "1", { weights[m.id] = it.filter { c -> c.isDigit() }.take(2) }, Modifier.width(90.dp), label = { Text("Shares") }, singleLine = true)
                    "CUSTOM" -> OutlinedTextField(custom[m.id] ?: "", { custom[m.id] = it }, Modifier.width(130.dp), label = { Text("Amount") }, singleLine = true)
                }
            }
        }
        val amt = Money.parse(amount)
        if (mode == "CUSTOM" && amt != null) {
            val s = people.filter { included[it.id] == true }.sumOf { Money.parse(custom[it.id] ?: "") ?: 0L }
            Text("Assigned ${Money.format(s)} of ${Money.format(amt)} — left ${Money.format(amt - s)}", style = MaterialTheme.typography.bodySmall)
        }
        PressButton("Save / সংরক্ষণ", {
            val a = Money.parse(amount)
            val ids = people.filter { included[it.id] == true }.map { it.id }
            if (a == null || a <= 0) { vm.toast("Enter a valid amount / সঠিক পরিমাণ দিন"); return@PressButton }
            if (ids.isEmpty()) { vm.toast("Select at least one member / অন্তত একজন নির্বাচন করুন"); return@PressButton }
            val sh = ids.associateWith { (weights[it]?.toIntOrNull() ?: 1).coerceAtLeast(1) }
            val splits = when (mode) {
                "SHARES" -> Accounting.splitWeighted(a, sh)
                "CUSTOM" -> ids.associateWith { Money.parse(custom[it] ?: "") ?: 0L }
                else -> Accounting.splitEqual(a, ids)
            }
            val e = Expense(id = expense?.id ?: 0, month = month, day = day, category = category, amount = a, payerId = payer, description = desc.trim(),
                note = note.trim(), splitMode = mode, createdAt = 0, updatedAt = 0)
            vm.run("Saved / সংরক্ষিত", onDismiss) { vm.repo.saveExpense(e, splits, if (mode == "SHARES") sh else emptyMap()) }
        }, Modifier.fillMaxWidth(), icon = Icons.Default.Check)
        if (expense != null) {
            OutlinedButton({ confirmDelete = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(8.dp)); Text("Delete / মুছুন") }
        }
    }
    if (confirmDelete && expense != null) ConfirmDialog("Delete expense?", "This removes the expense and its split. An audit entry is kept. / খরচটি মুছে যাবে।", "Delete", { vm.run("Deleted / মুছে ফেলা হয়েছে", onDismiss) { vm.repo.deleteExpense(expense) } }, { confirmDelete = false })
}

data class ItemDraft(val name: String = "", val qty: String = "1", val unit: String = "kg", val price: String = "")

@Composable
fun BazarSheet(vm: AppVm, entry: BazarEntry?, onDismiss: () -> Unit) {
    val snap by vm.snapshot.collectAsState()
    val viewMonth by vm.month.collectAsState()
    var day by remember { mutableStateOf(entry?.day ?: defaultDay(viewMonth)) }
    var buyer by remember { mutableStateOf(entry?.purchasedById) }
    var note by remember { mutableStateOf(entry?.note ?: "") }
    var receipt by remember { mutableStateOf(entry?.receipt) }
    val drafts = remember {
        mutableStateListOf<ItemDraft>().also { l ->
            val old = entry?.let { snap.itemsByEntry[it.id].orEmpty() }.orEmpty()
            if (old.isEmpty()) l.add(ItemDraft()) else old.forEach { l.add(ItemDraft(it.name, Money.qtyText(it.qtyMilli), it.unit, Money.plain(it.unitPrice))) }
        }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.run { receipt = withContext(Dispatchers.IO) { vm.repo.saveReceipt(uri) } }
    }
    fun total(d: ItemDraft): Long {
        val q = Money.parseQty(d.qty) ?: return 0L
        val p = Money.parse(d.price) ?: return 0L
        return Money.mulQty(q, p)
    }
    val units = listOf("kg", "g", "L", "ml", "pcs", "dozen", "packet", "other")
    Sheet(if (entry == null) "Add bazar / বাজার যোগ করুন" else "Edit bazar / বাজার সম্পাদনা", onDismiss) {
        DateField("Date / তারিখ", day, { day = it })
        DropdownField("Purchased by / কে কিনেছে", listOf<Pair<Long?, String>>(null to "Mess fund (মেস ফান্ড)") + snap.members.map { it.id to it.name }, buyer, { buyer = it })
        drafts.forEachIndexed { i, d ->
            Text("Item ${i + 1}", style = MaterialTheme.typography.labelLarge)
            AppField(d.name, { drafts[i] = d.copy(name = it) }, "Item name / আইটেম")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppField(d.qty, { drafts[i] = d.copy(qty = it) }, "Qty", Modifier.weight(1f), numeric = true)
                DropdownField("Unit", units.map { it to it }, d.unit, { drafts[i] = d.copy(unit = it) }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppField(d.price, { drafts[i] = d.copy(price = it) }, "Unit price ৳", Modifier.weight(1f), numeric = true)
                Text("= ৳ ${Money.format(total(d))}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (drafts.size > 1) IconButton({ drafts.removeAt(i) }) { Icon(Icons.Default.Delete, "Remove item") }
            }
        }
        OutlinedButton({ drafts.add(ItemDraft()) }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add item / আইটেম যোগ") }
        Text("Total: ৳ ${Money.format(drafts.sumOf { total(it) })}", style = MaterialTheme.typography.titleMedium)
        AppField(note, { note = it }, "Note (optional)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton({ picker.launch("image/*") }) { Text(if (receipt == null) "Attach receipt / রসিদ" else "Replace receipt") }
            if (receipt != null) TextButton({ receipt = null }) { Text("Remove") }
        }
        receipt?.let { r ->
            val bmp = remember(r) { try { BitmapFactory.decodeFile(vm.repo.receiptFile(r).path, BitmapFactory.Options().apply { inSampleSize = 4 }) } catch (e: Exception) { null } }
            if (bmp != null) Image(bmp.asImageBitmap(), "Receipt image", Modifier.height(140.dp))
        }
        PressButton("Save / সংরক্ষণ", {
            val items = drafts.map {
                val q = Money.parseQty(it.qty); val p = Money.parse(it.price)
                if (q == null || p == null) { vm.toast("Check quantity and price for '${it.name}' / পরিমাণ ও দাম দেখুন"); return@PressButton }
                BazarItem(entryId = 0, name = it.name.trim(), qtyMilli = q, unit = it.unit, unitPrice = p, total = Money.mulQty(q, p))
            }
            vm.run("Saved / সংরক্ষিত", onDismiss) {
                vm.repo.saveBazar(BazarEntry(id = entry?.id ?: 0, month = Dates.monthOf(day), day = day, purchasedById = buyer, note = note.trim(), receipt = receipt, createdAt = 0), items)
            }
        }, Modifier.fillMaxWidth(), icon = Icons.Default.Check)
        if (entry != null) OutlinedButton({ confirmDelete = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(8.dp)); Text("Delete / মুছুন") }
    }
    if (confirmDelete && entry != null) ConfirmDialog("Delete bazar entry?", "All its items will be removed. / এন্ট্রি ও আইটেম মুছে যাবে।", "Delete", { vm.run("Deleted", onDismiss) { vm.repo.deleteBazar(entry) } }, { confirmDelete = false })
}

/** kind: DEPOSIT | PAYMENT | REFUND */
@Composable
fun MoneySheet(vm: AppVm, kind: String, memberId: Long?, initialAmount: Long?, onDismiss: () -> Unit) {
    val snap by vm.snapshot.collectAsState()
    val viewMonth by vm.month.collectAsState()
    var member by remember { mutableStateOf(memberId ?: snap.members.firstOrNull { it.isActive() }?.id ?: snap.members.firstOrNull()?.id ?: 0L) }
    var day by remember { mutableStateOf(defaultDay(viewMonth)) }
    var amount by remember { mutableStateOf(initialAmount?.let { Money.plain(it) } ?: "") }
    var note by remember { mutableStateOf("") }
    val title = when (kind) { "DEPOSIT" -> "Member deposit / জমা"; "PAYMENT" -> "Due payment / বকেয়া পরিশোধ"; else -> "Refund / ফেরত" }
    Sheet(title, onDismiss) {
        DropdownField("Member / সদস্য", snap.members.map { it.id to it.name }, member, { member = it })
        DateField("Date / তারিখ", day, { day = it })
        AppField(amount, { amount = it }, "Amount (৳) / পরিমাণ", numeric = true)
        AppField(note, { note = it }, "Note (optional)")
        PressButton("Save / সংরক্ষণ", {
            val a = Money.parse(amount)
            if (a == null || a <= 0) { vm.toast("Enter a valid amount / সঠিক পরিমাণ দিন"); return@PressButton }
            vm.run("Saved / সংরক্ষিত", onDismiss) {
                if (kind == "DEPOSIT") vm.repo.addDeposit(member, day, a, note.trim()) else vm.repo.addPayment(member, day, a, kind, note.trim())
            }
        }, Modifier.fillMaxWidth(), icon = Icons.Default.Check)
    }
}

@Composable
fun MemberSheet(vm: AppVm, member: Member?, onDismiss: () -> Unit) {
    val month by vm.month.collectAsState()
    var name by remember { mutableStateOf(member?.name ?: "") }
    var phone by remember { mutableStateOf(member?.phone ?: "") }
    var join by remember { mutableStateOf(member?.joinDay ?: Dates.today()) }
    var weight by remember { mutableStateOf((member?.shareWeight ?: 1).toString()) }
    var active by remember { mutableStateOf(member?.isActive() ?: true) }
    var confirmDelete by remember { mutableStateOf(false) }
    Sheet(if (member == null) "Add member / সদস্য যোগ" else "Edit member / সদস্য সম্পাদনা", onDismiss) {
        AppField(name, { name = it }, "Name / নাম")
        AppField(phone, { phone = it }, "Phone (optional) / ফোন")
        DateField("Joining date / যোগদানের তারিখ", join, { join = it })
        AppField(weight, { weight = it.filter { c -> c.isDigit() }.take(2) }, "Fixed share weight (default 1)")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Active / সক্রিয়", Modifier.weight(1f)); Switch(active, { active = it })
        }
        PressButton("Save / সংরক্ষণ", {
            val old = member
            val m = Member(id = old?.id ?: 0, name = name.trim(), phone = phone.trim(), joinDay = join,
                inactiveFrom = if (active) null else (old?.inactiveFrom ?: month), shareWeight = weight.toIntOrNull() ?: 1, createdAt = old?.createdAt ?: 0)
            vm.run("Saved / সংরক্ষিত", onDismiss) { vm.repo.saveMember(m, month) }
        }, Modifier.fillMaxWidth(), icon = Icons.Default.Check)
        if (member != null) OutlinedButton({ confirmDelete = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(8.dp)); Text("Delete member / মুছুন") }
    }
    if (confirmDelete && member != null) ConfirmDialog("Delete member?", "Only possible when the member has no history; otherwise deactivate. / ইতিহাস থাকলে মোছা যাবে না।", "Delete", { vm.run("Deleted", onDismiss) { vm.repo.deleteMember(member) } }, { confirmDelete = false })
}

@Composable
fun NoteSheet(vm: AppVm, note: Note?, presetMember: Long? = null, onDismiss: () -> Unit) {
    val snap by vm.snapshot.collectAsState()
    val month by vm.month.collectAsState()
    var title by remember { mutableStateOf(note?.title ?: "") }
    var body by remember { mutableStateOf(note?.body ?: "") }
    var pinned by remember { mutableStateOf(note?.pinned ?: false) }
    var day by remember { mutableStateOf(note?.day ?: Dates.today()) }
    var link by remember { mutableStateOf(if (note?.memberId != null || presetMember != null) "MEMBER" else if (note?.expenseId != null) "EXPENSE" else if (note?.month != null) "MONTH" else "NONE") }
    var memberId by remember { mutableStateOf(note?.memberId ?: presetMember ?: snap.members.firstOrNull()?.id ?: 0L) }
    val monthExpenses = snap.expenses.filter { it.month == month || it.id == note?.expenseId }
    var expenseId by remember { mutableStateOf(note?.expenseId ?: monthExpenses.firstOrNull()?.id ?: 0L) }
    var confirmDelete by remember { mutableStateOf(false) }
    Sheet(if (note == null) "New note / নতুন নোট" else "Edit note / নোট সম্পাদনা", onDismiss) {
        AppField(title, { title = it }, "Title / শিরোনাম")
        AppField(body, { body = it }, "Note / লিখুন", multiline = true)
        DateField("Date / তারিখ", day, { day = it })
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Pinned / পিন করা", Modifier.weight(1f)); Switch(pinned, { pinned = it }) }
        DropdownField("Link to / সংযুক্ত", listOf("NONE" to "Nothing", "MEMBER" to "Member", "EXPENSE" to "Expense", "MONTH" to "Month ($month)"), link, { link = it })
        if (link == "MEMBER") DropdownField("Member", snap.members.map { it.id to it.name }, memberId, { memberId = it })
        if (link == "EXPENSE") {
            if (monthExpenses.isEmpty()) Text("No expenses this month", style = MaterialTheme.typography.bodySmall)
            else DropdownField("Expense", monthExpenses.map { it.id to "${it.description} · ${Money.format(it.amount)}" }, expenseId, { expenseId = it })
        }
        PressButton("Save / সংরক্ষণ", {
            val n = Note(id = note?.id ?: 0, title = title.trim(), body = body.trim(), pinned = pinned, day = day,
                memberId = if (link == "MEMBER") memberId else null, expenseId = if (link == "EXPENSE" && monthExpenses.isNotEmpty()) expenseId else null,
                month = if (link == "MONTH") month else null, createdAt = note?.createdAt ?: 0, updatedAt = 0)
            vm.run("Saved / সংরক্ষিত", onDismiss) { vm.repo.saveNote(n) }
        }, Modifier.fillMaxWidth(), icon = Icons.Default.Check)
        if (note != null) OutlinedButton({ confirmDelete = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(8.dp)); Text("Delete / মুছুন") }
    }
    if (confirmDelete && note != null) ConfirmDialog("Delete note?", "This cannot be undone. / এটি ফিরিয়ে আনা যাবে না।", "Delete", { vm.run("Deleted", onDismiss) { vm.repo.deleteNote(note.id) } }, { confirmDelete = false })
}

@Composable
fun DutySheet(vm: AppVm, day: Long, onDismiss: () -> Unit) {
    val snap by vm.snapshot.collectAsState()
    val duties by vm.duties.collectAsState()
    val cur = duties.firstOrNull { it.day == day }
    val month = Dates.monthOf(day)
    val people = Accounting.participants(snap.members, month)
    var member by remember { mutableStateOf(cur?.memberId ?: people.firstOrNull()?.id ?: 0L) }
    var status by remember { mutableStateOf(cur?.status ?: "PENDING") }
    Sheet("Bazar duty · ${Dates.fmt(day)}", onDismiss) {
        if (people.isEmpty()) { Text("No active members / কোনো সক্রিয় সদস্য নেই"); return@Sheet }
        DropdownField("Assigned to / দায়িত্বে", people.map { it.id to it.name }, member, { member = it })
        DropdownField("Status / অবস্থা", listOf("PENDING" to "Pending", "COMPLETED" to "Completed", "SKIPPED" to "Skipped"), status, { status = it })
        if (status == "SKIPPED") {
            OutlinedButton({
                val next = DutyRotation.nextAfter(people.map { it.id }, member)
                if (next != null) { member = next; status = "PENDING" }
            }, Modifier.fillMaxWidth()) { Text("Reassign to next member / পরের সদস্যকে দিন") }
        }
        PressButton("Save / সংরক্ষণ", { vm.run("Saved", onDismiss) { vm.repo.setDuty(day, member, status) } }, Modifier.fillMaxWidth(), icon = Icons.Default.Check)
        if (cur != null) OutlinedButton({ vm.run("Cleared", onDismiss) { vm.repo.clearDuty(day) } }, Modifier.fillMaxWidth()) { Text("Clear duty / মুছুন") }
    }
}
