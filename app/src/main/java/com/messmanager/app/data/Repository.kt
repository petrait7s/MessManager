package com.messmanager.app.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.messmanager.app.domain.Accounting
import com.messmanager.app.domain.Dates
import com.messmanager.app.domain.DutyRotation
import com.messmanager.app.domain.Snapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

class ClosedMonthException(month: String) : Exception("Month $month is closed (read-only) / মাস $month বন্ধ, পরিবর্তন করা যাবে না")
class ValidationException(msg: String) : Exception(msg)

object Security {
    fun hash(pin: String, salt: String): String {
        var d = (salt + pin).toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        repeat(5000) { d = md.digest(d) }
        return d.joinToString("") { "%02x".format(it) }
    }
    fun newSalt(): String {
        val b = ByteArray(16); SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }
    fun newRecoveryCode(): String = (SecureRandom().nextInt(90000000) + 10000000).toString()
}

class Repository(private val ctx: Context, val db: AppDatabase) {
    val dao = db.dao()

    @Suppress("UNCHECKED_CAST")
    val snapshot: Flow<Snapshot> = combine(
        listOf<Flow<List<Any?>>>(
            dao.observeMembers(), dao.observeExpenses(), dao.observeSplits(), dao.observeDeposits(),
            dao.observePayments(), dao.observeBazar(), dao.observeItems(), dao.observeCycles(),
        )
    ) { a ->
        Snapshot(
            a[0] as List<Member>, a[1] as List<Expense>, a[2] as List<ExpenseSplit>, a[3] as List<Deposit>,
            a[4] as List<Payment>, a[5] as List<BazarEntry>, a[6] as List<BazarItem>, a[7] as List<MonthlyCycle>,
        )
    }

    suspend fun loadSnapshot(): Snapshot = Snapshot(
        dao.allMembers(), dao.allExpenses(), dao.allSplits(), dao.allDeposits(), dao.allPayments(),
        dao.allBazar(), dao.allItems(), dao.allCycles(),
    )

    private suspend fun guard(month: String) {
        if (dao.cycle(month)?.status == "CLOSED") throw ClosedMonthException(month)
    }

    suspend fun audit(action: String, entity: String, id: Long, detail: String, month: String) {
        dao.insertAudit(AuditLog(ts = System.currentTimeMillis(), action = action, entity = entity, entityId = id, detail = detail, month = month))
    }

    // ---------- settings / mess ----------
    suspend fun setting(k: String): String? = dao.setting(k)
    suspend fun putSetting(k: String, v: String) = dao.putSetting(AppSettings(k, v))

    suspend fun setupMess(mess: Mess, members: List<Member>, pin: String): String {
        if (mess.name.isBlank()) throw ValidationException("Mess name required / মেসের নাম দিন")
        if (mess.managerName.isBlank()) throw ValidationException("Manager name required / ম্যানেজারের নাম দিন")
        if (members.size > 10) throw ValidationException("Maximum 10 members / সর্বোচ্চ ১০ জন সদস্য")
        val code = Security.newRecoveryCode()
        db.withTransaction {
            dao.upsertMess(mess)
            members.forEach { dao.insertMember(it.copy(createdAt = System.currentTimeMillis())) }
            setPin(pin, code)
            audit("SETUP", "mess", 1, mess.name, "")
        }
        return code
    }

    suspend fun setPin(pin: String, recovery: String) {
        if (pin.length !in 4..8 || !pin.all { it.isDigit() }) throw ValidationException("PIN must be 4-8 digits / পিন ৪-৮ সংখ্যার হতে হবে")
        val salt = Security.newSalt()
        putSetting("pin_salt", salt)
        putSetting("pin_hash", Security.hash(pin, salt))
        putSetting("recovery_hash", Security.hash(recovery, salt))
    }

    suspend fun verifyPin(pin: String): Boolean {
        val salt = setting("pin_salt") ?: return false
        return setting("pin_hash") == Security.hash(pin, salt)
    }

    suspend fun resetPinWithRecovery(code: String, newPin: String): String {
        val salt = setting("pin_salt") ?: throw ValidationException("No PIN set")
        if (setting("recovery_hash") != Security.hash(code.trim(), salt)) throw ValidationException("Wrong recovery code / ভুল রিকভারি কোড")
        val fresh = Security.newRecoveryCode()
        setPin(newPin, fresh)
        audit("PIN_RESET", "security", 0, "PIN reset via recovery code", "")
        return fresh
    }

    suspend fun updateMess(m: Mess) {
        if (m.name.isBlank() || m.managerName.isBlank()) throw ValidationException("Name required / নাম দিন")
        dao.upsertMess(m); audit("UPDATE", "mess", 1, m.name, "")
    }

    // ---------- members ----------
    suspend fun saveMember(m: Member, viewMonth: String) {
        if (m.name.isBlank()) throw ValidationException("Member name required / সদস্যের নাম দিন")
        if (m.shareWeight < 1) throw ValidationException("Share weight must be at least 1")
        db.withTransaction {
            val all = dao.allMembers()
            if (all.any { it.id != m.id && it.name.trim().equals(m.name.trim(), true) }) throw ValidationException("Duplicate member name / একই নামের সদস্য আছে")
            val activeCount = all.count { it.id != m.id && it.inactiveFrom == null } + (if (m.inactiveFrom == null) 1 else 0)
            if (activeCount > 10) throw ValidationException("Maximum 10 active members / সর্বোচ্চ ১০ জন সক্রিয় সদস্য")
            if (m.id == 0L) {
                val id = dao.insertMember(m.copy(createdAt = System.currentTimeMillis()))
                audit("CREATE", "member", id, m.name, viewMonth)
            } else {
                dao.updateMember(m); audit("UPDATE", "member", m.id, m.name, viewMonth)
            }
        }
    }

    suspend fun setMemberActive(m: Member, active: Boolean, month: String) {
        if (active) saveMember(m.copy(inactiveFrom = null), month)
        else saveMember(m.copy(inactiveFrom = month), month)
    }

    suspend fun deleteMember(m: Member) {
        if (dao.memberRefCount(m.id) > 0) throw ValidationException("Member has history; deactivate instead / ইতিহাস আছে, নিষ্ক্রিয় করুন")
        dao.deleteMember(m); audit("DELETE", "member", m.id, m.name, "")
    }

    // ---------- expenses ----------
    suspend fun saveExpense(e: Expense, splits: Map<Long, Long>, shares: Map<Long, Int> = emptyMap()) {
        if (e.amount <= 0) throw ValidationException("Amount must be greater than zero / পরিমাণ শূন্যের বেশি হতে হবে")
        if (e.description.isBlank()) throw ValidationException("Description required / বিবরণ দিন")
        if (splits.isEmpty()) throw ValidationException("Select at least one member / অন্তত একজন সদস্য নির্বাচন করুন")
        if (splits.values.sum() != e.amount) throw ValidationException("Split total must equal amount / ভাগের যোগফল পরিমাণের সমান হতে হবে")
        if (splits.values.any { it < 0 }) throw ValidationException("Negative share not allowed")
        val month = Dates.monthOf(e.day)
        db.withTransaction {
            guard(month)
            val now = System.currentTimeMillis()
            val id: Long
            if (e.id == 0L) {
                id = dao.insertExpense(e.copy(month = month, createdAt = now, updatedAt = now))
                audit("CREATE", "expense", id, "${e.category} ${e.amount} ${e.description}", month)
            } else {
                val old = dao.expense(e.id) ?: throw ValidationException("Expense not found")
                guard(old.month)
                id = e.id
                dao.updateExpense(e.copy(month = month, createdAt = old.createdAt, updatedAt = now))
                dao.deleteSplitsOf(id)
                audit("UPDATE", "expense", id, "was ${old.amount} ${old.description} -> ${e.amount} ${e.description}", month)
            }
            dao.insertSplits(splits.filter { it.value > 0 }.map { ExpenseSplit(expenseId = id, memberId = it.key, amount = it.value, shares = shares[it.key] ?: 1) })
        }
    }

    suspend fun deleteExpense(e: Expense) {
        db.withTransaction {
            guard(e.month)
            dao.deleteExpense(e.id)
            audit("DELETE", "expense", e.id, "${e.category} ${e.amount} ${e.description}", e.month)
        }
    }

    // ---------- money in ----------
    suspend fun addDeposit(memberId: Long, day: Long, amount: Long, note: String) {
        if (amount <= 0) throw ValidationException("Amount must be greater than zero / পরিমাণ শূন্যের বেশি হতে হবে")
        val month = Dates.monthOf(day)
        db.withTransaction {
            guard(month)
            val id = dao.insertDeposit(Deposit(month = month, day = day, memberId = memberId, amount = amount, note = note, createdAt = System.currentTimeMillis()))
            audit("CREATE", "deposit", id, "member $memberId amount $amount", month)
        }
    }

    suspend fun addPayment(memberId: Long, day: Long, amount: Long, kind: String, note: String) {
        if (amount <= 0) throw ValidationException("Amount must be greater than zero / পরিমাণ শূন্যের বেশি হতে হবে")
        val month = Dates.monthOf(day)
        db.withTransaction {
            guard(month)
            val id = dao.insertPayment(Payment(month = month, day = day, memberId = memberId, amount = amount, kind = kind, note = note, createdAt = System.currentTimeMillis()))
            audit("CREATE", kind.lowercase(), id, "member $memberId amount $amount", month)
        }
    }

    suspend fun deleteDeposit(d: Deposit) = db.withTransaction {
        guard(d.month); dao.deleteDeposit(d.id); audit("DELETE", "deposit", d.id, "amount ${d.amount}", d.month)
    }

    suspend fun deletePayment(p: Payment) = db.withTransaction {
        guard(p.month); dao.deletePayment(p.id); audit("DELETE", p.kind.lowercase(), p.id, "amount ${p.amount}", p.month)
    }

    // ---------- bazar ----------
    suspend fun saveBazar(entry: BazarEntry, items: List<BazarItem>) {
        if (items.isEmpty()) throw ValidationException("Add at least one item / অন্তত একটি আইটেম দিন")
        if (items.any { it.name.isBlank() }) throw ValidationException("Item name required / আইটেমের নাম দিন")
        if (items.any { it.total <= 0 }) throw ValidationException("Item price must be greater than zero / দাম শূন্যের বেশি হতে হবে")
        val month = Dates.monthOf(entry.day)
        db.withTransaction {
            guard(month)
            val id: Long
            if (entry.id == 0L) {
                id = dao.insertBazar(entry.copy(month = month, createdAt = System.currentTimeMillis()))
            } else {
                val old = dao.bazarEntry(entry.id) ?: throw ValidationException("Entry not found")
                guard(old.month)
                id = entry.id
                dao.updateBazar(entry.copy(month = month, createdAt = old.createdAt))
                dao.deleteItemsOf(id)
            }
            dao.insertItems(items.map { it.copy(id = 0, entryId = id) })
            audit(if (entry.id == 0L) "CREATE" else "UPDATE", "bazar", id, "total ${items.sumOf { it.total }}", month)
        }
    }

    suspend fun deleteBazar(e: BazarEntry) = db.withTransaction {
        guard(e.month); dao.deleteBazar(e.id); audit("DELETE", "bazar", e.id, "day ${e.day}", e.month)
    }

    fun receiptFile(name: String): File = File(File(ctx.filesDir, "receipts"), name)

    fun saveReceipt(uri: Uri): String {
        val dir = File(ctx.filesDir, "receipts").also { it.mkdirs() }
        val name = UUID.randomUUID().toString() + ".jpg"
        ctx.contentResolver.openInputStream(uri)?.use { i -> File(dir, name).outputStream().use { o -> i.copyTo(o) } }
            ?: throw ValidationException("Cannot read file")
        return name
    }

    // ---------- duty ----------
    suspend fun generateDuties(month: String): Int {
        guard(month)
        return db.withTransaction {
            val snap = loadSnapshot()
            val order = Accounting.participants(snap.members, month).map { it.id }
            if (order.isEmpty()) throw ValidationException("No active members / কোনো সক্রিয় সদস্য নেই")
            val duties = dao.allDuties()
            val first = Dates.firstDay(month)
            val last = (first..Dates.lastDay(month)).toList()
            val lastBefore = duties.filter { it.day < first }.maxByOrNull { it.day }?.memberId
            val existing = duties.filter { it.month == month }.map { it.day }.toSet()
            val plan = DutyRotation.assign(last, order, lastBefore, existing)
            plan.forEach { (d, m) -> dao.upsertDuty(BazarDuty(month = month, day = d, memberId = m)) }
            audit("GENERATE", "duty", 0, "${plan.size} days", month)
            plan.size
        }
    }

    suspend fun setDuty(day: Long, memberId: Long, status: String) {
        val month = Dates.monthOf(day)
        guard(month)
        val old = dao.dutyOn(day)
        dao.upsertDuty(BazarDuty(id = old?.id ?: 0, month = month, day = day, memberId = memberId, status = status, note = old?.note ?: ""))
        audit("DUTY", "duty", day, "member $memberId $status", month)
    }

    suspend fun clearDuty(day: Long) {
        val d = dao.dutyOn(day) ?: return
        guard(d.month); dao.deleteDuty(d.id)
    }

    // ---------- notes ----------
    suspend fun saveNote(n: Note) {
        if (n.title.isBlank() && n.body.isBlank()) throw ValidationException("Write something / কিছু লিখুন")
        val now = System.currentTimeMillis()
        dao.upsertNote(if (n.id == 0L) n.copy(createdAt = now, updatedAt = now) else n.copy(updatedAt = now))
    }
    suspend fun deleteNote(id: Long) = dao.deleteNote(id)

    // ---------- month closing ----------
    suspend fun closeMonth(month: String) = db.withTransaction {
        guard(month)
        val snap = loadSnapshot()
        val blockers = Accounting.closingBlockers(snap, month)
        if (blockers.isNotEmpty()) throw ValidationException(blockers.joinToString("\n"))
        val sum = Accounting.summary(snap, month)
        val prev = dao.cycle(month)
        dao.upsertCycle(MonthlyCycle(month, "CLOSED", System.currentTimeMillis(), Accounting.closingSnapshot(sum), prev?.reopenCount ?: 0))
        audit("CLOSE", "month", 0, "total ${sum.totalExpense}", month)
    }

    suspend fun reopenMonth(month: String, reason: String) = db.withTransaction {
        val c = dao.cycle(month)
        if (c == null || c.status != "CLOSED") throw ValidationException("Month is not closed")
        val later = dao.allCycles().filter { it.month > month && it.status == "CLOSED" }
        if (later.isNotEmpty()) throw ValidationException("Reopen the latest closed month first / আগে সর্বশেষ বন্ধ মাস খুলুন")
        if (reason.isBlank()) throw ValidationException("Reason required / কারণ লিখুন")
        dao.upsertCycle(c.copy(status = "OPEN", reopenCount = c.reopenCount + 1))
        audit("REOPEN", "month", 0, reason, month)
    }
}
