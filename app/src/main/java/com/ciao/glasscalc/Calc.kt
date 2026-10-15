package com.ciao.glasscalc

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.floor

object Calc {
    private const val OPS = "+−×÷"

    fun evaluate(raw: String): Double? {
        val s = raw.trimEnd('+', '−', '×', '÷', '-')
        if (s.isEmpty()) return null
        return runCatching { Parser(s).parse() }.getOrNull()?.takeIf { it.isFinite() }
    }

    fun format(v: Double): String =
        if (v == floor(v) && abs(v) < 1e15) v.toLong().toString()
        else BigDecimal(v).round(MathContext(10)).stripTrailingZeros().toPlainString()

    fun negateLast(e: String): String {
        val i = e.indexOfLast { it in OPS }
        val head = e.substring(0, i + 1)
        val num = e.substring(i + 1)
        if (num.isEmpty()) return e
        return head + if (num.startsWith("-")) num.drop(1) else "-$num"
    }

    private class Parser(val s: String) {
        var i = 0
        fun parse(): Double {
            val v = expr()
            check(i == s.length)
            return v
        }
        fun expr(): Double {
            var v = term()
            while (i < s.length && s[i] in "+−") {
                val op = s[i++]
                val r = term()
                v = if (op == '+') v + r else v - r
            }
            return v
        }
        fun term(): Double {
            var v = unary()
            while (i < s.length && s[i] in "×÷") {
                val op = s[i++]
                val r = unary()
                v = if (op == '×') v * r else v / r
            }
            return v
        }
        fun unary(): Double {
            if (i < s.length && s[i] == '-') { i++; return -unary() }
            return percent()
        }
        fun percent(): Double {
            var v = number()
            while (i < s.length && s[i] == '%') { i++; v /= 100 }
            return v
        }
        fun number(): Double {
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            return s.substring(st, i).toDouble()
        }
    }
}
