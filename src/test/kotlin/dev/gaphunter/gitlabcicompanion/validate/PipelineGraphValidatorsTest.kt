package dev.gaphunter.gitlabcicompanion.validate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PipelineGraphValidatorsTest {

    private fun def(
        name: String,
        stage: String? = null,
        extends: List<String>? = emptyList(),
        needs: List<String>? = emptyList(),
        dependencies: List<String>? = emptyList(),
        crossPipelineNeeds: List<String> = emptyList(),
    ) = JobDef(
        name, stage, null, hasScript = true, hasTrigger = false, hasExtends = !extends.isNullOrEmpty(),
        hasOnly = false, hasExcept = false, rules = emptyList(), location = SourceRef(name),
        extends = extends, extendsLocation = if (extends.isNullOrEmpty()) null else SourceRef("$name-extends"),
        needs = needs?.map { NeedRef(it, SourceRef("$name-needs-$it")) }?.plus(
            crossPipelineNeeds.map { NeedRef(it, SourceRef("$name-x-$it"), crossPipeline = true) },
        ),
        dependencies = dependencies?.map { NeedRef(it, SourceRef("$name-deps-$it")) },
    )

    private fun doc(vararg jobs: JobDef, stages: List<String>? = listOf("build", "test", "deploy"), include: Boolean = false) =
        GitlabCiDocument(stages.orEmpty(), jobs.toList(), hasStagesKey = stages != null, hasInclude = include)

    private fun jobsIn(findings: List<Finding>) = findings.map { it.jobName }.toSet()

    // --- extends ---------------------------------------------------------------

    @Test
    fun flagsEveryTemplateOnAnExtendsCycle() {
        val findings = ExtendsValidator.validate(doc(def(".a", extends = listOf(".b")), def(".b", extends = listOf(".a")), def("job", extends = listOf(".a"))))
        assertEquals(setOf(".a", ".b"), jobsIn(findings))
        assertTrue(findings.all { "circular dependency detected in `extends`" in it.message })
    }

    @Test
    fun aSelfExtendIsACycle() {
        assertEquals(setOf("x"), jobsIn(ExtendsValidator.validate(doc(def("x", extends = listOf("x"))))))
    }

    @Test
    fun nestingDepthFollowsGitlabsTenLevelLimit() {
        fun chain(links: Int): GitlabCiDocument {
            val jobs = (0..links).map { i -> def("t$i", extends = if (i < links) listOf("t${i + 1}") else emptyList()) }
            return doc(*jobs.toTypedArray())
        }
        assertEquals(emptyList<Finding>(), ExtendsValidator.validate(chain(10)))
        val findings = ExtendsValidator.validate(chain(11))
        assertEquals(setOf("t0"), jobsIn(findings))
        assertTrue(findings.single().message.contains("a chain of 11 levels"))
    }

    @Test
    fun templatesFromElsewhereDontCountTowardsDepth() {
        val jobs = (0..9).map { i -> def("t$i", extends = listOf(if (i < 9) "t${i + 1}" else ".from-include")) }
        assertEquals(emptyList<Finding>(), ExtendsValidator.validate(doc(*jobs.toTypedArray())))
    }

    @Test
    fun duplicatedJobNamesAreLeftToTheirOwnCheck() {
        assertEquals(emptyList<Finding>(), ExtendsValidator.validate(doc(def("x", extends = listOf("x")), def("x"))))
    }

    // --- needs -----------------------------------------------------------------

    @Test
    fun stageListFollowsGitlabDefaults() {
        assertEquals(listOf(".pre", "build", "test", "deploy", ".post"), NeedsValidator.effectiveStages(doc(stages = null)))
        assertEquals(listOf(".pre", "a", "b", ".post"), NeedsValidator.effectiveStages(doc(stages = listOf("a", "b"))))
        assertNull("the include may declare them", NeedsValidator.effectiveStages(doc(stages = null, include = true)))
        assertEquals(listOf(".pre", "a", ".post"), NeedsValidator.effectiveStages(doc(stages = listOf("a"), include = true)))
    }

    @Test
    fun flagsANeedOrDependencyOnALaterStage() {
        val findings = NeedsValidator.validate(doc(
            def("package", stage = "build", needs = listOf("publish")),
            def("smoke", stage = "build", dependencies = listOf("publish")),
            def("publish", stage = "deploy"),
        ))
        assertEquals(setOf("package", "smoke"), jobsIn(findings))
        assertTrue(findings.any { it.message.endsWith("need publish is not defined in current or prior stages.") })
        assertTrue(findings.any { it.message.endsWith("dependency publish is not defined in current or prior stages.") })
        assertEquals(SourceRef("package-needs-publish"), findings.first { it.jobName == "package" }.location)
    }

    @Test
    fun sameOrEarlierStagesAreFine() {
        assertEquals(emptyList<Finding>(), NeedsValidator.validate(doc(
            def("a", stage = "test", needs = listOf("b", "c")),
            def("b", stage = "test"),
            def("c", stage = "build"),
        )))
    }

    @Test
    fun aJobWithoutStageIsInTest() {
        assertEquals(setOf("x"), jobsIn(NeedsValidator.validate(doc(def("x", needs = listOf("d")), def("d", stage = "deploy")))))
    }

    @Test
    fun stageIsInheritedAndTheLastParentWins() {
        val findings = NeedsValidator.validate(doc(
            def(".early", stage = "build"),
            def(".late", stage = "deploy"),
            def("x", extends = listOf(".early", ".late")),
            def("y", stage = "test", needs = listOf("x")),
        ))
        assertEquals(setOf("y"), jobsIn(findings)) // x is in deploy, after y's test
    }

    @Test
    fun anythingItCantKnowIsSkipped() {
        assertEquals(emptyList<Finding>(), NeedsValidator.validate(doc(
            def("from-include", extends = listOf(".not-here"), needs = listOf("d")), // stage unknown
            def("cross", stage = "build", crossPipelineNeeds = listOf("d")),         // other pipeline
            def("hidden-target", stage = "build", needs = listOf(".tpl")),           // hidden job
            def("elsewhere", stage = "build", needs = listOf("not-in-file")),        // not in this file
            def("unreadable", stage = "build", needs = null),                        // e.g. !reference
            def("undeclared", stage = "nope", needs = listOf("d")),                  // its own check
            def(".tpl", stage = "deploy"),
            def("d", stage = "deploy"),
        )))
        // With an include and no local stages:, needs order isn't judged at all.
        assertEquals(emptyList<Finding>(), NeedsValidator.validate(doc(
            def("a", stage = "build", needs = listOf("b")), def("b", stage = "deploy"), stages = null, include = true,
        )))
    }

    @Test
    fun aStageThatMayComeFromAMergeKeyIsUnknown() {
        // Real corpus case: `ubuntu-clang: <<: *build_job` -- the anchor carries the stage.
        val merged = def("image").copy(hasMergeKey = true)
        assertEquals(emptyList<Finding>(), NeedsValidator.validate(doc(merged, def("x", stage = "build", needs = listOf("image")))))
        // An explicit stage still wins over whatever the merge key brings.
        val explicit = def("image", stage = "deploy").copy(hasMergeKey = true)
        assertEquals(setOf("x"), jobsIn(NeedsValidator.validate(doc(explicit, def("x", stage = "build", needs = listOf("image"))))))
    }

    @Test
    fun anUnreadableStageIsUnknown() {
        // `stage: $[[ inputs.stage ]]` or `stage: *anchor`: not "no stage" (which would mean test).
        val interpolated = def("release").copy(stageReadable = false)
        assertEquals(emptyList<Finding>(), NeedsValidator.validate(doc(interpolated, def("x", stage = "build", needs = listOf("release")))))
        assertNull(NeedsValidator.effectiveStages(doc(stages = listOf("build")).copy(stagesReadable = false)))
    }

    @Test
    fun withAnIncludeAJobWithoutStageMayOverrideAnIncludedJob() {
        // Real corpus case: `container_scanning: needs: [build-image]` on top of GitLab's security template.
        assertEquals(emptyList<Finding>(), NeedsValidator.validate(doc(
            def("container_scanning", needs = listOf("build-image")), def("build-image", stage = "build"),
            stages = listOf("test", "security", "build"), include = true,
        )))
    }

    @Test
    fun missingScriptIsNotReportedWhenItMayComeFromElsewhere() {
        val bare = job("bare", hasScript = false)
        assertEquals(setOf("bare"), jobsIn(ScriptPresenceValidator.validate(doc(bare))))
        assertEquals(emptyList<Finding>(), ScriptPresenceValidator.validate(doc(bare.copy(hasMergeKey = true))))
        assertEquals(emptyList<Finding>(), ScriptPresenceValidator.validate(doc(bare, include = true)))
        assertEquals(emptyList<Finding>(), ScriptPresenceValidator.validate(doc(bare).copy(mayBeIncluded = true)))
    }

    @Test
    fun aFileThatMayBeIncludedHasNoKnownStageList() {
        val fragment = doc(def("a", stage = "build", needs = listOf("b")), def("b", stage = "deploy")).copy(mayBeIncluded = true)
        assertNull(NeedsValidator.effectiveStages(fragment))
        assertEquals(emptyList<Finding>(), NeedsValidator.validate(fragment))
        assertEquals(emptyList<Finding>(), StageReferenceValidator.validate(fragment.copy(jobs = listOf(def("c", stage = "lint")))))
        // Checks that only look inside one job still run.
        assertEquals(setOf("r"), jobsIn(NeedsValidator.validate(doc(def("r", needs = listOf("c", "c")), def("c")).copy(mayBeIncluded = true))))
    }

    @Test
    fun aDuplicatedTemplateIsOnlyReportedWhenSomethingExtendsIt() {
        val anchorHolders = doc(def(".tpl"), def(".tpl"), def("job"))
        assertEquals(emptyList<Finding>(), DuplicateJobNameValidator.validate(anchorHolders))
        val extended = doc(def(".tpl"), def(".tpl"), def("job", extends = listOf(".tpl")))
        assertEquals(setOf(".tpl"), jobsIn(DuplicateJobNameValidator.validate(extended)))
        assertEquals(setOf("job"), jobsIn(DuplicateJobNameValidator.validate(doc(def("job"), def("job")))))
    }

    @Test
    fun flagsADuplicatedNeedOnce() {
        val findings = NeedsValidator.validate(doc(def("r", stage = "test", needs = listOf("c", "c")), def("c", stage = "build")))
        assertEquals(1, findings.size)
        assertTrue(findings.single().message.endsWith("r has the following needs duplicated: c."))
        assertEquals(SourceRef("r-needs-c"), findings.single().location)
    }
}
