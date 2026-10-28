package dev.openmacro.core.model.text

/**
 * Arithmetic for numeric variables: `+ - * / %`, parentheses, unary minus and decimals.
 * Evaluated after magic text substitution, so `{lv=count} + 1` works.
 */
object Expression {

    class ParseException(message: String) : IllegalArgumentException(message)

    fun evaluate(input: String): Double = Parser(input.trim()).run {
        val value = expression()
        skipSpaces()
        if (pos != text.length) throw ParseException("Unexpected '${text[pos]}' at ${pos + 1}")
        value
    }

    fun evaluateOrNull(input: String): Double? = try {
        evaluate(input)
    } catch (e: ParseException) {
        null
    }

    private class Parser(val text: String) {
        var pos = 0

        fun skipSpaces() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        private fun peek(): Char? {
            skipSpaces()
            return text.getOrNull(pos)
        }

        fun expression(): Double {
            var value = term()
            while (true) {
                value = when (peek()) {
                    '+' -> { pos++; value + term() }
                    '-' -> { pos++; value - term() }
                    else -> return value
                }
            }
        }

        private fun term(): Double {
            var value = unary()
            while (true) {
                value = when (peek()) {
                    '*' -> { pos++; value * unary() }
                    '/' -> {
                        pos++
                        val divisor = unary()
                        if (divisor == 0.0) throw ParseException("Division by zero")
                        value / divisor
                    }
                    '%' -> {
                        pos++
                        val divisor = unary()
                        if (divisor == 0.0) throw ParseException("Division by zero")
                        value % divisor
                    }
                    else -> return value
                }
            }
        }

        private fun unary(): Double = when (peek()) {
            '-' -> { pos++; -unary() }
            '+' -> { pos++; unary() }
            else -> primary()
        }

        private fun primary(): Double {
            if (peek() == '(') {
                pos++
                val value = expression()
                if (peek() != ')') throw ParseException("Missing ')'")
                pos++
                return value
            }
            skipSpaces()
            val start = pos
            while (pos < text.length && (text[pos].isDigit() || text[pos] == '.')) pos++
            if (start == pos) throw ParseException(if (pos < text.length) "Unexpected '${text[pos]}'" else "Unexpected end")
            return text.substring(start, pos).toDoubleOrNull() ?: throw ParseException("Bad number '${text.substring(start, pos)}'")
        }
    }
}
