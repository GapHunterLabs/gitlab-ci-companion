package dev.gaphunter.gitlabcicompanion.validate

/** Direct fix for the real competitor complaint: a stage referenced but never declared should be caught statically, not discovered as a runtime GitLab API error after a real pipeline run. */
object StageReferenceValidator {
    fun validate(doc: GitlabCiDocument): List<Finding> =
        doc.jobs.mapNotNull { job ->
            val stage = job.stage ?: return@mapNotNull null
            if (stage in doc.declaredStages) return@mapNotNull null
            val location = job.stageLocation ?: job.location
            Finding(job.name, "Job '${job.name}' references stage '$stage', which is not declared in the top-level 'stages:' list.", location)
        }
}
