package dev.gaphunter.gitlabcicompanion.validate

/**
 * Two ways GitLab rejects a job because of `needs:`/`dependencies:`, with
 * the wording of GitLab's own errors (lib/gitlab/ci/yaml_processor.rb):
 * - "<job> job: need <other> is not defined in current or prior stages"
 *   (same for `dependencies:`): the needed job's stage comes AFTER this
 *   job's stage;
 * - "<job> has the following needs duplicated: <names>".
 *
 * Fail-closed: a job whose stage can't be known for sure (it comes through
 * an `extends` template from an include, or the stage list itself may come
 * from an include) is skipped, and so is a need on another pipeline or
 * project, on a hidden job, or on a job that isn't in this file.
 */
object NeedsValidator {
    private val DEFAULT_STAGES = listOf("build", "test", "deploy")
    private const val DEFAULT_JOB_STAGE = "test"
    private const val MAX_EXTENDS_WALK = 32

    /** GitLab always runs `.pre` first and `.post` last. Null when not knowable here. */
    fun effectiveStages(doc: GitlabCiDocument): List<String>? {
        if (!doc.stagesReadable || doc.mayBeIncluded) return null
        val middle = when {
            doc.hasStagesKey -> doc.declaredStages
            doc.hasInclude -> return null // the included file may declare the stages
            else -> DEFAULT_STAGES
        }
        return listOf(".pre") + middle.filter { it != ".pre" && it != ".post" } + listOf(".post")
    }

    private sealed interface Stage {
        data class Known(val name: String) : Stage
        object NotSet : Stage
        object Unknown : Stage
    }

    /**
     * A job's own `stage:`, else inherited through `extends` -- where a later
     * parent overrides an earlier one, as GitLab merges them -- else "test".
     * Unknown as soon as an `extends` target isn't in this file.
     */
    private fun stageOf(name: String, jobs: Map<String, JobDef>, depth: Int = 0): Stage {
        if (depth > MAX_EXTENDS_WALK) return Stage.Unknown
        val job = jobs[name] ?: return Stage.Unknown
        if (!job.stageReadable) return Stage.Unknown
        job.stage?.let { return Stage.Known(it) }
        // `<<: *anchor` may carry the stage (an explicit key would have won).
        // Found on a real corpus: 11 of 12 first-run findings were this.
        if (job.hasMergeKey) return Stage.Unknown
        val parents = job.extends ?: return Stage.Unknown
        for (parent in parents.asReversed()) {
            when (val inherited = stageOf(parent, jobs, depth + 1)) {
                is Stage.Known, Stage.Unknown -> return inherited
                Stage.NotSet -> continue
            }
        }
        return Stage.NotSet
    }

    /**
     * With an `include:`, a local job without its own stage may be a partial
     * override of an included job (e.g. adding `needs:` to the
     * `container_scanning` job of a GitLab security template), whose stage
     * comes from the include -- so "no stage" only means "test" when
     * nothing is included.
     */
    private fun resolvedStage(name: String, jobs: Map<String, JobDef>, hasInclude: Boolean): String? =
        when (val stage = stageOf(name, jobs)) {
            is Stage.Known -> stage.name
            Stage.NotSet -> if (hasInclude) null else DEFAULT_JOB_STAGE
            Stage.Unknown -> null
        }

    fun validate(doc: GitlabCiDocument): List<Finding> {
        val jobs = doc.uniqueJobs
        val findings = mutableListOf<Finding>()
        val stages = effectiveStages(doc)

        for (job in jobs.values) {
            if (job.isHidden) continue
            findings += duplicatedNeeds(job)
            if (stages == null) continue
            val jobStage = resolvedStage(job.name, jobs, doc.hasInclude) ?: continue
            val jobIndex = stages.indexOf(jobStage).takeIf { it >= 0 } ?: continue // undeclared stage: its own check
            for ((kind, refs) in listOf("need" to job.needs, "dependency" to job.dependencies)) {
                for (ref in refs.orEmpty()) {
                    if (ref.crossPipeline) continue
                    val target = jobs[ref.name]?.takeIf { !it.isHidden } ?: continue
                    val targetStage = resolvedStage(target.name, jobs, doc.hasInclude) ?: continue
                    val targetIndex = stages.indexOf(targetStage).takeIf { it >= 0 } ?: continue
                    if (targetIndex > jobIndex) {
                        findings += Finding(
                            job.name,
                            "'${job.name}' ${if (kind == "need") "needs" else "depends on"} '${ref.name}', which runs in a " +
                                "later stage ('$targetStage' comes after '$jobStage') -- GitLab rejects this: " +
                                "$kind ${ref.name} is not defined in current or prior stages.",
                            ref.location,
                        )
                    }
                }
            }
        }
        return findings
    }

    private fun duplicatedNeeds(job: JobDef): List<Finding> {
        val plain = job.needs.orEmpty().filter { !it.crossPipeline && !it.hasParallel }
        return plain.groupBy { it.name }.filterValues { it.size > 1 }.map { (name, refs) ->
            Finding(
                job.name,
                "'${job.name}' lists need '$name' ${refs.size} times -- GitLab rejects this: " +
                    "${job.name} has the following needs duplicated: $name.",
                refs[1].location,
            )
        }
    }
}
