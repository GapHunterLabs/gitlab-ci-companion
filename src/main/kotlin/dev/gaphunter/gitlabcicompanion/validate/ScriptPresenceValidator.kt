package dev.gaphunter.gitlabcicompanion.validate

/**
 * GitLab requires a job to have one of script/trigger/extends -- a job
 * with none of them fails at pipeline creation time in real GitLab,
 * caught here statically instead.
 *
 * **A hidden job (name starts with `.`) is exempt.** Per GitLab's own
 * documented convention, any top-level key starting with a dot is a
 * "hidden job" that GitLab never processes/runs on its own -- it
 * exists purely as a template other jobs pull in via `extends:`, so it
 * legitimately has none of script/trigger/extends itself. Without this
 * exemption, a perfectly valid `.base_job:` template would be a false
 * positive every time.
 */
object ScriptPresenceValidator {
    fun validate(doc: GitlabCiDocument): List<Finding> =
        doc.jobs
            .filterNot { it.name.startsWith(".") }
            .filterNot { it.hasScript || it.hasTrigger || it.hasExtends }
            .map { job ->
                Finding(job.name, "Job '${job.name}' has none of 'script:', 'trigger:', or 'extends:' -- GitLab requires at least one.", job.location)
            }
}
