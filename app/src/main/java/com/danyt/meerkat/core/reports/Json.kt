package com.danyt.meerkat.core.reports

/**
 * A small JSON reader and writer, enough for report files.
 *
 * Values are plain Kotlin types: `Map<String, Any?>`, `List<Any?>`, `String`, `Long`, `Double`, `Boolean`, and
 * null. The reader is strict and limits nesting, because imported files come from outside the app.
 */
internal object Json {
    private const val MAX_DEPTH = 16

    /** Writes [value] as compact JSON. Throws [IllegalArgumentException] for a type JSON cannot hold. */
    fun write(value: Any?): String = StringBuilder().also { write(value, it) }.toString()

    /** Reads one JSON document. Throws [JsonException] when the text is not valid JSON. */
    fun read(text: String): Any? {
        val reader = Reader(text)
        val value = reader.value(depth = 0)
        reader.skipSpace()
        if (!reader.atEnd()) reader.fail("Unexpected text after the document")
        return value
    }

    private fun write(value: Any?, out: StringBuilder) {
        when (value) {
            null -> out.append("null")
            is Boolean, is Int, is Long -> out.append(value)
            is Double -> out.append(if (value.isFinite()) value.toString() else "null")
            is String -> writeString(value, out)
            is Map<*, *> -> {
                out.append('{')
                value.entries.forEachIndexed { index, (key, item) ->
                    if (index > 0) out.append(',')
                    writeString(key as? String ?: throw IllegalArgumentException("JSON keys must be text"), out)
                    out.append(':')
                    write(item, out)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                value.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    write(item, out)
                }
                out.append(']')
            }
            else -> throw IllegalArgumentException("Cannot write ${value::class.simpleName} as JSON")
        }
    }

    private fun writeString(text: String, out: StringBuilder) {
        out.append('"')
        for (char in text) {
            when {
                char == '"' -> out.append("\\\"")
                char == '\\' -> out.append("\\\\")
                char == '\n' -> out.append("\\n")
                char == '\r' -> out.append("\\r")
                char == '\t' -> out.append("\\t")
                char < ' ' -> out.append("\\u").append(char.code.toString(16).padStart(4, '0'))
                else -> out.append(char)
            }
        }
        out.append('"')
    }

    private class Reader(private val text: String) {
        private var position = 0

        fun atEnd() = position >= text.length

        fun fail(message: String): Nothing = throw JsonException("$message at character $position")

        fun skipSpace() {
            while (!atEnd() && text[position] in " \t\r\n") position++
        }

        fun value(depth: Int): Any? {
            if (depth > MAX_DEPTH) fail("Nested too deeply")
            skipSpace()
            if (atEnd()) fail("Unexpected end")
            return when (val char = text[position]) {
                '{' -> readObject(depth)
                '[' -> readArray(depth)
                '"' -> readString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (char == '-' || char.isDigit()) readNumber() else fail("Unexpected character")
            }
        }

        private fun readObject(depth: Int): Map<String, Any?> {
            val result = LinkedHashMap<String, Any?>()
            position++
            skipSpace()
            if (peek() == '}') {
                position++
                return result
            }
            while (true) {
                skipSpace()
                if (peek() != '"') fail("Expected a key")
                val key = readString()
                skipSpace()
                if (peek() != ':') fail("Expected ':'")
                position++
                result[key] = value(depth + 1)
                skipSpace()
                when (peek()) {
                    ',' -> position++
                    '}' -> {
                        position++
                        return result
                    }
                    else -> fail("Expected ',' or '}'")
                }
            }
        }

        private fun readArray(depth: Int): List<Any?> {
            val result = ArrayList<Any?>()
            position++
            skipSpace()
            if (peek() == ']') {
                position++
                return result
            }
            while (true) {
                result += value(depth + 1)
                skipSpace()
                when (peek()) {
                    ',' -> position++
                    ']' -> {
                        position++
                        return result
                    }
                    else -> fail("Expected ',' or ']'")
                }
            }
        }

        private fun readString(): String {
            val out = StringBuilder()
            position++
            while (true) {
                if (atEnd()) fail("Text is not closed")
                val char = text[position++]
                when {
                    char == '"' -> return out.toString()
                    char == '\\' -> out.append(readEscape())
                    char < ' ' -> fail("Control character in text")
                    else -> out.append(char)
                }
            }
        }

        private fun readEscape(): Char {
            if (atEnd()) fail("Text is not closed")
            return when (val char = text[position++]) {
                '"', '\\', '/' -> char
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'b' -> '\b'
                'f' -> '\u000C'
                'u' -> {
                    if (position + 4 > text.length) fail("Short unicode escape")
                    val code = text.substring(position, position + 4).toIntOrNull(16) ?: fail("Bad unicode escape")
                    position += 4
                    code.toChar()
                }
                else -> fail("Unknown escape")
            }
        }

        private fun readNumber(): Any {
            val start = position
            while (!atEnd() && (text[position].isDigit() || text[position] in "+-.eE")) position++
            val number = text.substring(start, position)
            return number.toLongOrNull() ?: number.toDoubleOrNull() ?: fail("Bad number")
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!text.startsWith(word, position)) fail("Unexpected word")
            position += word.length
            return value
        }

        private fun peek(): Char = if (atEnd()) fail("Unexpected end") else text[position]
    }
}

/** Thrown when text is not valid JSON. */
class JsonException(message: String) : Exception(message)
