package com.messmanager.app.domain

import com.messmanager.app.data.BazarEntry
import com.messmanager.app.data.BazarItem
import com.messmanager.app.data.Deposit
import com.messmanager.app.data.Expense
import com.messmanager.app.data.ExpenseSplit
import com.messmanager.app.data.Member
import com.messmanager.app.data.MonthlyCycle
import com.messmanager.app.data.Payment
import java.time.YearMonth

val CATEGORIES = listOf("Rent", "WiFi", "Electricity", "Khala/Cook", "Gas", "Water", "Bazar", "Maintenance", "Other")
val FIXED_CATEGORIES = setOf("Rent", "WiFi", "Electricity", "Khala/Cook", "Gas", "Water")
const val BAZAR_CATEGORY = "Bazar"

data class Snapshot(
    val members: List<Member> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val splits: List<ExpenseSplit> = emptyList(),
    val deposits: List<Deposit> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val bazar: List<BazarEntry> = emptyList(),
    val items: List<BazarItem> = emptyList(),
    val cycles: List<MonthlyCycle> = emptyList(),
) {
    val itemsByEntry: Map<Long, List<BazarItem>> by lazy { items.groupBy { it.entryId } }
    fun entryTotal(e: BazarEntry): Long = itemsByEntry[e.id].orEmpty().sumOf { it.total }
    fun isClosed(month: String): Boolean = cycles.any { it.month == month && it.status == "CLOSED" }
}

data class MemberLine(
    val member: Member,
    val carry: Long,
    val deposits: Long,
    val payments: Long,
    val refunds: Long,
    val paidOut: Long,
    val expenseShare: Long,
    val bazarShare: Long,
) {
    val totalShare: Long get() = expenseShare + bazarShare
    val totalCredit: Long get() = carry + deposits + payments - refunds + paidOut
    /** > 0 advance (mess owes member), < 0 due (member owes mess). */
    val balance: Long get() = totalCredit - totalShare
}

data class MonthSummary(
    val month: String,
    val participants: List<Member>,
    val lines: List<MemberLine>,
    val totalExpense: Long,
    val expenseOnly: Long,
    val bazarTotal: Long,
    val fixedBills: Long,
    val deposits: Long,
    val payments: Long,
    val refunds: Long,
    val categoryTotals: Map<String, Long>,
    val dailyTotals: Map<Long, Long>,
    val unallocated: Long,
    val closed: Boolean,
) {
    val totalDue: Long get() = lines.filter { it.balance < 0 }.sumOf { -it.balance }
    val totalAdvance: Long get() = lines.filter { it.balance > 0 }.sumOf { it.balance }
    val collected: Long get() = deposits + payments - refunds
}

object Accounting {
    /** Splits amount by integer weights using the largest-remainder method; result always sums to amount. */
    fun splitWeighted(amount: Long, weights: Map<Long, Int>): Map<Long, Long> {
        require(weights.isNotEmpty()) { "no members" }
        val total = weights.values.sumOf { it.toLong() }
        require(total > 0) { "weights must be positive" }
        val base = LinkedHashMap<Long, Long>()
        val rem = LinkedHashMap<Long, Long>()
        for ((id, w) in weights) {
            val num = amount * w
            base[id] = num / total
            rem[id] = num % total
        }
        var left = amount - base.values.sum()
        val order = weights.keys.withIndex().sortedWith(compareByDescending<IndexedValue<Long>> { rem[it.value]!! }.thenBy { it.index })
        for (iv in order) {
            if (left <= 0) break
            base[iv.value] = base[iv.value]!! + 1
            left--
        }
        return base
    }

    fun splitEqual(amount: Long, ids: List<Long>): Map<Long, Long> = splitWeighted(amount, ids.associateWith { 1 })

    fun participants(members: List<Member>, month: String): List<Member> {
        val end = Dates.lastDay(month)
        return members.filter { it.joinDay <= end && (it.inactiveFrom == null || month < it.inactiveFrom) }
    }

    fun monthsWithData(s: Snapshot): List<String> {
        val set = sortedSetOf<String>()
        s.expenses.forEach { set.add(it.month) }
        s.deposits.forEach { set.add(it.month) }
        s.payments.forEach { set.add(it.month) }
        s.bazar.forEach { set.add(it.month) }
        return set.toList()
    }

    fun summary(s: Snapshot, month: String): MonthSummary {
        val first = monthsWithData(s).firstOrNull() ?: month
        var carry: Map<Long, Long> = emptyMap()
        var m = YearMonth.parse(first)
        val target = YearMonth.parse(month)
        var guard = 0
        while (m < target && guard++ < 1200) {
            val r = compute(s, m.toString(), carry)
            carry = r.lines.associate { it.member.id to it.balance }
            m = m.plusMonths(1)
        }
        return compute(s, month, carry)
    }

    private fun compute(s: Snapshot, month: String, carry: Map<Long, Long>): MonthSummary {
        val parts = participants(s.members, month)
        val exps = s.expenses.filter { it.month == month }
        val expIds = exps.map { it.id }.toSet()
        val splits = s.splits.filter { it.expenseId in expIds }
        val bazar = s.bazar.filter { it.month == month }
        val deps = s.deposits.filter { it.month == month }
        val pays = s.payments.filter { it.month == month }

        val bazarTotal = bazar.sumOf { s.entryTotal(it) }
        val expenseOnly = exps.sumOf { it.amount }
        val bazarShares: Map<Long, Long> =
            if (parts.isNotEmpty() && bazarTotal > 0) splitEqual(bazarTotal, parts.map { it.id }) else emptyMap()

        val involved = LinkedHashSet<Long>()
        parts.forEach { involved.add(it.id) }
        splits.forEach { involved.add(it.memberId) }
        deps.forEach { involved.add(it.memberId) }
        pays.forEach { involved.add(it.memberId) }
        exps.forEach { e -> e.payerId?.let { involved.add(it) } }
        bazar.forEach { b -> b.purchasedById?.let { involved.add(it) } }
        carry.forEach { (id, v) -> if (v != 0L) involved.add(id) }

        val byId = s.members.associateBy { it.id }
        val lines = involved.mapNotNull { id ->
            val mem = byId[id] ?: return@mapNotNull null
            MemberLine(
                member = mem,
                carry = carry[id] ?: 0L,
                deposits = deps.filter { it.memberId == id }.sumOf { it.amount },
                payments = pays.filter { it.memberId == id && it.kind == "PAYMENT" }.sumOf { it.amount },
                refunds = pays.filter { it.memberId == id && it.kind == "REFUND" }.sumOf { it.amount },
                paidOut = exps.filter { it.payerId == id }.sumOf { it.amount } +
                    bazar.filter { it.purchasedById == id }.sumOf { s.entryTotal(it) },
                expenseShare = splits.filter { it.memberId == id }.sumOf { it.amount },
                bazarShare = bazarShares[id] ?: 0L,
            )
        }.sortedBy { it.member.id }

        val cat = LinkedHashMap<String, Long>()
        exps.forEach { cat[it.category] = (cat[it.category] ?: 0L) + it.amount }
        if (bazarTotal > 0) cat[BAZAR_CATEGORY] = (cat[BAZAR_CATEGORY] ?: 0L) + bazarTotal
        val daily = sortedMapOf<Long, Long>()
        exps.forEach { daily[it.day] = (daily[it.day] ?: 0L) + it.amount }
        bazar.forEach { daily[it.day] = (daily[it.day] ?: 0L) + s.entryTotal(it) }

        val totalExpense = expenseOnly + bazarTotal
        val allocated = lines.sumOf { it.totalShare }
        return MonthSummary(
            month = month,
            participants = parts,
            lines = lines,
            totalExpense = totalExpense,
            expenseOnly = expenseOnly,
            bazarTotal = bazarTotal,
            fixedBills = exps.filter { it.category in FIXED_CATEGORIES }.sumOf { it.amount },
            deposits = deps.sumOf { it.amount },
            payments = pays.filter { it.kind == "PAYMENT" }.sumOf { it.amount },
            refunds = pays.filter { it.kind == "REFUND" }.sumOf { it.amount },
            categoryTotals = cat,
            dailyTotals = daily,
            unallocated = totalExpense - allocated,
            closed = s.isClosed(month),
        )
    }

    /** Problems that block closing a month. */
    fun closingBlockers(s: Snapshot, month: String): List<String> {
        val out = mutableListOf<String>()
        val earlierOpen = monthsWithData(s).filter { it < month && !s.isClosed(it) }
        if (earlierOpen.isNotEmpty()) out.add("Close earlier months first / আগের মাস আগে বন্ধ করুন: ${earlierOpen.joinToString { Dates.monthLabel(it) }}")
        val sum = summary(s, month)
        if (sum.unallocated != 0L) out.add("Unallocated amount ${Money.format(sum.unallocated)} / বণ্টনহীন অর্থ আছে")
        return out
    }

    /** Compact text snapshot of closing balances stored on the cycle row. */
    fun closingSnapshot(sum: MonthSummary): String =
        sum.lines.joinToString(";") { "${it.member.id}=${it.balance}" } + "|total=${sum.totalExpense}"

    fun parseSnapshot(text: String): Map<Long, Long> =
        text.substringBefore("|").split(";").filter { it.contains("=") }
            .associate { it.substringBefore("=").toLong() to it.substringAfter("=").toLong() }
}
