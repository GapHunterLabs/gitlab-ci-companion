package dev.gaphunter.gitlabcicompanion.validate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateJobNameValidatorTest {

    @Test
    fun `no finding when all job names are unique`() {
        val doc = GitlabCiDocument(emptyList(), listOf(job("build"), job("test"), job("deploy")))
        assertTrue(DuplicateJobNameValidator.validate(doc).isEmpty())
    }

    @Test
    fun `finding for the second occurrence of a duplicated job name, not the first`() {
        val first = job("build")
        val second = job("build")
        val doc = GitlabCiDocument(emptyList(), listOf(first, second))
        val findings = DuplicateJobNameValidator.validate(doc)
        assertEquals(1, findings.size)
        assertEquals(second.location, findings.first().location)
    }

    @Test
    fun `three occurrences of the same name produce two findings`() {
        val doc = GitlabCiDocument(emptyList(), listOf(job("build"), job("build"), job("build")))
        assertEquals(2, DuplicateJobNameValidator.validate(doc).size)
    }
}
