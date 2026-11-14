package com.messmanager.app

import com.messmanager.app.data.*
import com.messmanager.app.domain.*
import org.junit.Assert.*
import org.junit.Test

private fun day(y: Int, m: Int, d: Int) = java.time.LocalDate.of(y, m, d).toEpochDay()

private fun members(n: Int, join: Long = day(2026, 1, 1)) = (1..n).map { Member(id = it.toLong(), name = "M$it", joinDay = join) }

private fun exp(id: Long, d: Long, amount: Long, payer: Long?, split: Map<Long, Long>, cat: String = "Rent") =
    Expense(id, Dates.monthOf(d), d, cat, amount, payer, "e$id", createdAt = 0, updatedAt = 0) to
        split.map { ExpenseSplit(expenseId = id, memberId = it.key, amount = it.value) }

class AccountingTest {
    @Test fun equalSplitSumsExactly() {
        val s = Accounting.splitEqual(10_000, listOf(1, 2, 3))
        assertEquals(10_000L, s.values.sum())
        assertEquals(setOf(3334L, 3333L), s.values.toSet())
    }

    @Test fun weightedSplitSumsExactly() {
        val s = Accounting.splitWeighted(100_001, mapOf(1L to 1, 2L to 2, 3L to 3))
        assertEquals(100_001L, s.values.sum())
        assertTrue(s[3]!! > s[2]!! && s[2]!! > s[1]!!)
    }

    @Test fun moneyParsingNeverUsesFloat() {
        assertEquals(12_345L, Money.parse("123.45"))
        assertEquals(10L, Money.parse("0.1"))
        assertEquals(30L, Money.parse("0.30"))
        assertNull(Money.parse("-5")); assertNull(Money.parse("abc"))
        assertEquals("1,234.50", Money.format(123_450))
        assertEquals(375L, Money.mulQty(Money.parseQty("1.5")!!, 250))
    }

    @Test fun balancesDoNotDoubleCount() {
        val m = members(3)
        val d1 = day(2026, 3, 5)
        val (e1, s1) = exp(1, d1, 30_000, null, Accounting.splitEqual(30_000, listOf(1, 2, 3)))
        val bz = BazarEntry(1, "2026-03", d1, 2, createdAt = 0)
        val items = listOf(BazarItem(1, 1, "Rice", 2000, "kg", 6000, 12_000), BazarItem(2, 1, "Egg", 1000, "dozen", 3000, 3_000))
        val snap = Snapshot(
            members = m, expenses = listOf(e1), splits = s1,
            deposits = listOf(Deposit(1, "2026-03", d1, 1, 50_000, createdAt = 0)),
            payments = listOf(Payment(1, "2026-03", d1, 3, 5_000, "PAYMENT", createdAt = 0), Payment(2, "2026-03", d1, 1, 2_000, "REFUND", createdAt = 0)),
            bazar = listOf(bz), items = items,
        )
        val sum = Accounting.summary(snap, "2026-03")
        assertEquals(45_000L, sum.totalExpense)
        assertEquals(15_000L, sum.bazarTotal)
        assertEquals(0L, sum.unallocated)
        // shares: each 10_000 expense + 5_000 bazar
        val l1 = sum.lines.first { it.member.id == 1L }
        assertEquals(15_000L, l1.totalShare)
        assertEquals(50_000L - 2_000L - 15_000L, l1.balance)
        val l2 = sum.lines.first { it.member.id == 2L }
        assertEquals(15_000L - 15_000L, l2.balance) // bought bazar worth 15_000 from pocket
        val l3 = sum.lines.first { it.member.id == 3L }
        assertEquals(5_000L - 15_000L, l3.balance)
        // money conservation: sum balances = (deposits + payments - refunds + paidOut) - totalExpense
        assertEquals(50_000L + 5_000L - 2_000L + 15_000L - 45_000L, sum.lines.sumOf { it.balance })
    }

    @Test fun carryForwardAcrossMonths() {
        val m = members(2)
        val (e1, s1) = exp(1, day(2026, 3, 5), 10_000, null, mapOf(1L to 5_000L, 2L to 5_000L))
        val snap = Snapshot(members = m, expenses = listOf(e1), splits = s1, deposits = listOf(Deposit(1, "2026-03", day(2026, 3, 1), 1, 8_000, createdAt = 0)))
        val april = Accounting.summary(snap, "2026-04")
        assertEquals(3_000L, april.lines.first { it.member.id == 1L }.carry)
        assertEquals(-5_000L, april.lines.first { it.member.id == 2L }.carry)
        val may = Accounting.summary(snap, "2026-05")
        assertEquals(-5_000L, may.lines.first { it.member.id == 2L }.balance)
    }

    @Test fun partialPaymentReducesDue() {
        val m = members(2)
        val d = day(2026, 3, 5)
        val (e1, s1) = exp(1, d, 10_000, null, mapOf(1L to 5_000L, 2L to 5_000L))
        val snap = Snapshot(members = m, expenses = listOf(e1), splits = s1, payments = listOf(Payment(1, "2026-03", d, 2, 2_000, "PAYMENT", createdAt = 0)))
        assertEquals(-3_000L, Accounting.summary(snap, "2026-03").lines.first { it.member.id == 2L }.balance)
    }

    @Test fun participationByJoinAndInactive() {
        val ms = listOf(
            Member(1, "A", joinDay = day(2026, 1, 1)),
            Member(2, "B", joinDay = day(2026, 4, 10)),
            Member(3, "C", joinDay = day(2026, 1, 1), inactiveFrom = "2026-03"),
        )
        assertEquals(listOf(1L, 3L), Accounting.participants(ms, "2026-02").map { it.id })
        assertEquals(listOf(1L), Accounting.participants(ms, "2026-03").map { it.id })
        assertEquals(listOf(1L, 2L), Accounting.participants(ms, "2026-04").map { it.id })
    }
}

class MonthClosingTest {
    @Test fun closingBlocksWhenEarlierMonthOpen() {
        val m = members(2)
        val (e1, s1) = exp(1, day(2026, 3, 5), 10_000, null, mapOf(1L to 5_000L, 2L to 5_000L))
        val (e2, s2) = exp(2, day(2026, 4, 5), 10_000, null, mapOf(1L to 5_000L, 2L to 5_000L))
        val snap = Snapshot(members = m, expenses = listOf(e1, e2), splits = s1 + s2)
        assertTrue(Accounting.closingBlockers(snap, "2026-04").isNotEmpty())
        assertTrue(Accounting.closingBlockers(snap, "2026-03").isEmpty())
        val closedMarch = snap.copy(cycles = listOf(MonthlyCycle("2026-03", "CLOSED", 1L)))
        assertTrue(Accounting.closingBlockers(closedMarch, "2026-04").isEmpty())
        assertTrue(closedMarch.isClosed("2026-03"))
    }

    @Test fun snapshotRoundTripAndCarry() {
        val m = members(2)
        val (e1, s1) = exp(1, day(2026, 3, 5), 10_000, null, mapOf(1L to 6_000L, 2L to 4_000L))
        val snap = Snapshot(members = m, expenses = listOf(e1), splits = s1, deposits = listOf(Deposit(1, "2026-03", day(2026, 3, 1), 1, 6_000, createdAt = 0)))
        val sum = Accounting.summary(snap, "2026-03")
        val parsed = Accounting.parseSnapshot(Accounting.closingSnapshot(sum))
        assertEquals(0L, parsed[1L]); assertEquals(-4_000L, parsed[2L])
        val next = Accounting.summary(snap.copy(cycles = listOf(MonthlyCycle("2026-03", "CLOSED", 1L))), "2026-04")
        assertEquals(parsed[2L], next.lines.first { it.member.id == 2L }.carry)
    }

    @Test fun unallocatedBlocksClose() {
        val m = members(1)
        // bazar exists but no participant in that month
        val snap = Snapshot(members = m, bazar = listOf(BazarEntry(1, "2025-12", day(2025, 12, 5), null, createdAt = 0)),
            items = listOf(BazarItem(1, 1, "x", 1000, "kg", 100, 100)))
        assertTrue(Accounting.closingBlockers(snap, "2025-12").any { it.contains("Unallocated") })
    }
}

class DutyTest {
    @Test fun rotatesRoundRobinContinuing() {
        val days = (1L..7L).toList()
        val plan = DutyRotation.assign(days, listOf(10, 20, 30), lastAssigned = 30, existing = emptySet())
        assertEquals(listOf(10L, 20L, 30L, 10L, 20L, 30L, 10L), plan.map { it.second })
    }

    @Test fun keepsManualAndSkipsExisting() {
        val plan = DutyRotation.assign(listOf(1, 2, 3), listOf(10, 20, 30), lastAssigned = null, existing = setOf(2L))
        assertEquals(listOf(1L to 10L, 3L to 20L), plan)
    }

    @Test fun reassignNextAndEmpty() {
        assertEquals(20L, DutyRotation.nextAfter(listOf(10, 20, 30), 10))
        assertEquals(10L, DutyRotation.nextAfter(listOf(10, 20, 30), 30))
        assertNull(DutyRotation.nextAfter(emptyList(), 1))
        assertTrue(DutyRotation.assign(listOf(1, 2), emptyList(), null, emptySet()).isEmpty())
    }
}

class BackupTest {
    private fun sample(): BackupData {
        val ms = members(2)
        val (e, s) = exp(1, day(2026, 3, 5), 10_000, 1, mapOf(1L to 5_000L, 2L to 5_000L))
        return BackupData(
            mess = listOf(Mess(1, "Dhaka Mess", "Addr", "Mgr", 1L)), members = ms, expenses = listOf(e), splits = s,
            deposits = listOf(Deposit(1, "2026-03", 1L, 1, 500, "n", 1L)), payments = listOf(Payment(1, "2026-03", 1L, 2, 100, "REFUND", "", 1L)),
            bazar = listOf(BazarEntry(1, "2026-03", 1L, null, "", "r.jpg", 1L)), items = listOf(BazarItem(1, 1, "Rice", 1500, "kg", 60, 90)),
            duties = listOf(BazarDuty(1, "2026-03", 5L, 1)), notes = listOf(Note(1, "t", "b", true, 1L, 1, 1, "2026-03", 1L, 1L)),
            cycles = listOf(MonthlyCycle("2026-02", "CLOSED", 5L, "1=0|total=0", 1)), audit = listOf(AuditLog(1, 1L, "CREATE", "expense", 1, "d", "2026-03")),
            settings = listOf(AppSettings("k", "v")),
        )
    }

    @Test fun roundTrip() {
        val d = sample()
        val back = BackupCodec.decode(BackupCodec.encode(d))
        assertEquals(d, back)
    }

    @Test fun rejectsGarbageAndFutureVersion() {
        assertThrows(BackupException::class.java) { BackupCodec.decode("not json") }
        assertThrows(BackupException::class.java) { BackupCodec.decode("{\"formatVersion\":99}") }
        assertThrows(BackupException::class.java) { BackupCodec.decode("{}") }
    }

    @Test fun rejectsInconsistentData() {
        val d = sample()
        val bad = d.copy(splits = d.splits.map { it.copy(amount = it.amount + 1) })
        assertThrows(BackupException::class.java) { BackupCodec.decode(BackupCodec.encode(bad)) }
        val orphan = d.copy(deposits = d.deposits.map { it.copy(memberId = 99) })
        assertThrows(BackupException::class.java) { BackupCodec.decode(BackupCodec.encode(orphan)) }
    }

    @Test fun restoredDataGivesSameAccounts() {
        val d = sample()
        val back = BackupCodec.decode(BackupCodec.encode(d))
        fun snap(x: BackupData) = Snapshot(x.members, x.expenses, x.splits, x.deposits, x.payments, x.bazar, x.items, x.cycles)
        assertEquals(Accounting.summary(snap(d), "2026-03"), Accounting.summary(snap(back), "2026-03"))
    }
}

class CalcTest {
    private fun ev(s: String) = Calc.eval(s)?.stripTrailingZeros()?.toPlainString()

    @Test fun basics() {
        assertEquals("14", ev("2+3×4"))
        assertEquals("2.5", ev("5÷2"))
        assertEquals("0.3", ev("0.1+0.2"))
        assertEquals("-3", ev("-5+2"))
    }

    @Test fun percent() {
        assertEquals("110", ev("100+10%"))
        assertEquals("90", ev("100-10%"))
        assertEquals("20", ev("200×10%"))
        assertEquals("0.5", ev("50%"))
    }

    @Test fun invalid() {
        assertNull(Calc.eval("2+")); assertNull(Calc.eval("abc")); assertNull(Calc.eval(""))
        assertThrows(ArithmeticException::class.java) { Calc.eval("5÷0") }
    }
}
