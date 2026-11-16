package game.storage

class SaveFormatException(message: String) : RuntimeException(message)

/**
 * Minimal JSON writer/parser (no external libraries). Values: Map<String,Any?>, List<Any?>, String, Long, Double, Boolean, null.
 * Any malformed input raises SaveFormatException, never another exception type.
 */
object Json {
    private const val MAX_DEPTH = 64

    fun write(v: Any?): String = StringBuilder().also { write(v, it) }.toString()

    private fun write(v: Any?, sb: StringBuilder) {
        when (v) {
            null -> sb.append("null")
            is Boolean -> sb.append(v)
            is Int -> sb.append(v)
            is Long -> sb.append(v)
            is Float -> sb.append(v.toDouble())
            is Double -> { require(v.isFinite()) { "non-finite number" }; sb.append(v) }
            is String -> writeString(v, sb)
            is Map<*, *> -> { sb.append('{'); var first = true
                for ((k, x) in v) { if (!first) sb.append(','); first = false; writeString(k as String, sb); sb.append(':'); write(x, sb) }
                sb.append('}') }
            is List<*> -> { sb.append('['); v.forEachIndexed { i, x -> if (i > 0) sb.append(','); write(x, sb) }; sb.append(']') }
            else -> throw IllegalArgumentException("unsupported JSON value ${v::class}")
        }
    }

    private fun writeString(s: String, sb: StringBuilder) {
        sb.append('"')
        for (c in s) when {
            c == '"' -> sb.append("\\\""); c == '\\' -> sb.append("\\\\"); c == '\n' -> sb.append("\\n")
            c == '\r' -> sb.append("\\r"); c == '\t' -> sb.append("\\t")
            c < ' ' -> sb.append("\\u%04x".format(c.code)); else -> sb.append(c)
        }
        sb.append('"')
    }

    fun parse(text: String): Any? {
        val p = Parser(text)
        val v = p.value(0)
        p.skip()
        if (!p.atEnd()) throw SaveFormatException("trailing data")
        return v
    }

    private class Parser(val s: String) {
        var i = 0
        fun atEnd() = i >= s.length
        fun skip() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun fail(m: String): Nothing = throw SaveFormatException("$m at $i")

        fun value(depth: Int): Any? {
            if (depth > MAX_DEPTH) fail("too deep")
            skip(); if (atEnd()) fail("unexpected end")
            return when (val c = s[i]) {
                '{' -> obj(depth); '[' -> arr(depth); '"' -> str()
                't' -> lit("true", true); 'f' -> lit("false", false); 'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else fail("unexpected '$c'")
            }
        }
        fun lit(w: String, v: Any?): Any? { if (!s.startsWith(w, i)) fail("bad literal"); i += w.length; return v }
        fun obj(d: Int): Map<String, Any?> {
            i++; val m = LinkedHashMap<String, Any?>(); skip()
            if (i < s.length && s[i] == '}') { i++; return m }
            while (true) {
                skip(); if (atEnd() || s[i] != '"') fail("key expected")
                val k = str(); skip(); if (atEnd() || s[i] != ':') fail("':' expected"); i++
                m[k] = value(d + 1); skip()
                if (atEnd()) fail("unterminated object")
                if (s[i] == ',') { i++; continue }
                if (s[i] == '}') { i++; return m }
                fail("',' or '}' expected")
            }
        }
        fun arr(d: Int): List<Any?> {
            i++; val l = ArrayList<Any?>(); skip()
            if (i < s.length && s[i] == ']') { i++; return l }
            while (true) {
                l += value(d + 1); skip()
                if (atEnd()) fail("unterminated array")
                if (s[i] == ',') { i++; continue }
                if (s[i] == ']') { i++; return l }
                fail("',' or ']' expected")
            }
        }
        fun str(): String {
            i++; val sb = StringBuilder()
            while (true) {
                if (atEnd()) fail("unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> { if (atEnd()) fail("bad escape"); when (val e = s[i++]) {
                        '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/'); 'n' -> sb.append('\n')
                        'r' -> sb.append('\r'); 't' -> sb.append('\t'); 'b' -> sb.append('\b'); 'f' -> sb.append('\u000c')
                        'u' -> { if (i + 4 > s.length) fail("bad \\u"); sb.append(s.substring(i, i + 4).toIntOrNull(16)?.toChar() ?: fail("bad \\u")); i += 4 }
                        else -> fail("bad escape '$e'") } }
                    else -> sb.append(c)
                }
            }
        }
        fun num(): Any {
            val st = i; if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            val t = s.substring(st, i)
            return if (t.any { it in ".eE" }) t.toDoubleOrNull() ?: fail("bad number") else t.toLongOrNull() ?: fail("bad number")
        }
    }
}

// Typed accessors that report problems as SaveFormatException.
internal fun Any?.asMap(): Map<String, Any?> { @Suppress("UNCHECKED_CAST") return (this as? Map<String, Any?>) ?: throw SaveFormatException("object expected") }
internal fun Any?.asList(): List<Any?> = (this as? List<*>) ?: throw SaveFormatException("array expected")
internal fun Any?.asInt(): Int = ((this as? Long) ?: throw SaveFormatException("integer expected")).also { if (it !in Int.MIN_VALUE..Int.MAX_VALUE) throw SaveFormatException("integer out of range") }.toInt()
internal fun Any?.asLong(): Long = (this as? Long) ?: throw SaveFormatException("integer expected")
internal fun Any?.asBool(): Boolean = (this as? Boolean) ?: throw SaveFormatException("boolean expected")
internal fun Any?.asString(): String = (this as? String) ?: throw SaveFormatException("string expected")
internal fun Any?.asDouble(): Double = when (this) { is Double -> this; is Long -> toDouble(); else -> throw SaveFormatException("number expected") }
