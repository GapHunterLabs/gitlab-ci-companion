package dev.gaphunter.gitlabcicompanion.validate

/** GitLab treats only/except and rules: as mutually exclusive on the same job -- combining them silently ignores only/except in real GitLab, a confusing surprise caught here instead. */
object OnlyExceptRulesConflictValidator {
    fun validate(doc: GitlabCiDocument): List<Finding> =
        doc.jobs.filter { it.rules.isNotEmpty() && (it.hasOnly || it.hasExcept) }.map { job ->
            val which = listOfNotNull(if (job.hasOnly) "only:" else null, if (job.hasExcept) "except:" else null).joinToString(" and ")
            Finding(job.name, "Job '${job.name}' combines '$which' with 'rules:' -- GitLab ignores 'only'/'except' when 'rules:' is present.", job.location)
        }
}
