package dev.gaphunter.gitlabcicompanion.validate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptPresenceValidatorTest {

    @Test
    fun `no finding when job has script`() {
        val doc = GitlabCiDocument(emptyList(), listOf(job("build_job", hasScript = true)))
        assertTrue(ScriptPresenceValidator.validate(doc).isEmpty())
    }

    @Test
    fun `no finding when job has trigger instead of script`() {
        val doc = GitlabCiDocument(emptyList(), listOf(job("trigger_job", hasScript = false, hasTrigger = true)))
        assertTrue(ScriptPresenceValidator.validate(doc).isEmpty())
    }

    @Test
    fun `no finding when job has extends instead of script`() {
        val doc = GitlabCiDocument(emptyList(), listOf(job("extends_job", hasScript = false, hasExtends = true)))
        assertTrue(ScriptPresenceValidator.validate(doc).isEmpty())
    }

    @Test
    fun `finding when job has none of script, trigger, extends`() {
        val doc = GitlabCiDocument(emptyList(), listOf(job("empty_job", hasScript = false)))
        val findings = ScriptPresenceValidator.validate(doc)
        assertEquals(1, findings.size)
        assertTrue(findings.first().message.contains("empty_job"))
    }

    @Test
    fun `no finding for a hidden dot-prefixed template job, even with none of script, trigger, extends`() {
        val doc = GitlabCiDocument(emptyList(), listOf(job(".base_job", hasScript = false)))
        assertTrue(ScriptPresenceValidator.validate(doc).isEmpty())
    }

    @Test
    fun `a real job is still flagged even when a hidden template job is also present`() {
        val doc = GitlabCiDocument(
            emptyList(),
            listOf(job(".base_job", hasScript = false), job("empty_job", hasScript = false)),
        )
        val findings = ScriptPresenceValidator.validate(doc)
        assertEquals(1, findings.size)
        assertTrue(findings.first().message.contains("empty_job"))
    }
}
