package dev.gaphunter.gitlabcicompanion.validate

/**
 * Pure data, deliberately decoupled from YAML PSI -- every validator in
 * this package is unit-testable against these classes directly, with no
 * platform bootstrap. The PSI-walking that BUILDS this model lives in
 * annotator/GitlabCiAnnotator.kt, kept as thin as possible.
 */

/** A finding, with the source location it should be reported at. */
data class Finding(val jobName: String?, val message: String, val location: SourceRef)

/** Which PSI element (identified by an opaque key the annotator assigns) a finding should be reported against. */
data class SourceRef(val elementKey: String)

data class RuleEntry(
    val hasIf: Boolean,
    val hasWhen: Boolean,
    val unknownKeys: List<String>,
    val location: SourceRef,
)

/**
 * One entry of `needs:` or `dependencies:`. [crossPipeline] marks a need on
 * another pipeline or project (`pipeline:`/`project:` keys), which no local
 * check can reason about; [hasParallel] marks a `parallel:matrix` need.
 */
data class NeedRef(
    val name: String,
    val location: SourceRef,
    val crossPipeline: Boolean = false,
    val hasParallel: Boolean = false,
)

/**
 * For [extends], [needs] and [dependencies]: an empty list means the key is
 * absent (or empty), and **null** means it's present but not statically
 * readable (a `!reference` tag, an unexpected shape) -- every check then
 * skips the job rather than guess.
 */
data class JobDef(
    val name: String,
    val stage: String?,
    val stageLocation: SourceRef?,
    val hasScript: Boolean,
    val hasTrigger: Boolean,
    val hasExtends: Boolean,
    val hasOnly: Boolean,
    val hasExcept: Boolean,
    val rules: List<RuleEntry>,
    val location: SourceRef,
    val extends: List<String>? = emptyList(),
    val extendsLocation: SourceRef? = null,
    val needs: List<NeedRef>? = emptyList(),
    val dependencies: List<NeedRef>? = emptyList(),
    /** A YAML merge key (`<<: *anchor`) can bring in `stage:` and more that this model doesn't see. */
    val hasMergeKey: Boolean = false,
    /** False when `stage:` is present but not a plain name (an alias, a tag, an `$[[ inputs.* ]]` interpolation). */
    val stageReadable: Boolean = true,
) {
    /** Hidden jobs (`.template`) are only ever extended, never run or needed. */
    val isHidden: Boolean get() = name.startsWith(".")
}

/**
 * `stages`/`variables`/`default`/`include`/`workflow`/`image`/`cache`/
 * `before_script`/`after_script`/`stages` are reserved top-level GitLab
 * CI keys, never job names -- excluded before treating a top-level key
 * as a job.
 */
val RESERVED_TOP_LEVEL_KEYS = setOf(
    "stages", "variables", "default", "include", "workflow",
    "image", "cache", "before_script", "after_script", "services", "pages",
    "spec",
)

/** GitLab substitutes `$[[ inputs.* ]]` before validating, so a value containing one can't be judged here. */
fun isInterpolated(value: String): Boolean = "\$[[" in value

/** Keys GitLab CI accepts inside a job's `rules:` entry (`allowed_keys` in lib/gitlab/ci/config/entry/processable.rb). */
val KNOWN_RULES_KEYS = setOf(
    "if", "when", "changes", "exists", "allow_failure", "variables", "start_in", "needs", "interruptible",
)

/**
 * [hasStagesKey] tells "no `stages:`" (GitLab then uses its defaults) from an
 * empty one; [hasInclude] matters because an included file can add jobs,
 * templates and -- when this file declares none -- the stage list itself.
 * [stagesReadable] is false when `stages:` isn't a plain list of names.
 * [mayBeIncluded] marks a file that isn't the project's own top-level
 * `.gitlab-ci.yml` (one under `.gitlab/`, or further down the tree): usually
 * pulled in by an `include:`, so the stage list -- and any job it only
 * partly overrides -- lives in the file that includes it.
 */
data class GitlabCiDocument(
    val declaredStages: List<String>,
    val jobs: List<JobDef>,
    val hasStagesKey: Boolean = declaredStages.isNotEmpty(),
    val hasInclude: Boolean = false,
    val stagesReadable: Boolean = true,
    val mayBeIncluded: Boolean = false,
) {
    /** Jobs defined exactly once. A duplicated name is reported by its own
     * check; every graph check skips it rather than pick a definition. */
    val uniqueJobs: Map<String, JobDef> by lazy {
        jobs.groupBy { it.name }.filterValues { it.size == 1 }.mapValues { it.value.single() }
    }
}
