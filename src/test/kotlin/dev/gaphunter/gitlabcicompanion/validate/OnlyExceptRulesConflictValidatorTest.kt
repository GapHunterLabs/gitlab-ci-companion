package dev.gaphunter.gitlabcicompanion.validate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlyExceptRulesConflictValidatorTest {

    @Test
    fun `no finding when job has only rules, no only-except`() {
        val doc = GitlabCiDocument(emptyList(), listOf(job("deploy", rules = listOf(rule()))))
        assertTrue(OnlyExceptRulesConflictValidator.validate(doc).isEmpty())
    }

    @Test
    fun `no finding when job has only-except but no rules`() {
        val doc = GitlabCiDocument(emptyList(), listOf(job("deploy", hasOnly = true)))
        assertTrue(OnlyExceptRulesConflictValidator.validate(doc).isEmpty())
    }

    @Test
    fun `finding when job combines only with rules`() {
        val doc = GitlabCiDocument(emptyList(), listOf(job("deploy", hasOnly = true, rules = listOf(rule()))))
        val findings = OnlyExceptRulesConflictValidator.validate(doc)
        assertEquals(1, findings.size)
        assertTrue(findings.first().message.contains("only:"))
    }

    @Test
    fun `finding when job combines except with rules`() {
        val doc = GitlabCiDocument(emptyList(), listOf(job("deploy", hasExcept = true, rules = listOf(rule()))))
        val findings = OnlyExceptRulesConflictValidator.validate(doc)
        assertEquals(1, findings.size)
        assertTrue(findings.first().message.contains("except:"))
    }

    @Test
    fun `finding message mentions both when job combines only and except with rules`() {
        val doc = GitlabCiDocument(emptyList(), listOf(job("deploy", hasOnly = true, hasExcept = true, rules = listOf(rule()))))
        val message = OnlyExceptRulesConflictValidator.validate(doc).first().message
        assertTrue(message.contains("only:"))
        assertTrue(message.contains("except:"))
    }
}
