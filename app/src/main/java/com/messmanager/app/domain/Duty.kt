package com.messmanager.app.domain

object DutyRotation {
    /** Order of rotation: participant ids in stable order. */
    fun nextAfter(order: List<Long>, lastId: Long?): Long? {
        if (order.isEmpty()) return null
        val i = order.indexOf(lastId)
        return order[if (i < 0) 0 else (i + 1) % order.size]
    }

    /**
     * Assigns days (ascending) to members round-robin, continuing after [lastAssigned].
     * Days in [existing] keep their duty and do not advance the rotation.
     */
    fun assign(days: List<Long>, order: List<Long>, lastAssigned: Long?, existing: Set<Long>): List<Pair<Long, Long>> {
        if (order.isEmpty()) return emptyList()
        var last = lastAssigned
        val out = mutableListOf<Pair<Long, Long>>()
        for (d in days.sorted()) {
            if (d in existing) continue
            val m = nextAfter(order, last)!!
            out.add(d to m)
            last = m
        }
        return out
    }
}

/** Tiny calculator: + - × ÷ with precedence, decimals and percent. Uses BigDecimal. */
object Calc {
    private val mc = java.math.MathContext(20, java.math.RoundingMode.HALF_UP)

    /** Returns null for an invalid expression, throws ArithmeticException for division by zero. */
    fun eval(expr: String): java.math.BigDecimal? {
        val tokens = tokenize(expr) ?: return null
        if (tokens.isEmpty()) return null
        return try {
            Parser(tokens).parse()
        } catch (e: IllegalStateException) {
            null
        }
    }

    private fun tokenize(s: String): List<String>? {
        val out = mutableListOf<String>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    var j = i
                    while (j < s.length && (s[j].isDigit() || s[j] == '.')) j++
                    out.add(s.substring(i, j)); i = j
                }
                c in "+-×÷*/%" -> { out.add(when (c) { '*' -> "×"; '/' -> "÷"; else -> c.toString() }); i++ }
                else -> return null
            }
        }
        return out
    }

    private class Parser(val t: List<String>) {
        var p = 0
        fun parse(): java.math.BigDecimal {
            val v = expr()
            if (p != t.size) error("trailing")
            return v
        }
        fun expr(): java.math.BigDecimal {
            var left = term()
            while (p < t.size && (t[p] == "+" || t[p] == "-")) {
                val op = t[p++]
                val startP = p
                val right = term()
                // "100 + 10%" means 100 + 10% of 100
                val pct = p - 1 > startP - 1 && t[p - 1] == "%" && p - startP == 2
                val r = if (pct) left.multiply(right, mc) else right
                left = if (op == "+") left.add(r, mc) else left.subtract(r, mc)
            }
            return left
        }
        fun term(): java.math.BigDecimal {
            var left = unary()
            while (p < t.size && (t[p] == "×" || t[p] == "÷")) {
                val op = t[p++]
                val right = unary()
                left = if (op == "×") left.multiply(right, mc) else {
                    if (right.signum() == 0) throw ArithmeticException("div0")
                    left.divide(right, mc)
                }
            }
            return left
        }
        fun unary(): java.math.BigDecimal {
            if (p < t.size && t[p] == "-") { p++; return unary().negate() }
            return postfix()
        }
        fun postfix(): java.math.BigDecimal {
            if (p >= t.size) error("eof")
            val n = t[p++].toBigDecimalOrNull() ?: error("num")
            var v = n
            while (p < t.size && t[p] == "%") { p++; v = v.divide(java.math.BigDecimal(100), mc) }
            return v
        }
    }
}
