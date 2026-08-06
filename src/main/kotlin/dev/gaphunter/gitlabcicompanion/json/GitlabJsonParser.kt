package dev.gaphunter.gitlabcicompanion.json

/**
 * Minimal, hand-rolled JSON reader for GitLab's REST API v4 responses.
 * Same "hand-roll over new dependency" call as elsewhere in this
 * workspace (see MinimalJsonParser.kt in firestore-companion for the
 * rationale spelled out in more detail) -- each plugin here is a
 * separate published product/repo, so this is an independent, smaller
 * implementation, not shared code.
 */
object GitlabJsonParser {
    fun parse(text: String): GitlabJsonNode {
        val parser = Parser(text)
        val value = parser.parseValue()
        parser.skipWhitespace()
        if (!parser.atEnd()) throw GitlabJsonException("Unexpected trailing content")
        return value
    }

    private class Parser(private val text: String) {
        var pos = 0

        fun atEnd() = pos >= text.length
        fun peek(): Char = text[pos]
        fun skipWhitespace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        fun parseValue(): GitlabJsonNode {
            skipWhitespace()
            if (atEnd()) throw GitlabJsonException("Unexpected end of input")
            return when (peek()) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> GitlabJsonNode.Str(parseString())
                't' -> parseLiteral("true", GitlabJsonNode.Bool(true))
                'f' -> parseLiteral("false", GitlabJsonNode.Bool(false))
                'n' -> parseLiteral("null", GitlabJsonNode.Null)
                else -> parseNumber()
            }
        }

        fun parseLiteral(literal: String, value: GitlabJsonNode): GitlabJsonNode {
            if (pos + literal.length > text.length || text.substring(pos, pos + literal.length) != literal) {
                throw GitlabJsonException("Invalid literal at $pos")
            }
            pos += literal.length
            return value
        }

        fun parseObject(): GitlabJsonNode.Obj {
            pos++
            val entries = LinkedHashMap<String, GitlabJsonNode>()
            skipWhitespace()
            if (!atEnd() && peek() == '}') {
                pos++
                return GitlabJsonNode.Obj(entries)
            }
            while (true) {
                skipWhitespace()
                if (atEnd() || peek() != '"') throw GitlabJsonException("Expected string key at $pos")
                val key = parseString()
                skipWhitespace()
                if (atEnd() || peek() != ':') throw GitlabJsonException("Expected ':' at $pos")
                pos++
                entries[key] = parseValue()
                skipWhitespace()
                if (atEnd()) throw GitlabJsonException("Unterminated object")
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return GitlabJsonNode.Obj(entries)
                    }
                    else -> throw GitlabJsonException("Expected ',' or '}' at $pos")
                }
            }
        }

        fun parseArray(): GitlabJsonNode.Arr {
            pos++
            val items = mutableListOf<GitlabJsonNode>()
            skipWhitespace()
            if (!atEnd() && peek() == ']') {
                pos++
                return GitlabJsonNode.Arr(items)
            }
            while (true) {
                items.add(parseValue())
                skipWhitespace()
                if (atEnd()) throw GitlabJsonException("Unterminated array")
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return GitlabJsonNode.Arr(items)
                    }
                    else -> throw GitlabJsonException("Expected ',' or ']' at $pos")
                }
            }
        }

        fun parseString(): String {
            pos++
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw GitlabJsonException("Unterminated string")
                val c = text[pos]
                pos++
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (atEnd()) throw GitlabJsonException("Unterminated escape")
                        val escaped = text[pos]
                        pos++
                        when (escaped) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            'r' -> sb.append('\r')
                            'b' -> sb.append('\b')
                            'u' -> {
                                if (pos + 4 > text.length) throw GitlabJsonException("Invalid unicode escape")
                                val code = text.substring(pos, pos + 4).toIntOrNull(16)
                                    ?: throw GitlabJsonException("Invalid unicode escape")
                                pos += 4
                                sb.append(code.toChar())
                            }
                            else -> throw GitlabJsonException("Invalid escape '\\$escaped'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        fun parseNumber(): GitlabJsonNode.Num {
            val start = pos
            if (!atEnd() && peek() == '-') pos++
            while (!atEnd() && peek().isDigit()) pos++
            if (!atEnd() && peek() == '.') {
                pos++
                while (!atEnd() && peek().isDigit()) pos++
            }
            if (!atEnd() && (peek() == 'e' || peek() == 'E')) {
                pos++
                if (!atEnd() && (peek() == '+' || peek() == '-')) pos++
                while (!atEnd() && peek().isDigit()) pos++
            }
            if (pos == start) throw GitlabJsonException("Invalid number at $pos")
            val numberText = text.substring(start, pos)
            return GitlabJsonNode.Num(
                numberText.toDoubleOrNull() ?: throw GitlabJsonException("Invalid number '$numberText'")
            )
        }
    }
}
