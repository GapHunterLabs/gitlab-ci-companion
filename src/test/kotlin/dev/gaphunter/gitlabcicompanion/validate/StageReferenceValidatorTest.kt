package dev.gaphunter.gitlabcicompanion.validate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StageReferenceValidatorTest {

    private fun flagged(doc: GitlabCiDocument) = StageReferenceValidator.validate(doc).map { it.jobName }.toSet()

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
        assertTrue(findings.first().message.endsWith(
            "deploy_job job: chosen stage depoy does not exist; available stages are .pre, build, test, .post.",
        ))
        assertEquals(SourceRef("deploy_job-stage"), findings.first().location)
    }

    @Test
    fun `no finding when a job has no stage at all`() {
        val doc = GitlabCiDocument(listOf("build"), listOf(job("compile", stage = null)))
        assertTrue(StageReferenceValidator.validate(doc).isEmpty())
    }

    @Test
    fun `pre and post always exist`() {
        val doc = GitlabCiDocument(listOf("build"), listOf(job("prep", stage = ".pre"), job("clean", stage = ".post")))
        assertTrue(StageReferenceValidator.validate(doc).isEmpty())
    }

    @Test
    fun `without a stages list GitLab's defaults apply`() {
        val doc = GitlabCiDocument(
            emptyList(),
            listOf(job("a", stage = "build"), job("b", stage = "test"), job("c", stage = "deploy"), job("d", stage = "lint")),
            hasStagesKey = false,
        )
        assertEquals(setOf("d"), flagged(doc))
    }

    @Test
    fun `an empty stages list is still a list`() {
        val doc = GitlabCiDocument(emptyList(), listOf(job("a", stage = "build")), hasStagesKey = true)
        assertEquals(setOf("a"), flagged(doc))
    }

    @Test
    fun `nothing is judged when the stage list may come from elsewhere`() {
        val jobs = listOf(job("a", stage = "lint"))
        // an include without stages of its own may declare them
        assertTrue(StageReferenceValidator.validate(GitlabCiDocument(emptyList(), jobs, hasStagesKey = false, hasInclude = true)).isEmpty())
        // stages built from aliases or inputs
        assertTrue(StageReferenceValidator.validate(GitlabCiDocument(emptyList(), jobs, hasStagesKey = true, stagesReadable = false)).isEmpty())
        // local stages win over an include's, so they're still checked
        assertEquals(setOf("a"), flagged(GitlabCiDocument(listOf("build"), jobs, hasInclude = true)))
    }

    @Test
    fun `hidden jobs are templates, never run on their own`() {
        val doc = GitlabCiDocument(listOf("build"), listOf(job(".tpl", stage = "lint")))
        assertTrue(StageReferenceValidator.validate(doc).isEmpty())
    }
}
