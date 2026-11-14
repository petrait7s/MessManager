@file:OptIn(ExperimentalMaterial3Api::class)

package com.messmanager.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messmanager.app.data.Member
import com.messmanager.app.data.Mess
import com.messmanager.app.domain.Calc
import com.messmanager.app.domain.Dates
import com.messmanager.app.domain.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecoveryDialog(vm: AppVm) {
    val code by vm.recovery.collectAsState()
    val c = code ?: return
    AlertDialog(
        onDismissRequest = {}, title = { Text("Recovery code / রিকভারি কোড") },
        text = { Text("Write this down and keep it safe. It is the only way to reset a forgotten PIN.\nপিন ভুলে গেলে এই কোড দিয়েই রিসেট করা যাবে।\n\n$c", style = MaterialTheme.typography.titleMedium) },
        confirmButton = { TextButton({ vm.recovery.value = null; if (!vm.unlocked.value) vm.unlocked.value = true }) { Text("I saved it / সংরক্ষণ করেছি") } },
    )
}

@Composable
fun SetupScreen(vm: AppVm) {
    var step by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var manager by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    val members = remember { mutableStateListOf<Member>() }
    var mName by remember { mutableStateOf("") }
    var mPhone by remember { mutableStateOf("") }
    var mJoin by remember { mutableStateOf(Dates.today()) }
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("MessManager", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text("Private offline mess accounting · সম্পূর্ণ অফলাইন", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (step == 0) {
            Text("Step 1 of 2 · Mess & manager PIN", style = MaterialTheme.typography.titleMedium)
            AppField(name, { name = it }, "Mess name / মেসের নাম")
            AppField(address, { address = it }, "Address (optional) / ঠিকানা")
            AppField(manager, { manager = it }, "Manager name / ম্যানেজারের নাম")
            AppField(pin, { pin = it.filter { c -> c.isDigit() }.take(8) }, "PIN (4–8 digits) / পিন", password = true)
            AppField(pin2, { pin2 = it.filter { c -> c.isDigit() }.take(8) }, "Confirm PIN / পিন নিশ্চিত করুন", password = true)
            PressButton("Next / পরবর্তী", {
                when {
                    name.isBlank() || manager.isBlank() -> vm.toast("Mess and manager name required / মেস ও ম্যানেজারের নাম দিন")
                    pin.length < 4 -> vm.toast("PIN must be 4–8 digits / পিন ৪-৮ সংখ্যার হতে হবে")
                    pin != pin2 -> vm.toast("PINs do not match / পিন মিলছে না")
                    else -> step = 1
                }
            }, Modifier.fillMaxWidth())
        } else {
            Text("Step 2 of 2 · Members (${members.size}/10, recommended 5–10)", style = MaterialTheme.typography.titleMedium)
            members.forEachIndexed { i, m ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${m.name}${if (m.phone.isNotBlank()) " · ${m.phone}" else ""} · ${Dates.fmtShort(m.joinDay)}", Modifier.weight(1f))
                    IconButton({ members.removeAt(i) }) { Icon(Icons.Default.Delete, "Remove ${m.name}") }
                }
            }
            AppField(mName, { mName = it }, "Member name / নাম")
            AppField(mPhone, { mPhone = it }, "Phone (optional)")
            DateField("Joining date", mJoin, { mJoin = it })
            OutlinedButton({
                when {
                    mName.isBlank() -> vm.toast("Enter member name / নাম দিন")
                    members.size >= 10 -> vm.toast("Maximum 10 members / সর্বোচ্চ ১০ জন")
                    members.any { it.name.equals(mName.trim(), true) } -> vm.toast("Duplicate name / একই নাম আছে")
                    else -> { members.add(Member(name = mName.trim(), phone = mPhone.trim(), joinDay = mJoin)); mName = ""; mPhone = "" }
                }
            }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add member / সদস্য যোগ") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ step = 0 }, Modifier.weight(1f)) { Text("Back") }
                PressButton("Finish / শেষ", {
                    if (members.isEmpty()) { vm.toast("Add at least one member / অন্তত একজন সদস্য দিন"); return@PressButton }
                    vm.run {
                        val code = vm.repo.setupMess(Mess(name = name.trim(), address = address.trim(), managerName = manager.trim(), createdAt = System.currentTimeMillis()), members.toList(), pin)
                        vm.recovery.value = code
                    }
                }, Modifier.weight(2f), icon = Icons.Default.Check)
            }
        }
    }
}

@Composable
fun LockScreen(vm: AppVm) {
    val boot by vm.boot.collectAsState()
    var pin by remember { mutableStateOf("") }
    var forgot by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(boot.mess?.name ?: "MessManager", style = MaterialTheme.typography.headlineMedium)
        Text("Enter manager PIN / ম্যানেজার পিন দিন", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        AppField(pin, { pin = it.filter { c -> c.isDigit() }.take(8) }, "PIN", Modifier.fillMaxWidth(), password = true)
        Spacer(Modifier.height(12.dp))
        PressButton("Unlock / খুলুন", { vm.unlock(pin) { vm.toast("Wrong PIN / ভুল পিন"); pin = "" } }, Modifier.fillMaxWidth(), enabled = pin.length >= 4)
        TextButton({ forgot = true }) { Text("Forgot PIN? / পিন ভুলে গেছেন?") }
    }
    if (forgot) {
        var code by remember { mutableStateOf("") }
        var np by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { forgot = false }, title = { Text("Reset PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the 8-digit recovery code shown at setup. / সেটআপের রিকভারি কোড দিন।", style = MaterialTheme.typography.bodySmall)
                    AppField(code, { code = it.filter { c -> c.isDigit() } }, "Recovery code")
                    AppField(np, { np = it.filter { c -> c.isDigit() }.take(8) }, "New PIN", password = true)
                }
            },
            confirmButton = {
                TextButton({
                    vm.run(null, { forgot = false }) { vm.recovery.value = vm.repo.resetPinWithRecovery(code, np) }
                }) { Text("Reset") }
            },
            dismissButton = { TextButton({ forgot = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun SettingsScreen(vm: AppVm, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val boot by vm.boot.collectAsState()
    val p by vm.uiPrefs.collectAsState()
    val audit by vm.audit.collectAsState()
    var name by remember(boot.mess) { mutableStateOf(boot.mess?.name ?: "") }
    var addr by remember(boot.mess) { mutableStateOf(boot.mess?.address ?: "") }
    var mgr by remember(boot.mess) { mutableStateOf(boot.mess?.managerName ?: "") }
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var showAudit by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { u -> if (u != null) vm.exportTo(u) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) restoreUri = u }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
        }
        SectionTitle("Appearance")
        DropdownField("Theme", listOf(0 to "System", 1 to "Light", 2 to "Dark"), p.theme, { vm.run { vm.prefs.setTheme(it) } })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Reduce motion / কম অ্যানিমেশন", Modifier.weight(1f)); Switch(p.reduceMotion, { vm.run { vm.prefs.setReduceMotion(it) } })
        }
        SectionTitle("Mess info")
        AppField(name, { name = it }, "Mess name")
        AppField(addr, { addr = it }, "Address (optional)")
        AppField(mgr, { mgr = it }, "Manager name")
        PressButton("Save mess info", { boot.mess?.let { m -> vm.run("Saved / সংরক্ষিত") { vm.repo.updateMess(m.copy(name = name.trim(), address = addr.trim(), managerName = mgr.trim())) } } }, Modifier.fillMaxWidth())
        SectionTitle("Security")
        AppField(oldPin, { oldPin = it.filter { c -> c.isDigit() }.take(8) }, "Current PIN", password = true)
        AppField(newPin, { newPin = it.filter { c -> c.isDigit() }.take(8) }, "New PIN (4–8 digits)", password = true)
        PressButton("Change PIN / পিন পরিবর্তন", { vm.changePin(oldPin, newPin) { oldPin = ""; newPin = "" } }, Modifier.fillMaxWidth(), enabled = oldPin.length >= 4 && newPin.length >= 4)
        OutlinedButton({ vm.lock() }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Lock, null); Spacer(Modifier.width(8.dp)); Text("Lock now / এখনই লক") }
        Text("The app also locks automatically after 30 seconds in the background.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionTitle("Backup & restore (local only)")
        PressButton("Export backup (.zip)", { export.launch("messmanager-backup-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".zip") }, Modifier.fillMaxWidth())
        OutlinedButton({ import.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) }, Modifier.fillMaxWidth()) { Text("Import / restore backup") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Automatic daily local backup", Modifier.weight(1f)); Switch(p.autoBackup, { vm.run { vm.prefs.setAutoBackup(it) } })
        }
        OutlinedButton({ vm.run("Backup saved in app storage / ব্যাকআপ সংরক্ষিত") { vm.writeAutoBackup() } }, Modifier.fillMaxWidth()) { Text("Backup now (app storage)") }
        Text("Auto backups are kept on this device only (last 7). Nothing is uploaded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionTitle("History")
        OutlinedButton({ showAudit = true }, Modifier.fillMaxWidth()) { Text("View audit log (${audit.size})") }
        Text("MessManager 1.0 · fully offline · data stays on this device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    restoreUri?.let { u ->
        ConfirmDialog("Restore backup?", "ALL current data will be replaced by the backup. This cannot be undone. The file is validated first. / বর্তমান সব তথ্য ব্যাকআপ দিয়ে প্রতিস্থাপিত হবে।", "Replace data", { vm.restoreFrom(u) }, { restoreUri = null })
    }
    if (showAudit) Sheet("Audit log", { showAudit = false }) {
        if (audit.isEmpty()) Text("No entries yet.")
        val f = SimpleDateFormat("dd MMM HH:mm", Locale.ENGLISH)
        audit.forEach { a ->
            Text("${f.format(Date(a.ts))} · ${a.action} ${a.entity}${if (a.month.isNotBlank()) " [${a.month}]" else ""}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
            if (a.detail.isNotBlank()) Text(a.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CalculatorOverlay(modifier: Modifier, onExpense: (Long) -> Unit, onPayment: (Long) -> Unit) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var expr by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    val result: BigDecimal? = remember(expr) { try { if (expr.isBlank()) null else Calc.eval(expr) } catch (e: ArithmeticException) { null } }
    fun fmt(v: BigDecimal) = v.setScale(2, RoundingMode.HALF_UP).toPlainString().let { if (it.endsWith(".00")) it.dropLast(3) else it }
    fun press(k: String) {
        err = false
        when (k) {
            "C" -> expr = ""
            "⌫" -> expr = expr.dropLast(1)
            "=" -> {
                try { val r = Calc.eval(expr); if (r == null) err = true else expr = fmt(r) } catch (e: ArithmeticException) { err = true }
            }
            "+", "-", "×", "÷" -> {
                if (expr.isEmpty()) { if (k == "-") expr = "-" }
                else if (expr.last() in "+-×÷") expr = expr.dropLast(1) + k
                else expr += k
            }
            "." -> {
                val tail = expr.takeLastWhile { it.isDigit() || it == '.' }
                if (!tail.contains('.')) expr += if (tail.isEmpty()) "0." else "."
            }
            "%" -> if (expr.isNotEmpty() && (expr.last().isDigit() || expr.last() == '%')) expr += "%"
            else -> expr += k
        }
    }
    fun poisha(): Long? = result?.let { Money.parse(it.setScale(2, RoundingMode.HALF_UP).toPlainString()) }
    Box(modifier) {
        AnimatedVisibility(open, enter = fadeIn() + scaleIn(initialScale = 0.85f), exit = fadeOut() + scaleOut(targetScale = 0.85f), modifier = Modifier.padding(bottom = 56.dp)) {
            Surface(Modifier.width(280.dp), shape = RoundedCornerShape(24.dp), tonalElevation = 8.dp, shadowElevation = 16.dp, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(10.dp).semantics { contentDescription = "Calculator display" }, horizontalAlignment = Alignment.End) {
                        Text(expr.ifEmpty { "0" }, style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), maxLines = 1, textAlign = TextAlign.End)
                        Text(if (err) "Invalid / ভুল হিসাব" else result?.let { "= " + fmt(it) } ?: " ", style = MaterialTheme.typography.bodyMedium, color = if (err) MaterialTheme.colorScheme.error else Emerald)
                    }
                    listOf(listOf("C", "⌫", "%", "÷"), listOf("7", "8", "9", "×"), listOf("4", "5", "6", "-"), listOf("1", "2", "3", "+"), listOf("0", ".", "=", "Copy")).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { k ->
                                val op = k in listOf("÷", "×", "-", "+", "=")
                                FilledTonalButton(
                                    onClick = {
                                        if (k == "Copy") {
                                            val v = result?.let { fmt(it) } ?: expr
                                            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("result", v))
                                        } else press(k)
                                    },
                                    modifier = Modifier.weight(1f).height(44.dp).semantics { contentDescription = when (k) { "⌫" -> "Backspace"; "C" -> "Clear"; "Copy" -> "Copy result"; else -> k } },
                                    contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(12.dp),
                                    colors = if (op) ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) else ButtonDefaults.filledTonalButtonColors(),
                                ) { Text(k, fontWeight = FontWeight.Bold, fontSize = if (k == "Copy") 12.sp else 18.sp) }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton({ poisha()?.takeIf { it > 0 }?.let { open = false; onExpense(it) } }, Modifier.weight(1f), contentPadding = PaddingValues(4.dp), enabled = (poisha() ?: 0) > 0) { Text("+ Expense", fontSize = 12.sp) }
                        OutlinedButton({ poisha()?.takeIf { it > 0 }?.let { open = false; onPayment(it) } }, Modifier.weight(1f), contentPadding = PaddingValues(4.dp), enabled = (poisha() ?: 0) > 0) { Text("+ Payment", fontSize = 12.sp) }
                    }
                    Text("Calculator never changes your books unless you tap + Expense / + Payment and save.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        SmallFloatingActionButton({ open = !open }, Modifier.align(Alignment.BottomStart).semantics { contentDescription = "Calculator" }, containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary) {
            Text(if (open) "✕" else "±×", fontWeight = FontWeight.Bold)
        }
    }
}
