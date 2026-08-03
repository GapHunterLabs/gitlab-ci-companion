package dev.gaphunter.gitlabcicompanion.validate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StageReferenceValidatorTest {

    @Test
    fun `no finding when a job's stage is declared`() {
        val doc = GitlabCiDocument(listOf("build", "test"), listOf(job("compile", stage = "build")))
        assertTrue(StageReferenceValidator.validate(doc).isEmpty())
    }

    @Test
    fun `finding when a job references an undeclared stage`() {
        val doc = GitlabCiDocument(listOf("build", "test"), listOf(job("deploy_job", stage = "depoy")))
        val findings = StageReferenceValidator.validate(doc)
        assertEquals(1, findings.size)
        assertTrue(findings.first().message.contains("depoy"))
        assertTrue(findings.first().message.contains("not declared"))
    }

    @Test
    fun `no finding when a job has no stage at all`() {
        val doc = GitlabCiDocument(listOf("build"), listOf(job("compile", stage = null)))
        assertTrue(StageReferenceValidator.validate(doc).isEmpty())
    }
}
