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
 *
 * **Also exempt, both found on a corpus of 250 real pipelines:** a job with
 * a YAML merge key (`<<: *build_job`), whose anchor usually carries the
 * `script:`; and every job of a file with an `include:`, where a job
 * without `script:` is often a partial override of an included job (adding
 * `needs:` to a GitLab template's `container_scanning`, say) -- and, for the
 * same reason, every job of a file that may itself be included.
 */
object ScriptPresenceValidator {
    fun validate(doc: GitlabCiDocument): List<Finding> =
        if (doc.hasInclude || doc.mayBeIncluded) emptyList() else doc.jobs
            .filterNot { it.name.startsWith(".") }
            .filterNot { it.hasMergeKey }
            .filterNot { it.hasScript || it.hasTrigger || it.hasExtends }
            .map { job ->
                Finding(job.name, "Job '${job.name}' has none of 'script:', 'trigger:', or 'extends:' -- GitLab requires at least one.", job.location)
            }
}
