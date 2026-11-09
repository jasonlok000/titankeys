package com.atlas.keyboard

/** Why the keyboard changed the text. An [automatic] change is the keyboard's own and not something the user asked for. */
enum class EditCause(val cause: String, val via: String? = null, val automatic: Boolean = false) {
    Swipe("commit", "swipe"),
    Tap("commit", "tap"),
    Key("commit", "key"),
    Symbol("commit", "symbol"),
    Space("commit", "space"),
    Newline("commit", "enter"),
    Backspace("backspace", "letter"),
    BackspaceWord("backspace", "word"),
    Choose("choose"),
    Command("command"),
    LookAlike("lookalike", automatic = true),
    FullStop("fullstop", automatic = true),
    DropSpace("spacedrop", automatic = true),
}

/** One word the swipe engine considered, with the parts of its score. */
class RawLogScore(val word: String, val total: Float, val path: Float, val context: Float)

/**
 * One line of the raw input log, a JSON object built as text. This costs little while typing, and because it
 * needs nothing from Android (unlike org.json) the format can be checked on a computer.
 */
internal class RawLogLine(kind: String) {
    private val text = StringBuilder(160).append("{\"k\":\"").append(kind).append('"')

    fun put(name: String, value: Long) = apply { key(name).append(value) }
    fun put(name: String, value: Int) = apply { key(name).append(value) }
    fun put(name: String, value: Boolean) = apply { key(name).append(value) }
    fun put(name: String, value: Float) = apply { key(name); number(value.toDouble()) }

    fun put(name: String, value: String?) = apply {
        key(name)
        string(value)
    }

    fun put(name: String, values: List<String>) = apply {
        key(name).append('[')
        for ((index, value) in values.withIndex()) {
            if (index > 0) text.append(',')
            string(value)
        }
        text.append(']')
    }

    fun put(name: String, values: Map<String, Any>) = apply {
        key(name).append('{')
        var first = true
        for ((item, value) in values) {
            if (!first) text.append(',')
            first = false
            string(item)
            text.append(':')
            when (value) {
                is Boolean -> text.append(value)
                is Float -> number(value.toDouble())
                is Double -> number(value)
                is Number -> text.append(value.toLong())
                else -> string(value.toString())
            }
        }
        text.append('}')
    }

    /** A list of `[x, y, time]` points. A position that isn't finite becomes null, as in the recorder. */
    fun points(name: String, count: Int, x: (Int) -> Float, y: (Int) -> Float, time: (Int) -> Long) = apply {
        key(name).append('[')
        for (index in 0 until count) {
            if (index > 0) text.append(',')
            text.append('[')
            number(x(index).toDouble())
            text.append(',')
            number(y(index).toDouble())
            text.append(',').append(time(index)).append(']')
        }
        text.append(']')
    }

    fun scores(name: String, values: List<RawLogScore>) = apply {
        key(name).append('[')
        for ((index, value) in values.withIndex()) {
            if (index > 0) text.append(',')
            text.append('[')
            string(value.word)
            for (part in listOf(value.total, value.path, value.context)) {
                text.append(',')
                number(part.toDouble())
            }
            text.append(']')
        }
        text.append(']')
    }

    override fun toString() = "$text}"

    private fun key(name: String) = text.append(",\"").append(name).append("\":")

    /** The recorder's rounding: five decimal places, with no decimal point on a whole number. */
    private fun number(value: Double) {
        if (!value.isFinite()) {
            text.append("null")
            return
        }
        val rounded = Math.round(value * 100000.0) / 100000.0
        if (rounded == rounded.toLong().toDouble()) text.append(rounded.toLong()) else text.append(rounded)
    }

    private fun string(value: String?) {
        if (value == null) {
            text.append("null")
            return
        }
        text.append('"')
        var index = 0
        while (index < value.length) {
            val character = value[index]
            when {
                character == '"' -> text.append("\\\"")
                character == '\\' -> text.append("\\\\")
                character == '\n' -> text.append("\\n")
                character == '\r' -> text.append("\\r")
                character == '\t' -> text.append("\\t")
                character < ' ' -> escape(character)
                character.isHighSurrogate() && index + 1 < value.length && value[index + 1].isLowSurrogate() -> {
                    text.append(character).append(value[++index])
                }
                // Half of a pair can't be written as UTF-8, and would come back as a question mark.
                character.isSurrogate() -> escape(character)
                else -> text.append(character)
            }
            index++
        }
        text.append('"')
    }

    private fun escape(character: Char) {
        text.append("\\u").append(character.code.toString(16).padStart(4, '0'))
    }
}

/**
 * The lines of the raw input log. `m`, `key`, `out` and `rec` lines have the recorder's shapes, with extra fields added
 * after them. The format is described in android/debug/recorder/README.md.
 */
internal object RawLogLines {
    const val FORMAT = 1

    fun motion(action: Int, time: Long, rawX: Float, rawY: Float,
               points: Int, x: (Int) -> Float, y: (Int) -> Float, pointTime: (Int) -> Long,
               samples: Int, sampleX: (Int) -> Float, sampleY: (Int) -> Float, sampleTime: (Int) -> Long) =
        RawLogLine("m").put("a", action).put("t", time).put("rx", rawX).put("ry", rawY)
            .points("p", points, x, y, pointTime).points("s", samples, sampleX, sampleY, sampleTime).toString()

    fun key(code: Int, down: Boolean, time: Long, gesture: Boolean, meta: Int, repeat: Int) =
        RawLogLine("key").put("c", code).put("d", down).put("t", time).put("g", gesture)
            .put("meta", meta).put("repeat", repeat).toString()

    fun swipe(points: Int, start: Long, end: Long, deletesWord: Boolean, time: Long): String {
        val line = RawLogLine("out").put("type", "swipe").put("n", points).put("start", start).put("end", end)
        if (deletesWord) line.put("deletes", true)
        return line.put("t", time).toString()
    }

    fun tap(key: String, end: Long, time: Long) =
        RawLogLine("out").put("type", "tap").put("key", key).put("end", end).put("t", time).toString()

    fun recognised(words: List<String>, resumed: Boolean, scores: List<RawLogScore>?, time: Long): String {
        val line = RawLogLine("rec").put("words", words).put("resumed", resumed)
        if (scores != null) line.scores("scores", scores)
        return line.put("t", time).toString()
    }

    fun edit(cause: EditCause, deleted: Int, inserted: String, deletedAfter: Int, accepted: Boolean, time: Long): String {
        val line = RawLogLine("text").put("t", time).put("cause", cause.cause)
        if (cause.via != null) line.put("via", cause.via)
        return line.put("auto", cause.automatic).put("del", deleted).put("ins", inserted)
            .put("aft", deletedAfter).put("ok", accepted).toString()
    }

    fun picked(index: Int, word: String, offered: List<String>, time: Long) =
        RawLogLine("pick").put("t", time).put("index", index).put("word", word).put("offered", offered).toString()

    fun selection(start: Int, end: Int, composingStart: Int, composingEnd: Int, before: String?, time: Long) =
        RawLogLine("sel").put("t", time).put("start", start).put("end", end)
            .put("cs", composingStart).put("ce", composingEnd).put("before", before).toString()

    fun fieldStart(inputType: Int, imeOptions: Int, reference: Boolean, restarting: Boolean,
                   selectionStart: Int, selectionEnd: Int, before: String?, time: Long) =
        RawLogLine("start").put("t", time).put("inputType", inputType).put("imeOptions", imeOptions)
            .put("reference", reference).put("restarting", restarting)
            .put("selStart", selectionStart).put("selEnd", selectionEnd).put("before", before).toString()

    fun fieldEnd(time: Long) = RawLogLine("end").put("t", time).toString()

    /** Marks input that was not recorded, so that nothing is linked across it. [count] is how many lines, if known. */
    fun gap(why: String, count: Long, time: Long) = RawLogLine("gap").put("t", time).put("why", why).put("n", count).toString()

    fun mark(kind: String?, label: String?, time: Long) =
        RawLogLine("mark").put("t", time).put("kind", kind).put("label", label).toString()

    fun header(time: Long, wallClock: Long, version: String?, model: String?, android: String?, slipMillis: Int,
               settings: Map<String, Any>, tuning: Map<String, Any>) =
        RawLogLine("header").put("format", FORMAT).put("t", time).put("wall", wallClock).put("atlas", version)
            .put("model", model).put("android", android).put("slipMillis", slipMillis)
            .put("settings", settings).put("tuning", tuning).toString()
}
