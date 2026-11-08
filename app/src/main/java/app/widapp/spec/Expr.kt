package app.widapp.spec

import app.widapp.core.AppJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull

/**
 * Evaluates `{token}` templates, `{cond ? 'a' : 'b'}` ternaries and `visibleIf` conditions
 * against the widget's state variables and the live [DataSnapshot].
 */
class Evaluator(
    private val state: Map<String, JsonPrimitive>,
    private val data: DataSnapshot?,
) {
    fun lookup(name: String, arg: String? = null): Any? {
        state[name]?.let { return it.toValue() }
        return data?.resolve(name, arg)
    }

    /** Replaces every `{...}` segment. Text without braces is returned unchanged. */
    fun template(input: String?): String {
        if (input.isNullOrEmpty()) return ""
        if (!input.contains('{')) return input
        val out = StringBuilder()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '{') {
                val end = findClosing(input, i)
                if (end < 0) { out.append(input.substring(i)); break }
                out.append(evalSegment(input.substring(i + 1, end).trim()))
                i = end + 1
            } else {
                out.append(c); i++
            }
        }
        return out.toString()
    }

    private fun findClosing(s: String, open: Int): Int {
        var quote: Char? = null
        for (j in open + 1 until s.length) {
            val ch = s[j]
            if (quote != null) { if (ch == quote) quote = null; continue }
            if (ch == '\'' || ch == '"') quote = ch
            else if (ch == '}') return j
        }
        return -1
    }

    private fun evalSegment(seg: String): String {
        val q = indexOutsideQuotes(seg, "?")
        if (q > 0) {
            val cond = seg.substring(0, q)
            val rest = seg.substring(q + 1)
            val colon = indexOutsideQuotes(rest, ":")
            val a = if (colon >= 0) rest.substring(0, colon) else rest
            val b = if (colon >= 0) rest.substring(colon + 1) else ""
            return format(operand(if (cond(cond)) a else b))
        }
        val colon = seg.indexOf(':')
        val name = if (colon > 0) seg.substring(0, colon).trim() else seg
        val arg = if (colon > 0) seg.substring(colon + 1) else null
        val v = lookup(name, arg)
        return if (v == null) (if (name.startsWith("weather")) "—" else "") else format(v)
    }

    fun cond(expr: String?): Boolean {
        if (expr.isNullOrBlank()) return true
        var e = expr.trim()
        if (e.startsWith("{") && e.endsWith("}")) e = e.substring(1, e.length - 1)
        return splitOutsideQuotes(e, "||").any { part ->
            splitOutsideQuotes(part, "&&").all { atom(it.trim()) }
        }
    }

    private fun atom(raw: String): Boolean {
        var a = raw.trim()
        while (a.startsWith("(") && a.endsWith(")")) a = a.substring(1, a.length - 1).trim()
        for (op in listOf("==", "!=", ">=", "<=", ">", "<")) {
            val idx = indexOutsideQuotes(a, op)
            if (idx > 0) {
                val l = operand(a.substring(0, idx))
                val r = operand(a.substring(idx + op.length))
                return compare(l, r, op)
            }
        }
        if (a.startsWith("!")) return !truthy(operand(a.substring(1)))
        return truthy(operand(a))
    }

    private fun compare(l: Any?, r: Any?, op: String): Boolean {
        val ln = toNumber(l); val rn = toNumber(r)
        if (ln != null && rn != null) return when (op) {
            "==" -> ln == rn; "!=" -> ln != rn; ">" -> ln > rn; "<" -> ln < rn; ">=" -> ln >= rn; else -> ln <= rn
        }
        val ls = format(l); val rs = format(r)
        return when (op) {
            "==" -> ls == rs; "!=" -> ls != rs
            ">" -> ls > rs; "<" -> ls < rs; ">=" -> ls >= rs; else -> ls <= rs
        }
    }

    /** A literal ('text', 42, true) or a token name. */
    fun operand(raw: String): Any? {
        val s = raw.trim()
        if (s.isEmpty()) return ""
        if ((s.startsWith("'") && s.endsWith("'") || s.startsWith("\"") && s.endsWith("\"")) && s.length >= 2) {
            return s.substring(1, s.length - 1)
        }
        if (s == "true") return true
        if (s == "false") return false
        s.toDoubleOrNull()?.let { return it }
        val noBraces = s.removePrefix("{").removeSuffix("}")
        val colon = noBraces.indexOf(':')
        return if (colon > 0) lookup(noBraces.substring(0, colon).trim(), noBraces.substring(colon + 1))
        else lookup(noBraces)
    }

    fun number(p: JsonPrimitive?): Double? {
        if (p == null) return null
        if (!p.isString) return p.doubleOrNull
        val s = p.content.trim()
        s.toDoubleOrNull()?.let { return it }
        return if (s.contains('{')) template(s).trim().toDoubleOrNull() else toNumber(operand(s))
    }

    companion object {
        fun truthy(v: Any?): Boolean = when (v) {
            null -> false
            is Boolean -> v
            is Number -> v.toDouble() != 0.0
            is String -> v.isNotEmpty() && v != "false" && v != "0"
            else -> true
        }

        fun toNumber(v: Any?): Double? = when (v) {
            is Number -> v.toDouble()
            is Boolean -> if (v) 1.0 else 0.0
            is String -> v.trim().toDoubleOrNull()
            else -> null
        }

        fun format(v: Any?): String = when (v) {
            null -> ""
            is Double -> if (v == Math.floor(v) && !v.isInfinite() && kotlin.math.abs(v) < 1e15) v.toLong().toString()
            else "%.1f".format(v)
            is Float -> format(v.toDouble())
            else -> v.toString()
        }

        fun indexOutsideQuotes(s: String, token: String): Int {
            var quote: Char? = null
            var i = 0
            while (i <= s.length - token.length) {
                val ch = s[i]
                if (quote != null) { if (ch == quote) quote = null; i++; continue }
                if (ch == '\'' || ch == '"') { quote = ch; i++; continue }
                if (s.startsWith(token, i)) {
                    // Do not treat "!=" as "!" or ">=" as ">" when looking for the shorter operator.
                    if (token == ">" || token == "<") {
                        if (i + 1 < s.length && s[i + 1] == '=') { i++; continue }
                    }
                    return i
                }
                i++
            }
            return -1
        }

        fun splitOutsideQuotes(s: String, sep: String): List<String> {
            val parts = mutableListOf<String>()
            var rest = s
            while (true) {
                val idx = indexOutsideQuotes(rest, sep)
                if (idx < 0) { parts += rest; return parts }
                parts += rest.substring(0, idx)
                rest = rest.substring(idx + sep.length)
            }
        }
    }
}

fun JsonPrimitive.toValue(): Any = when {
    isString -> content
    booleanOrNull != null -> booleanOrNull!!
    doubleOrNull != null -> doubleOrNull!!
    else -> content
}

fun Any?.toPrimitive(): JsonPrimitive = when (this) {
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this.toDouble())
    null -> JsonPrimitive("")
    else -> JsonPrimitive(this.toString())
}

/** onClick may be one action object or an array of them. */
fun parseActions(el: JsonElement?): List<WAction> = when (el) {
    is JsonObject -> listOfNotNull(runCatching { AppJson.decodeFromJsonElement<WAction>(el) }.getOrNull())
    is JsonArray -> el.mapNotNull { (it as? JsonObject)?.let { o -> runCatching { AppJson.decodeFromJsonElement<WAction>(o) }.getOrNull() } }
    else -> emptyList()
}

object StateOps {
    /** Applies state-changing actions. Returns the new override map (only values that differ from nothing). */
    fun apply(
        current: Map<String, JsonPrimitive>,
        actions: List<WAction>,
        data: DataSnapshot?,
    ): Map<String, JsonPrimitive> {
        val s = current.toMutableMap()
        for (a in actions) {
            val key = a.bind
            val ev = Evaluator(s, data)
            when (a.op.lowercase()) {
                "set" -> if (key != null) {
                    val v = a.value
                    s[key] = when {
                        v == null -> JsonPrimitive("")
                        v.isString -> {
                            val t = ev.template(v.content)
                            t.toDoubleOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(t)
                        }
                        else -> v
                    }
                }
                "inc", "increment", "add", "dec", "decrement", "sub" -> if (key != null) {
                    val sign = if (a.op.lowercase().startsWith("d") || a.op.lowercase() == "sub") -1 else 1
                    var n = (Evaluator.toNumber(ev.lookup(key)) ?: 0.0) + sign * (a.by ?: 1.0)
                    a.min?.let { if (n < it) n = it }
                    a.max?.let { if (n > it) n = it }
                    s[key] = JsonPrimitive(n)
                }
                "toggle" -> if (key != null) s[key] = JsonPrimitive(!Evaluator.truthy(ev.lookup(key)))
                "cycle", "next", "prev" -> if (key != null) {
                    val count = (a.count ?: a.max?.toInt()?.plus(1) ?: 2).coerceAtLeast(1)
                    val cur = (Evaluator.toNumber(ev.lookup(key)) ?: 0.0).toInt()
                    val step = if (a.op.lowercase() == "prev") -1 else 1
                    s[key] = JsonPrimitive((((cur + step) % count) + count) % count)
                }
                "reset" -> {
                    if (key != null) s.remove(key) else s.clear()
                }
            }
        }
        return s
    }
}
