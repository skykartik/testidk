package com.bubbly.calc

import kotlin.math.*

class CalcError : Exception()

/** Same recursive-descent grammar as the Windows and Linux versions, so behaviour matches exactly. */
class Calc {
    var deg = true
    var ans = 0.0

    private val names = listOf("Ans", "abs", "sin", "cos", "tan", "log", "ln", "e")
    private var toks: List<String> = emptyList()
    private var i = 0

    private fun tokenize(input: String): List<String> {
        var s = input.replace('-', '\u2212')
        val opens = s.count { it == '(' }
        val closes = s.count { it == ')' }
        if (opens > closes) s += ")".repeat(opens - closes)
        val toks = mutableListOf<String>()
        var p = 0
        val n = s.length
        while (p < n) {
            val c = s[p]
            if (c.isDigit() || c == '.') {
                var j = p
                while (j < n && (s[j].isDigit() || s[j] == '.')) j++
                if (j < n && s[j] == 'e') {
                    var k = j + 1
                    if (k < n && (s[k] == '+' || s[k] == '\u2212')) k++
                    if (k < n && s[k].isDigit()) {
                        while (k < n && s[k].isDigit()) k++
                        j = k
                    }
                }
                toks.add(s.substring(p, j)); p = j; continue
            }
            val matched = names.firstOrNull { s.startsWith(it, p) }
            if (matched != null) { toks.add(matched); p += matched.length } else { toks.add(c.toString()); p++ }
        }
        return toks
    }

    fun eval(input: String): Double {
        toks = tokenize(input); i = 0
        val v = expr()
        if (i < toks.size) throw CalcError()
        if (v.isNaN() || v.isInfinite()) throw CalcError()
        return v
    }

    private fun peek(): String? = toks.getOrNull(i)
    private fun isPrimStart(t: String?): Boolean {
        if (t == null) return false
        return t[0].isDigit() || t[0] == '.' || t in setOf("(", "\u03c0", "e", "Ans", "sin", "cos", "tan", "ln", "log", "abs", "\u221a")
    }

    private fun expr(): Double {
        var v = term()
        while (peek() == "+" || peek() == "\u2212") {
            val op = toks[i]; i++
            val r = term()
            v = if (op == "+") v + r else v - r
        }
        return v
    }

    private fun term(): Double {
        var v = unary()
        while (true) {
            val p = peek()
            if (p == "\u00d7" || p == "\u00f7") {
                i++
                val r = unary()
                v = if (p == "\u00d7") v * r else v / r
            } else if (isPrimStart(p)) {
                v *= unary()
            } else break
        }
        return v
    }

    private fun unary(): Double {
        val p = peek()
        if (p == "\u2212") { i++; return -unary() }
        if (p == "+") { i++; return unary() }
        return power()
    }

    private fun power(): Double {
        var v = postfix()
        if (peek() == "^") {
            i++
            v = v.pow(unary())
            if (v.isNaN()) throw CalcError()
        }
        return v
    }

    private fun postfix(): Double {
        var v = primary()
        while (peek() == "!" || peek() == "%") {
            val t = toks[i]; i++
            v = if (t == "!") fact(v) else v / 100
        }
        return v
    }

    private fun fact(n: Double): Double {
        if (n < 0 || n > 170 || n != floor(n)) throw CalcError()
        var r = 1.0
        for (k in 2..n.toInt()) r *= k
        return r
    }

    private fun primary(): Double {
        val t = peek() ?: throw CalcError()
        i++
        if (t[0].isDigit() || t[0] == '.') return t.replace('\u2212', '-').toDouble()
        if (t == "\u03c0") return PI
        if (t == "e") return E
        if (t == "Ans") return ans
        if (t == "(") {
            val v = expr()
            if (peek() == ")") i++
            return v
        }
        if (t in setOf("sin", "cos", "tan", "ln", "log", "abs", "\u221a")) {
            if (peek() != "(") throw CalcError()
            i++
            val x = expr()
            if (peek() == ")") i++
            val k = if (deg) PI / 180 else 1.0
            return when (t) {
                "sin" -> { val r = sin(x * k); if (abs(r) < 1e-15) 0.0 else r }
                "cos" -> { val r = cos(x * k); if (abs(r) < 1e-15) 0.0 else r }
                "tan" -> tan(x * k)
                "ln" -> ln(x)
                "log" -> log10(x)
                "abs" -> abs(x)
                else -> sqrt(x)
            }
        }
        throw CalcError()
    }
}

fun fmtNum(d: Double): String {
    if (d == 0.0) return "0"
    val a = abs(d)
    // Java's own %g switches to scientific notation once the exponent reaches the precision (10^12 here),
    // which would look inconsistent with our own %e formatting - so route anything near that threshold
    // through our formatter first, well before Java's automatic switch could kick in.
    val s = if (a >= 1e11 || a < 1e-9) {
        String.format("%.6e", d).replace(Regex("0+e"), "e").replace(".e", "e")
    } else {
        // trim to ~12 significant digits, drop trailing zeros, like the C#/Python versions
        var str = String.format("%.12g", d).trimEnd('0').trimEnd('.')
        if (str.isEmpty() || str == "-") str = "0"
        str
    }
    return s.replace("-", "\u2212")
}
