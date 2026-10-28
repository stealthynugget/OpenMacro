package dev.openmacro.core.model.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextTest {

    @Test
    fun magicTextSubstitutesKnownTokens() {
        val out = MagicText.render("Battery {battery}% at {time}") { key ->
            when (key) { "battery" -> "80"; "time" -> "09:00"; else -> null }
        }
        assertEquals("Battery 80% at 09:00", out)
    }

    @Test
    fun magicTextLeavesUnknownTokens() {
        val out = MagicText.render("Hello {unknown}") { null }
        assertEquals("Hello {unknown}", out)
    }

    @Test
    fun magicTextLeavesNestedJsonUntouched() {
        // Regression: "}}" used to be collapsed to "}", corrupting nested JSON bodies.
        val json = """{"a":{"b":[10,20,{"c":"deep"}]}}"""
        assertEquals(json, MagicText.render(json) { null })
    }

    @Test
    fun magicTextResolvesTokensInsideJson() {
        val out = MagicText.render("""{"battery":{battery},"nested":{"k":"v"}}""") { if (it == "battery") "80" else null }
        assertEquals("""{"battery":80,"nested":{"k":"v"}}""", out)
    }

    @Test
    fun magicTextKeepsDoubledBracesLiteral() {
        assertEquals("a {{x}} b V", MagicText.render("a {{x}} b {v}") { if (it == "v") "V" else null })
    }

    @Test
    fun expressionEvaluatesArithmetic() {
        assertEquals(7.0, Expression.evaluate("1 + 2 * 3"), 0.0001)
        assertEquals(9.0, Expression.evaluate("(1 + 2) * 3"), 0.0001)
        assertEquals(-5.0, Expression.evaluate("-10 / 2"), 0.0001)
        assertEquals(1.0, Expression.evaluate("10 % 3"), 0.0001)
    }

    @Test
    fun expressionRejectsGarbage() {
        assertNull(Expression.evaluateOrNull("1 +"))
        assertNull(Expression.evaluateOrNull("abc"))
        assertNull(Expression.evaluateOrNull("1 / 0"))
    }
}
