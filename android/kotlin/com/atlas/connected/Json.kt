package com.atlas.connected

/**
 * A small JSON reader and writer for the connected features. It needs no Android classes, so the JVM tests run the same
 * code as the phone. Objects read as [Map], arrays as [List], numbers as [Double].
 */
internal object Json {
    class Error(message: String) : Exception(message)

    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.value(0)
        reader.space()
        if (!reader.end) throw Error("Text after the JSON value")
        return value
    }

    /** A JSON string literal, with quotes. */
    fun quote(value: String): String {
        val out = StringBuilder(value.length + 2).append('"')
        for (c in value) when {
            c == '"' -> out.append("\\\"")
            c == '\\' -> out.append("\\\\")
            c == '\n' -> out.append("\\n")
            c == '\r' -> out.append("\\r")
            c == '\t' -> out.append("\\t")
            c < ' ' || c == ' ' || c == ' ' -> out.append(String.format("\\u%04x", c.code))
            else -> out.append(c)
        }
        return out.append('"').toString()
    }

    /** A number as JSON. NaN and infinity have no JSON form, so they become null. */
    fun number(value: Double): String = when {
        !value.isFinite() -> "null"
        value == Math.rint(value) && Math.abs(value) < 1e15 -> value.toLong().toString()
        else -> value.toString()
    }

    private const val MAX_DEPTH = 32

    private class Reader(val text: String) {
        var at = 0
        val end get() = at >= text.length

        fun space() {
            while (!end && text[at].let { it == ' ' || it == '\n' || it == '\r' || it == '\t' }) at++
        }

        fun value(depth: Int): Any? {
            if (depth > MAX_DEPTH) throw Error("JSON nested too deeply")
            space()
            if (end) throw Error("JSON ended early")
            return when (val c = text[at]) {
                '{' -> objectValue(depth)
                '[' -> arrayValue(depth)
                '"' -> string()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> if (c == '-' || c in '0'..'9') number() else throw Error("Unexpected '$c' at $at")
            }
        }

        private fun word(word: String, value: Any?): Any? {
            if (!text.startsWith(word, at)) throw Error("Unexpected text at $at")
            at += word.length
            return value
        }

        private fun objectValue(depth: Int): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            at++
            space()
            if (!end && text[at] == '}') { at++; return map }
            while (true) {
                space()
                if (end || text[at] != '"') throw Error("Expected a key at $at")
                val key = string()
                space()
                if (end || text[at] != ':') throw Error("Expected ':' at $at")
                at++
                if (map.containsKey(key)) throw Error("Repeated key $key")
                map[key] = value(depth + 1)
                space()
                if (end) throw Error("JSON ended early")
                when (text[at++]) {
                    ',' -> continue
                    '}' -> return map
                    else -> throw Error("Expected ',' or '}' at ${at - 1}")
                }
            }
        }

        private fun arrayValue(depth: Int): List<Any?> {
            val list = ArrayList<Any?>()
            at++
            space()
            if (!end && text[at] == ']') { at++; return list }
            while (true) {
                list += value(depth + 1)
                space()
                if (end) throw Error("JSON ended early")
                when (text[at++]) {
                    ',' -> continue
                    ']' -> return list
                    else -> throw Error("Expected ',' or ']' at ${at - 1}")
                }
            }
        }

        private fun string(): String {
            val out = StringBuilder()
            at++
            while (true) {
                if (end) throw Error("String ended early")
                val c = text[at++]
                when {
                    c == '"' -> return out.toString()
                    c == '\\' -> {
                        if (end) throw Error("String ended early")
                        when (val e = text[at++]) {
                            '"', '\\', '/' -> out.append(e)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (at + 4 > text.length) throw Error("Bad escape")
                                out.append(text.substring(at, at + 4).toIntOrNull(16)?.toChar() ?: throw Error("Bad escape"))
                                at += 4
                            }
                            else -> throw Error("Bad escape '\\$e'")
                        }
                    }
                    c < ' ' -> throw Error("Control character in a string")
                    else -> out.append(c)
                }
            }
        }

        private fun number(): Double {
            val start = at
            if (text[at] == '-') at++
            while (!end && (text[at].isDigit() || text[at] in ".eE+-")) at++
            return text.substring(start, at).toDoubleOrNull()?.takeIf { it.isFinite() } ?: throw Error("Bad number at $start")
        }
    }
}

/** Reading helpers that say which field was wrong. */
internal fun Map<String, Any?>.text(key: String): String = this[key] as? String ?: throw Json.Error("$key must be text")
internal fun Map<String, Any?>.textOrNull(key: String): String? =
    if (this[key] == null) null else this[key] as? String ?: throw Json.Error("$key must be text")
@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.objectOrNull(key: String): Map<String, Any?>? =
    if (this[key] == null) null else this[key] as? Map<String, Any?> ?: throw Json.Error("$key must be an object")
internal fun Map<String, Any?>.list(key: String): List<Any?> = this[key] as? List<Any?> ?: throw Json.Error("$key must be a list")
internal fun Map<String, Any?>.number(key: String): Double = this[key] as? Double ?: throw Json.Error("$key must be a number")
