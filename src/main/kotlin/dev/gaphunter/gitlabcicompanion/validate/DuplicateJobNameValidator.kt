package dev.gaphunter.gitlabcicompanion.validate

/** A duplicate top-level job key means the second definition silently wins in real YAML -- easy to miss by eye in a long file, cheap to catch statically. */
object DuplicateJobNameValidator {
    fun validate(doc: GitlabCiDocument): List<Finding> =
        doc.jobs.groupBy { it.name }
            .filterValues { it.size > 1 }
            .flatMap { (name, jobs) ->
                // Report on every occurrence after the first, at each occurrence's own location.
                jobs.drop(1).map { job ->
                    Finding(name, "Job '$name' is defined more than once; the later definition overrides the earlier one.", job.location)
                }
            }
}
