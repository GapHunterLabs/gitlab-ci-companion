package dev.gaphunter.gitlabcicompanion.validate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesSyntaxValidatorTest {

    @Test
    fun `no finding when rules entry uses only recognized keys`() {
        val doc = GitlabCiDocument(emptyList(), listOf(job("deploy", rules = listOf(rule(unknownKeys = emptyList())))))
        assertTrue(RulesSyntaxValidator.validate(doc).isEmpty())
    }

    @Test
    fun `finding when rules entry uses an unrecognized key`() {
        val doc = GitlabCiDocument(emptyList(), listOf(job("deploy", rules = listOf(rule(unknownKeys = listOf("condition"))))))
        val findings = RulesSyntaxValidator.validate(doc)
        assertEquals(1, findings.size)
        assertTrue(findings.first().message.contains("condition"))
    }

    @Test
    fun `one finding per unknown key across multiple rules entries`() {
        val doc = GitlabCiDocument(
            emptyList(),
            listOf(
                job(
                    "deploy",
                    rules = listOf(
                        rule(unknownKeys = listOf("bad_key1"), location = "r1"),
                        rule(unknownKeys = listOf("bad_key2", "bad_key3"), location = "r2"),
                    ),
                ),
            ),
        )
        assertEquals(3, RulesSyntaxValidator.validate(doc).size)
    }
}
