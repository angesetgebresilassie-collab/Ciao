package com.ciao.glasscalc

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.*

object Calc {
    private const val OPS = "+−×÷^("

    fun evaluate(raw: String, deg: Boolean = true): Double? {
        var s = raw.trimEnd('+', '−', '×', '÷', '-', '^')
        if (s.isEmpty()) return null
        s += ")".repeat((s.count { it == '(' } - s.count { it == ')' }).coerceAtLeast(0))
        return runCatching { Parser(s, deg).parse() }.getOrNull()
            ?.takeIf { it.isFinite() }
            ?.let { if (abs(it) < 1e-12) 0.0 else it }
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

    private class Parser(val s: String, val deg: Boolean) {
        var i = 0
        fun peek() = if (i < s.length) s[i] else '\u0000'

        fun parse(): Double {
            val v = expr()
            check(i == s.length)
            return v
        }

        fun expr(): Double {
            var v = term()
            while (peek() == '+' || peek() == '−') {
                val op = s[i++]
                val r = term()
                v = if (op == '+') v + r else v - r
            }
            return v
        }

        fun term(): Double {
            var v = unary()
            while (true) {
                val c = peek()
                v = when {
                    c == '×' -> { i++; v * unary() }
                    c == '÷' -> { i++; v / unary() }
                    c.isDigit() || c.isLetter() || c in ".(π√" -> v * unary() // implicit multiply
                    else -> return v
                }
            }
        }

        fun unary(): Double {
            val c = peek()
            if (c == '-' || c == '−') { i++; return -unary() }
            return power()
        }

        fun power(): Double {
            val b = postfix()
            if (peek() == '^') { i++; return b.pow(unary()) }
            return b
        }

        fun postfix(): Double {
            var v = atom()
            while (true) {
                when (peek()) {
                    '%' -> { i++; v /= 100 }
                    '!' -> { i++; v = fact(v) }
                    else -> return v
                }
            }
        }

        fun group(): Double {
            check(peek() == '(')
            i++
            val v = expr()
            check(peek() == ')')
            i++
            return v
        }

        fun atom(): Double {
            val c = peek()
            return when {
                c == '(' -> group()
                c == 'π' -> { i++; PI }
                c == 'e' -> { i++; E }
                c == '√' -> { i++; sqrt(group()) }
                c.isLetter() -> {
                    val st = i
                    while (peek().isLetter()) i++
                    fn(s.substring(st, i), group())
                }
                else -> number()
            }
        }

        fun fn(name: String, v: Double): Double {
            val x = if (deg) Math.toRadians(v) else v
            return when (name) {
                "sin" -> sin(x)
                "cos" -> cos(x)
                "tan" -> tan(x)
                "ln" -> ln(v)
                "log" -> log10(v)
                else -> error("fn")
            }
        }

        fun fact(v: Double): Double {
            if (v < 0 || v != floor(v) || v > 170) return Double.NaN
            var r = 1.0
            for (k in 2..v.toInt()) r *= k
            return r
        }

        fun number(): Double {
            val st = i
            while (peek().isDigit() || peek() == '.') i++
            return s.substring(st, i).toDouble()
        }
    }
}
