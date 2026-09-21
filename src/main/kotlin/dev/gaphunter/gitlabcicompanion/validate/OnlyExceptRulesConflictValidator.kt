package dev.gaphunter.gitlabcicompanion.validate

/** GitLab treats only/except and rules: as mutually exclusive on the same job: the linter rejects the
 * configuration with "... config key may not be used with `rules`". (An earlier version of this comment
 * and message said GitLab silently ignores only/except -- it doesn't, it's an error.) */
object OnlyExceptRulesConflictValidator {
    fun validate(doc: GitlabCiDocument): List<Finding> =
        doc.jobs.filter { it.rules.isNotEmpty() && (it.hasOnly || it.hasExcept) }.map { job ->
            val which = listOfNotNull(if (job.hasOnly) "only:" else null, if (job.hasExcept) "except:" else null).joinToString(" and ")
            Finding(job.name, "Job '${job.name}' combines '$which' with 'rules:' -- GitLab rejects this job: 'only'/'except' may not be used with 'rules'.", job.location)
        }
}
