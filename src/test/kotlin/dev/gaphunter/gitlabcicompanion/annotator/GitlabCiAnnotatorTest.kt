package dev.gaphunter.gitlabcicompanion.annotator

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * The unattended replacement for a manual runIde smoke pass: confirms the
 * real PSI -> GitlabCiDocument -> validator -> annotation wiring actually
 * connects end-to-end (highlight-companion's own gotcha #2.4 -- a "dead
 * button" wiring bug is invisible to unit tests on the validators alone,
 * only an integration-level check catches it), with a concrete
 * assertion per finding, not just "did it crash."
 */
class GitlabCiAnnotatorTest : BasePlatformTestCase() {

    private fun warningsFor(yaml: String): List<String> {
        myFixture.configureByText(".gitlab-ci.yml", yaml)
        return myFixture.doHighlighting(HighlightSeverity.WARNING)
            .filter { it.severity == HighlightSeverity.WARNING }
            .mapNotNull { it.description }
    }

    fun testValidBaselinePipelineProducesNoWarnings() {
        val warnings = warningsFor(
            """
            stages:
              - build
              - test
              - deploy

            compile:
              stage: build
              script:
                - echo "building"

            run_tests:
              stage: test
              script:
                - echo "testing"
              rules:
                - if: '${'$'}CI_PIPELINE_SOURCE == "merge_request_event"'

            deploy_prod:
              stage: deploy
              trigger:
                project: acme/deploy-project
            """.trimIndent(),
        )
        assertTrue("Expected zero warnings on a valid pipeline, got: $warnings", warnings.isEmpty())
    }

    fun testUndeclaredStageReferenceProducesAWarning() {
        val warnings = warningsFor(
            """
            stages:
              - build
              - test

            deploy_job:
              stage: depoy
              script:
                - echo "deploying"
            """.trimIndent(),
        )
        assertTrue(warnings.any { it.contains("depoy") && it.contains("not declared") })
    }

    fun testJobMissingScriptTriggerExtendsProducesAWarning() {
        val warnings = warningsFor(
            """
            stages:
              - build

            empty_job:
              stage: build
            """.trimIndent(),
        )
        assertTrue(warnings.any { it.contains("empty_job") && it.contains("script") })
    }

    fun testRulesEntryWithUnknownKeyProducesAWarning() {
        val warnings = warningsFor(
            """
            stages:
              - test

            check:
              stage: test
              script:
                - echo "checking"
              rules:
                - condition: true
            """.trimIndent(),
        )
        assertTrue(warnings.any { it.contains("condition") })
    }

    fun testOnlyCombinedWithRulesProducesAWarning() {
        val warnings = warningsFor(
            """
            stages:
              - test

            check:
              stage: test
              script:
                - echo "checking"
              only:
                - main
              rules:
                - if: '${'$'}CI_COMMIT_BRANCH == "main"'
            """.trimIndent(),
        )
        assertTrue(warnings.any { it.contains("only:") && it.contains("rules:") })
    }

    fun testDuplicateJobNameProducesAWarning() {
        val warnings = warningsFor(
            """
            stages:
              - build

            build_job:
              stage: build
              script:
                - echo "first"

            build_job:
              stage: build
              script:
                - echo "second"
            """.trimIndent(),
        )
        assertTrue(warnings.any { it.contains("build_job") && it.contains("more than once") })
    }

    fun testNonGitlabCiYamlFileProducesNoWarningsEvenWithSimilarShape() {
        // Same shape (a "stage"/"script"-looking mapping) but NOT named
        // .gitlab-ci.yml -- must not fire, confirming GitlabCiFileDetector
        // actually gates this, not just "any YAML file gets annotated."
        myFixture.configureByText(
            "docker-compose.yml",
            """
            stages:
              - build

            broken_job:
              stage: nope
            """.trimIndent(),
        )
        val warnings = myFixture.doHighlighting(HighlightSeverity.WARNING).filter { it.severity == HighlightSeverity.WARNING }
        assertTrue("Expected no warnings on a non-.gitlab-ci.yml file, got: ${warnings.map { it.description }}", warnings.isEmpty())
    }
}
