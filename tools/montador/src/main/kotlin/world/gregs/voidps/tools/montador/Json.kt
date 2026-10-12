package world.gregs.voidps.tools.montador

/**
 * Minimal JSON writer so the exporter needs no extra dependency.
 * Values: null, Boolean, Number, String, Map<String, Any?>, List/Array/IntArray/ByteArray.
 */
object Json {

    fun write(value: Any?): String {
        val builder = StringBuilder()
        append(builder, value)
        return builder.toString()
    }

    private fun append(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is Boolean -> out.append(value)
            is Int, is Long, is Short, is Byte -> out.append(value)
            is Float, is Double -> {
                val number = (value as Number).toDouble()
                if (number.isFinite()) out.append(number) else out.append("null")
            }
            is String -> string(out, value)
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((key, item) in value) {
                    if (!first) {
                        out.append(',')
                    }
                    first = false
                    string(out, key.toString())
                    out.append(':')
                    append(out, item)
                }
                out.append('}')
            }
            is IntArray -> list(out, value.asList())
            is ByteArray -> list(out, value.map { it.toInt() })
            is Array<*> -> list(out, value.asList())
            is Iterable<*> -> list(out, value)
            else -> string(out, value.toString())
        }
    }

    /** Reads JSON text into null, Boolean, Long, Double, String, List<Any?> and Map<String, Any?>. */
    fun read(text: String): Any? {
        val reader = Reader(text)
        val value = reader.value()
        reader.space()
        require(reader.at == text.length) { "JSON: text after the value at ${reader.at}" }
        return value
    }

    private class Reader(val text: String) {
        var at = 0

        fun space() {
            while (at < text.length && text[at].isWhitespace()) {
                at++
            }
        }

        fun value(): Any? {
            space()
            require(at < text.length) { "JSON: ended early" }
            return when (val char = text[at]) {
                '{' -> obj()
                '[' -> array()
                '"' -> string()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> if (char == '-' || char.isDigit()) number() else throw IllegalArgumentException("JSON: unexpected '$char' at $at")
            }
        }

        fun word(word: String, value: Any?): Any? {
            require(text.startsWith(word, at)) { "JSON: expected $word at $at" }
            at += word.length
            return value
        }

        fun number(): Any {
            val start = at
            while (at < text.length && (text[at].isDigit() || text[at] in "+-.eE")) {
                at++
            }
            val raw = text.substring(start, at)
            return raw.toLongOrNull() ?: raw.toDouble()
        }

        fun string(): String {
            at++
            val out = StringBuilder()
            while (true) {
                require(at < text.length) { "JSON: unclosed string" }
                val char = text[at++]
                when (char) {
                    '"' -> return out.toString()
                    '\\' -> {
                        when (val escaped = text[at++]) {
                            'n' -> out.append('\n')
                            't' -> out.append('\t')
                            'r' -> out.append('\r')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000c')
                            'u' -> {
                                out.append(text.substring(at, at + 4).toInt(16).toChar())
                                at += 4
                            }
                            else -> out.append(escaped)
                        }
                    }
                    else -> out.append(char)
                }
            }
        }

        fun array(): List<Any?> {
            at++
            val list = mutableListOf<Any?>()
            space()
            if (text[at] == ']') {
                at++
                return list
            }
            while (true) {
                list.add(value())
                space()
                when (text[at++]) {
                    ',' -> continue
                    ']' -> return list
                    else -> throw IllegalArgumentException("JSON: expected , or ] at ${at - 1}")
                }
            }
        }

        fun obj(): Map<String, Any?> {
            at++
            val map = linkedMapOf<String, Any?>()
            space()
            if (text[at] == '}') {
                at++
                return map
            }
            while (true) {
                space()
                val key = string()
                space()
                require(text[at++] == ':') { "JSON: expected : at ${at - 1}" }
                map[key] = value()
                space()
                when (text[at++]) {
                    ',' -> continue
                    '}' -> return map
                    else -> throw IllegalArgumentException("JSON: expected , or } at ${at - 1}")
                }
            }
        }
    }

    private fun list(out: StringBuilder, values: Iterable<*>) {
        out.append('[')
        var first = true
        for (item in values) {
            if (!first) {
                out.append(',')
            }
            first = false
            append(out, item)
        }
        out.append(']')
    }

    private fun string(out: StringBuilder, value: String) {
        out.append('"')
        for (char in value) {
            when {
                char == '"' -> out.append("\\\"")
                char == '\\' -> out.append("\\\\")
                char == '\n' -> out.append("\\n")
                char == '\r' -> out.append("\\r")
                char == '\t' -> out.append("\\t")
                char < ' ' -> out.append(String.format("\\u%04x", char.code))
                else -> out.append(char)
            }
        }
        out.append('"')
    }
}
