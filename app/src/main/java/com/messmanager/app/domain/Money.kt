package com.messmanager.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object Money {
    /** Parse user text like "1,250.50" into poisha. Null when invalid or negative. */
    fun parse(s: String): Long? {
        val t = s.trim().replace(",", "")
        if (t.isEmpty()) return null
        return try {
            val bd = BigDecimal(t)
            if (bd.signum() < 0) null
            else bd.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        } catch (e: Exception) { null }
    }

    /** Parse quantity like "1.5" into milli-units. */
    fun parseQty(s: String): Long? {
        val t = s.trim()
        if (t.isEmpty()) return null
        return try {
            val bd = BigDecimal(t)
            if (bd.signum() <= 0) null
            else bd.setScale(3, RoundingMode.HALF_UP).movePointRight(3).longValueExact()
        } catch (e: Exception) { null }
    }

    fun mulQty(qtyMilli: Long, unitPrice: Long): Long =
        BigDecimal(qtyMilli).multiply(BigDecimal(unitPrice)).divide(BigDecimal(1000), 0, RoundingMode.HALF_UP).longValueExact()

    fun format(p: Long): String {
        val a = abs(p)
        val s = String.format(Locale.US, "%,d.%02d", a / 100, a % 100)
        return if (p < 0) "-$s" else s
    }

    fun plain(p: Long): String = String.format(Locale.US, "%d.%02d", p / 100, abs(p) % 100)

    fun qtyText(milli: Long): String {
        val bd = BigDecimal(milli).movePointLeft(3).stripTrailingZeros()
        return bd.toPlainString()
    }
}

object Dates {
    private val dmy = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
    private val dm = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

    fun today(): Long = LocalDate.now().toEpochDay()
    fun date(day: Long): LocalDate = LocalDate.ofEpochDay(day)
    fun fmt(day: Long): String = date(day).format(dmy)
    fun fmtShort(day: Long): String = date(day).format(dm)
    fun monthOf(day: Long): String = YearMonth.from(date(day)).toString()
    fun monthLabel(m: String): String = YearMonth.parse(m).format(monthFmt)
    fun firstDay(m: String): Long = YearMonth.parse(m).atDay(1).toEpochDay()
    fun lastDay(m: String): Long = YearMonth.parse(m).atEndOfMonth().toEpochDay()
    fun daysIn(m: String): Int = YearMonth.parse(m).lengthOfMonth()
    fun plus(m: String, n: Long): String = YearMonth.parse(m).plusMonths(n).toString()
    fun currentMonth(): String = YearMonth.now().toString()
}
