package dev.gaphunter.gitlabcicompanion.validate

/**
 * Direct fix for the real competitor complaint: a stage referenced but never
 * declared should be caught statically, not discovered as a runtime GitLab
 * API error after a real pipeline run. Checked against the stages GitLab
 * actually offers (`.pre`, the declared list or GitLab's defaults, `.post`),
 * with GitLab's own wording (lib/gitlab/ci/yaml_processor.rb). Skipped when
 * that list can't be known here (it may come from an include), and for
 * hidden jobs, which GitLab never runs on their own.
 */
object StageReferenceValidator {
    fun validate(doc: GitlabCiDocument): List<Finding> {
        val available = NeedsValidator.effectiveStages(doc) ?: return emptyList()
        return doc.jobs.mapNotNull { job ->
            if (job.isHidden) return@mapNotNull null
            val stage = job.stage ?: return@mapNotNull null
            if (stage in available) return@mapNotNull null
            val location = job.stageLocation ?: job.location
            Finding(
                job.name,
                "Job '${job.name}' references stage '$stage', which is not in the pipeline's stages -- GitLab rejects this: " +
                    "${job.name} job: chosen stage $stage does not exist; available stages are ${available.joinToString(", ")}.",
                location,
            )
        }
    }
}
