package dev.gaphunter.gitlabcicompanion.validate

/** Flags rules: entries using keys GitLab doesn't recognize -- a real, common typo source (e.g. "condition" instead of "if"). */
object RulesSyntaxValidator {
    fun validate(doc: GitlabCiDocument): List<Finding> =
        doc.jobs.flatMap { job ->
            job.rules.flatMap { rule ->
                rule.unknownKeys.map { key ->
                    Finding(job.name, "Job '${job.name}' has a 'rules:' entry with unrecognized key '$key'.", rule.location)
                }
            }
        }
}
