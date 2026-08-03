package dev.gaphunter.gitlabcicompanion.validate

/** GitLab requires a job to have one of script/trigger/extends -- a job with none of them fails at pipeline creation time in real GitLab, caught here statically instead. */
object ScriptPresenceValidator {
    fun validate(doc: GitlabCiDocument): List<Finding> =
        doc.jobs.filterNot { it.hasScript || it.hasTrigger || it.hasExtends }.map { job ->
            Finding(job.name, "Job '${job.name}' has none of 'script:', 'trigger:', or 'extends:' -- GitLab requires at least one.", job.location)
        }
}
