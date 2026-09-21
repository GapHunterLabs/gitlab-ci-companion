package dev.gaphunter.gitlabcicompanion.annotator

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/**
 * The pipeline-graph checks through the real annotator, against the demo
 * pipelines read from disk (demo/pipeline-graph*, demo/pipeline-inputs):
 * every FLAGGED line in those files must produce exactly these messages, and
 * every SILENT trap none -- compared against every warning in the file, so a
 * false positive from any other check shows up too.
 */
class PipelineGraphAnnotatorTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = File("demo").absolutePath

    /** Each demo is checked as the project's own top-level `.gitlab-ci.yml`, which is what it stands for. */
    private fun warningsIn(demoDir: String, file: String = ".gitlab-ci.yml"): Set<String> {
        myFixture.copyFileToProject("$demoDir/$file", file)
        myFixture.configureFromTempProjectFile(file)
        return myFixture.doHighlighting(HighlightSeverity.WARNING)
            .filter { it.severity == HighlightSeverity.WARNING }
            .mapNotNull { it.description }
            .toSet()
    }

    fun testFlagsExactlyThePipelinesGitlabWouldReject() {
        assertEquals(
            setOf(
                "'package' needs 'publish', which runs in a later stage ('deploy' comes after 'build') -- GitLab rejects this: need publish is not defined in current or prior stages.",
                "'smoke' depends on 'publish', which runs in a later stage ('deploy' comes after 'build') -- GitLab rejects this: dependency publish is not defined in current or prior stages.",
                "'default-stage-job' needs 'publish', which runs in a later stage ('deploy' comes after 'test') -- GitLab rejects this: need publish is not defined in current or prior stages.",
                "'report' lists need 'compile' 2 times -- GitLab rejects this: report has the following needs duplicated: compile.",
                "'.loop-a': circular dependency detected in `extends` -- GitLab rejects this pipeline.",
                "'.loop-b': circular dependency detected in `extends` -- GitLab rejects this pipeline.",
            ),
            warningsIn("pipeline-graph"),
        )
    }

    fun testAnIncludeWithoutLocalStagesKeepsStagesSilent() {
        assertEquals(emptySet<String>(), warningsIn("pipeline-graph-include"))
    }

    fun testAPartialOverrideOfAnIncludedJobIsNeverJudged() {
        assertEquals(emptySet<String>(), warningsIn("pipeline-graph-include-override"))
    }

    fun testAFileWithInputsIsCheckedOnThePipelineAfterItsHeader() {
        assertEquals(
            setOf(
                "Job 'lint' references stage 'tests', which is not in the pipeline's stages -- GitLab rejects this: " +
                    "lint job: chosen stage tests does not exist; available stages are .pre, build, test, deploy, .post.",
                "'package' needs 'publish', which runs in a later stage ('deploy' comes after 'build') -- GitLab rejects this: need publish is not defined in current or prior stages.",
            ),
            warningsIn("pipeline-inputs"),
        )
    }

    fun testFilesPulledInByAnIncludeAreNotJudgedOnStages() {
        assertEquals(emptySet<String>(), warningsIn("pipeline-fragment", ".gitlab/ci/lint.yml"))
        assertEquals(emptySet<String>(), warningsIn("pipeline-fragment", "services/api/.gitlab-ci.yml"))
    }

    fun testTheSameFragmentAsTheTopLevelFileIsJudged() {
        // Control for the test above: the same content as the project's own
        // .gitlab-ci.yml is checked, so the silence there comes from where
        // the file lives and nothing else.
        myFixture.copyFileToProject("pipeline-fragment/.gitlab/ci/lint.yml", ".gitlab-ci.yml")
        myFixture.configureFromTempProjectFile(".gitlab-ci.yml")
        val warnings = myFixture.doHighlighting(HighlightSeverity.WARNING).mapNotNull { it.description }
        assertTrue(warnings.toString(), warnings.any { "chosen stage lint does not exist" in it })
        assertTrue(warnings.toString(), warnings.any { "'lint:yaml' has none of" in it })
    }
}
