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
