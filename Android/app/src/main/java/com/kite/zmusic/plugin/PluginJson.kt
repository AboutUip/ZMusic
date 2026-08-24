package com.kite.zmusic.plugin

/**
 * 小型 JSON 读写。失败返回 null（含尾随逗号、截断）。
 * 不用 `org.json`，以便 JVM 单元测试不踩 Android stub。
 */
internal object PluginJson {
    fun parse(text: String): Any? {
        val src = text.removePrefix("\uFEFF")
        val p = Parser(src)
        val value = p.parseValue() ?: return null
        p.skipWs()
        return if (p.done) value.value else null
    }

    @Suppress("UNCHECKED_CAST")
    fun parseObject(text: String): Map<String, Any?>? =
        parse(text) as? Map<String, Any?>

    fun stringify(value: Any?): String = buildString { write(value) }

    private class Parsed(val value: Any?)

    private fun StringBuilder.write(value: Any?) {
        when (value) {
            null -> append("null")
            is Boolean -> append(value)
            is Int -> append(value)
            is Long -> append(value)
            is Double -> append(value)
            is String -> writeString(value)
            is Map<*, *> -> {
                append('{')
                var first = true
                value.forEach { (k, v) ->
                    if (k !is String) return@forEach
                    if (!first) append(',')
                    first = false
                    writeString(k)
                    append(':')
                    write(v)
                }
                append('}')
            }
            is List<*> -> {
                append('[')
                value.forEachIndexed { i, v ->
                    if (i > 0) append(',')
                    write(v)
                }
                append(']')
            }
            else -> append("null")
        }
    }

    private fun StringBuilder.writeString(s: String) {
        append('"')
        s.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code < 0x20) {
                    append("\\u").append(c.code.toString(16).padStart(4, '0'))
                } else {
                    append(c)
                }
            }
        }
        append('"')
    }

    private class Parser(private val s: String) {
        var i = 0
        val done: Boolean get() = i >= s.length

        fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun parseValue(): Parsed? {
            skipWs()
            if (i >= s.length) return null
            return when (s[i]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()?.let { Parsed(it) }
                't' -> keyword("true", true)
                'f' -> keyword("false", false)
                'n' -> keyword("null", null)
                '-', in '0'..'9' -> parseNumber()
                else -> null
            }
        }

        private fun keyword(word: String, value: Any?): Parsed? {
            if (!s.startsWith(word, i)) return null
            i += word.length
            return Parsed(value)
        }

        private fun parseObject(): Parsed? {
            i++
            val out = LinkedHashMap<String, Any?>()
            skipWs()
            if (i < s.length && s[i] == '}') {
                i++
                return Parsed(out)
            }
            while (i < s.length) {
                skipWs()
                val key = parseString() ?: return null
                skipWs()
                if (i >= s.length || s[i] != ':') return null
                i++
                val value = parseValue() ?: return null
                out[key] = value.value
                skipWs()
                if (i >= s.length) return null
                when (s[i]) {
                    ',' -> {
                        i++
                        skipWs()
                        if (i >= s.length || s[i] == '}') return null
                    }
                    '}' -> {
                        i++
                        return Parsed(out)
                    }
                    else -> return null
                }
            }
            return null
        }

        private fun parseArray(): Parsed? {
            i++
            val out = ArrayList<Any?>()
            skipWs()
            if (i < s.length && s[i] == ']') {
                i++
                return Parsed(out)
            }
            while (i < s.length) {
                val value = parseValue() ?: return null
                out.add(value.value)
                skipWs()
                if (i >= s.length) return null
                when (s[i]) {
                    ',' -> {
                        i++
                        skipWs()
                        if (i >= s.length || s[i] == ']') return null
                    }
                    ']' -> {
                        i++
                        return Parsed(out)
                    }
                    else -> return null
                }
            }
            return null
        }

        private fun parseString(): String? {
            if (i >= s.length || s[i] != '"') return null
            i++
            val sb = StringBuilder()
            while (i < s.length) {
                when (val c = s[i++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) return null
                        when (val e = s[i++]) {
                            '"', '\\', '/' -> sb.append(e)
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) return null
                                val hex = s.substring(i, i + 4)
                                val code = hex.toIntOrNull(16) ?: return null
                                sb.append(code.toChar())
                                i += 4
                            }
                            else -> return null
                        }
                    }
                    else -> {
                        if (c.code < 0x20) return null
                        sb.append(c)
                    }
                }
            }
            return null
        }

        private fun parseNumber(): Parsed? {
            val start = i
            if (s[i] == '-') i++
            if (i >= s.length || s[i] !in '0'..'9') return null
            if (s[i] == '0') {
                i++
            } else {
                while (i < s.length && s[i] in '0'..'9') i++
            }
            var fractional = false
            if (i < s.length && s[i] == '.') {
                fractional = true
                i++
                if (i >= s.length || s[i] !in '0'..'9') return null
                while (i < s.length && s[i] in '0'..'9') i++
            }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                fractional = true
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                if (i >= s.length || s[i] !in '0'..'9') return null
                while (i < s.length && s[i] in '0'..'9') i++
            }
            val token = s.substring(start, i)
            if (!fractional) {
                val l = token.toLongOrNull() ?: return null
                val n: Any = if (l in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                    l.toInt()
                } else {
                    l
                }
                return Parsed(n)
            }
            val d = token.toDoubleOrNull() ?: return null
            return Parsed(d)
        }
    }
}
