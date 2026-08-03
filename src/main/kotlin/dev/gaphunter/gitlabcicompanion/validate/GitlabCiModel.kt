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
)

/**
 * `stages`/`variables`/`default`/`include`/`workflow`/`image`/`cache`/
 * `before_script`/`after_script`/`stages` are reserved top-level GitLab
 * CI keys, never job names -- excluded before treating a top-level key
 * as a job.
 */
val RESERVED_TOP_LEVEL_KEYS = setOf(
    "stages", "variables", "default", "include", "workflow",
    "image", "cache", "before_script", "after_script", "services", "pages",
)

/** Keys GitLab CI actually recognizes inside a `rules:` entry, per GitLab's own CI/CD YAML reference. */
val KNOWN_RULES_KEYS = setOf(
    "if", "when", "changes", "exists", "allow_failure", "variables", "start_in", "needs",
)

data class GitlabCiDocument(
    val declaredStages: List<String>,
    val jobs: List<JobDef>,
)
