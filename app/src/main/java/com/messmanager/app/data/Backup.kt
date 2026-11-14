package com.messmanager.app.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupException(msg: String) : Exception(msg)

data class BackupData(
    val mess: List<Mess> = emptyList(),
    val members: List<Member> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val splits: List<ExpenseSplit> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val deposits: List<Deposit> = emptyList(),
    val bazar: List<BazarEntry> = emptyList(),
    val items: List<BazarItem> = emptyList(),
    val duties: List<BazarDuty> = emptyList(),
    val notes: List<Note> = emptyList(),
    val cycles: List<MonthlyCycle> = emptyList(),
    val audit: List<AuditLog> = emptyList(),
    val settings: List<AppSettings> = emptyList(),
)

/** Pure JSON codec (testable without Android). */
object BackupCodec {
    const val FORMAT_VERSION = 1

    private fun JSONObject.lng(k: String): Long? = if (!has(k) || isNull(k)) null else getLong(k)
    private fun JSONObject.str(k: String): String? = if (!has(k) || isNull(k)) null else getString(k)
    private fun <T> JSONArray.mapObj(f: (JSONObject) -> T): List<T> = (0 until length()).map { f(getJSONObject(it)) }
    private fun nz(v: Any?): Any = v ?: JSONObject.NULL

    private fun <T> arr(l: List<T>, f: (T) -> JSONObject) = JSONArray().also { a -> l.forEach { a.put(f(it)) } }

    fun encode(d: BackupData): String {
        val o = JSONObject()
        o.put("formatVersion", FORMAT_VERSION)
        o.put("mess", arr(d.mess) { JSONObject().put("id", it.id).put("name", it.name).put("address", it.address).put("managerName", it.managerName).put("createdAt", it.createdAt) })
        o.put("members", arr(d.members) { JSONObject().put("id", it.id).put("name", it.name).put("phone", it.phone).put("joinDay", it.joinDay).put("inactiveFrom", nz(it.inactiveFrom)).put("shareWeight", it.shareWeight).put("createdAt", it.createdAt) })
        o.put("expenses", arr(d.expenses) { JSONObject().put("id", it.id).put("month", it.month).put("day", it.day).put("category", it.category).put("amount", it.amount).put("payerId", nz(it.payerId)).put("description", it.description).put("note", it.note).put("splitMode", it.splitMode).put("createdAt", it.createdAt).put("updatedAt", it.updatedAt) })
        o.put("splits", arr(d.splits) { JSONObject().put("id", it.id).put("expenseId", it.expenseId).put("memberId", it.memberId).put("amount", it.amount).put("shares", it.shares) })
        o.put("payments", arr(d.payments) { JSONObject().put("id", it.id).put("month", it.month).put("day", it.day).put("memberId", it.memberId).put("amount", it.amount).put("kind", it.kind).put("note", it.note).put("createdAt", it.createdAt) })
        o.put("deposits", arr(d.deposits) { JSONObject().put("id", it.id).put("month", it.month).put("day", it.day).put("memberId", it.memberId).put("amount", it.amount).put("note", it.note).put("createdAt", it.createdAt) })
        o.put("bazar", arr(d.bazar) { JSONObject().put("id", it.id).put("month", it.month).put("day", it.day).put("purchasedById", nz(it.purchasedById)).put("note", it.note).put("receipt", nz(it.receipt)).put("createdAt", it.createdAt) })
        o.put("items", arr(d.items) { JSONObject().put("id", it.id).put("entryId", it.entryId).put("name", it.name).put("qtyMilli", it.qtyMilli).put("unit", it.unit).put("unitPrice", it.unitPrice).put("total", it.total) })
        o.put("duties", arr(d.duties) { JSONObject().put("id", it.id).put("month", it.month).put("day", it.day).put("memberId", it.memberId).put("status", it.status).put("note", it.note) })
        o.put("notes", arr(d.notes) { JSONObject().put("id", it.id).put("title", it.title).put("body", it.body).put("pinned", it.pinned).put("day", it.day).put("memberId", nz(it.memberId)).put("expenseId", nz(it.expenseId)).put("month", nz(it.month)).put("createdAt", it.createdAt).put("updatedAt", it.updatedAt) })
        o.put("cycles", arr(d.cycles) { JSONObject().put("month", it.month).put("status", it.status).put("closedAt", nz(it.closedAt)).put("snapshotJson", it.snapshotJson).put("reopenCount", it.reopenCount) })
        o.put("audit", arr(d.audit) { JSONObject().put("id", it.id).put("ts", it.ts).put("action", it.action).put("entity", it.entity).put("entityId", it.entityId).put("detail", it.detail).put("month", it.month) })
        o.put("settings", arr(d.settings) { JSONObject().put("key", it.key).put("value", it.value) })
        return o.toString()
    }

    /** Parses and validates; throws BackupException with a useful message. */
    fun decode(text: String): BackupData {
        try {
            val o = JSONObject(text)
            val v = o.optInt("formatVersion", -1)
            if (v < 1) throw BackupException("Not a MessManager backup / এটি MessManager ব্যাকআপ নয়")
            if (v > FORMAT_VERSION) throw BackupException("Backup is from a newer app version / ব্যাকআপ নতুন সংস্করণের")
            fun a(k: String) = o.optJSONArray(k) ?: throw BackupException("Missing section: $k")
            val d = BackupData(
                mess = a("mess").mapObj { Mess(it.getLong("id"), it.getString("name"), it.getString("address"), it.getString("managerName"), it.getLong("createdAt")) },
                members = a("members").mapObj { Member(it.getLong("id"), it.getString("name"), it.getString("phone"), it.getLong("joinDay"), it.str("inactiveFrom"), it.getInt("shareWeight"), it.getLong("createdAt")) },
                expenses = a("expenses").mapObj { Expense(it.getLong("id"), it.getString("month"), it.getLong("day"), it.getString("category"), it.getLong("amount"), it.lng("payerId"), it.getString("description"), it.getString("note"), it.getString("splitMode"), it.getLong("createdAt"), it.getLong("updatedAt")) },
                splits = a("splits").mapObj { ExpenseSplit(it.getLong("id"), it.getLong("expenseId"), it.getLong("memberId"), it.getLong("amount"), it.getInt("shares")) },
                payments = a("payments").mapObj { Payment(it.getLong("id"), it.getString("month"), it.getLong("day"), it.getLong("memberId"), it.getLong("amount"), it.getString("kind"), it.getString("note"), it.getLong("createdAt")) },
                deposits = a("deposits").mapObj { Deposit(it.getLong("id"), it.getString("month"), it.getLong("day"), it.getLong("memberId"), it.getLong("amount"), it.getString("note"), it.getLong("createdAt")) },
                bazar = a("bazar").mapObj { BazarEntry(it.getLong("id"), it.getString("month"), it.getLong("day"), it.lng("purchasedById"), it.getString("note"), it.str("receipt"), it.getLong("createdAt")) },
                items = a("items").mapObj { BazarItem(it.getLong("id"), it.getLong("entryId"), it.getString("name"), it.getLong("qtyMilli"), it.getString("unit"), it.getLong("unitPrice"), it.getLong("total")) },
                duties = a("duties").mapObj { BazarDuty(it.getLong("id"), it.getString("month"), it.getLong("day"), it.getLong("memberId"), it.getString("status"), it.getString("note")) },
                notes = a("notes").mapObj { Note(it.getLong("id"), it.getString("title"), it.getString("body"), it.getBoolean("pinned"), it.getLong("day"), it.lng("memberId"), it.lng("expenseId"), it.str("month"), it.getLong("createdAt"), it.getLong("updatedAt")) },
                cycles = a("cycles").mapObj { MonthlyCycle(it.getString("month"), it.getString("status"), it.lng("closedAt"), it.getString("snapshotJson"), it.getInt("reopenCount")) },
                audit = a("audit").mapObj { AuditLog(it.getLong("id"), it.getLong("ts"), it.getString("action"), it.getString("entity"), it.getLong("entityId"), it.getString("detail"), it.getString("month")) },
                settings = a("settings").mapObj { AppSettings(it.getString("key"), it.getString("value")) },
            )
            validate(d)
            return d
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException("Corrupt backup / ব্যাকআপ ফাইল নষ্ট: ${e.message}")
        }
    }

    fun validate(d: BackupData) {
        val mem = d.members.map { it.id }.toSet()
        val exp = d.expenses.map { it.id }.toSet()
        val ent = d.bazar.map { it.id }.toSet()
        if (d.mess.size > 1) throw BackupException("Invalid mess data")
        d.expenses.forEach {
            if (it.amount <= 0) throw BackupException("Invalid expense amount")
            if (it.payerId != null && it.payerId !in mem) throw BackupException("Expense references unknown member")
        }
        d.splits.forEach { if (it.expenseId !in exp || it.memberId !in mem) throw BackupException("Split references unknown record") }
        val sums = d.splits.groupBy { it.expenseId }.mapValues { e -> e.value.sumOf { it.amount } }
        d.expenses.forEach { if ((sums[it.id] ?: 0L) != it.amount) throw BackupException("Expense split mismatch (id ${it.id})") }
        d.payments.forEach { if (it.memberId !in mem || it.amount <= 0) throw BackupException("Invalid payment") }
        d.deposits.forEach { if (it.memberId !in mem || it.amount <= 0) throw BackupException("Invalid deposit") }
        d.bazar.forEach { if (it.purchasedById != null && it.purchasedById !in mem) throw BackupException("Bazar references unknown member") }
        d.items.forEach { if (it.entryId !in ent) throw BackupException("Item references unknown entry") }
        d.duties.forEach { if (it.memberId !in mem) throw BackupException("Duty references unknown member") }
        d.notes.forEach {
            if (it.memberId != null && it.memberId !in mem) throw BackupException("Note references unknown member")
            if (it.expenseId != null && it.expenseId !in exp) throw BackupException("Note references unknown expense")
        }
    }

    fun sha256(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
}

class BackupManager(private val repo: Repository, private val receiptsDir: () -> File) {
    private val dao = repo.dao

    suspend fun collect(): BackupData = BackupData(
        dao.allMess(), dao.allMembers(), dao.allExpenses(), dao.allSplits(), dao.allPayments(), dao.allDeposits(),
        dao.allBazar(), dao.allItems(), dao.allDuties(), dao.allNotes(), dao.allCycles(), dao.allAudit(), dao.allSettings(),
    )

    suspend fun export(out: OutputStream) {
        val data = collect()
        val json = BackupCodec.encode(data).toByteArray(Charsets.UTF_8)
        ZipOutputStream(out).use { z ->
            val manifest = JSONObject().put("app", "MessManager").put("formatVersion", BackupCodec.FORMAT_VERSION)
                .put("createdAt", System.currentTimeMillis()).put("sha256", BackupCodec.sha256(json))
            z.putNextEntry(ZipEntry("manifest.json")); z.write(manifest.toString().toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("data.json")); z.write(json); z.closeEntry()
            data.bazar.mapNotNull { it.receipt }.distinct().forEach { name ->
                val f = File(receiptsDir(), name)
                if (f.exists()) { z.putNextEntry(ZipEntry("receipts/$name")); f.inputStream().use { it.copyTo(z) }; z.closeEntry() }
            }
        }
    }

    /** Validates fully before touching the database. Returns counts summary. */
    suspend fun restore(input: InputStream): String {
        var manifest: JSONObject? = null
        var json: ByteArray? = null
        val receipts = HashMap<String, ByteArray>()
        try {
            ZipInputStream(input).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    val bytes = z.readBytes()
                    when {
                        e.name == "manifest.json" -> manifest = JSONObject(String(bytes))
                        e.name == "data.json" -> json = bytes
                        e.name.startsWith("receipts/") -> receipts[e.name.removePrefix("receipts/").substringAfterLast('/')] = bytes
                    }
                    e = z.nextEntry
                }
            }
        } catch (ex: Exception) {
            throw BackupException("Cannot read backup file / ব্যাকআপ ফাইল পড়া যায়নি")
        }
        val j = json ?: throw BackupException("data.json missing")
        val sum = manifest?.optString("sha256", "").orEmpty()
        if (sum.isNotEmpty() && sum != BackupCodec.sha256(j)) throw BackupException("Checksum mismatch: file damaged / ফাইল ক্ষতিগ্রস্ত")
        val d = BackupCodec.decode(String(j, Charsets.UTF_8))
        repo.db.withTransaction {
            dao.clearNotes(); dao.clearDuties(); dao.clearItems(); dao.clearBazar(); dao.clearSplits(); dao.clearExpenses()
            dao.clearPayments(); dao.clearDeposits(); dao.clearCycles(); dao.clearAudit(); dao.clearMembers(); dao.clearMess(); dao.clearSettings()
            dao.insertMessAll(d.mess); dao.insertMemberAll(d.members); dao.insertExpenseAll(d.expenses); dao.insertSplitAll(d.splits)
            dao.insertPaymentAll(d.payments); dao.insertDepositAll(d.deposits); dao.insertBazarAll(d.bazar); dao.insertItemAll(d.items)
            dao.insertDutyAll(d.duties); dao.insertNoteAll(d.notes); dao.insertCycleAll(d.cycles); dao.insertAuditAll(d.audit)
            dao.insertSettingAll(d.settings)
        }
        val dir = receiptsDir().also { it.mkdirs() }
        receipts.forEach { (n, b) -> File(dir, n).writeBytes(b) }
        return "${d.members.size} members, ${d.expenses.size} expenses, ${d.bazar.size} bazar entries"
    }
}
