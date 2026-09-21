package dev.gaphunter.gitlabcicompanion.validate

/**
 * A duplicate top-level job key means the second definition silently wins in
 * real YAML -- easy to miss by eye in a long file, cheap to catch statically.
 *
 * A duplicated hidden job that nothing `extends:` is left alone: found on a
 * corpus of real pipelines as a deliberate idiom, the same `.template:` key
 * reused only to hold different YAML anchors (`&rpm_build`, `&deb_build`...),
 * where which definition "wins" never matters.
 */
object DuplicateJobNameValidator {
    fun validate(doc: GitlabCiDocument): List<Finding> {
        val extended = doc.jobs.flatMap { it.extends.orEmpty() }.toSet()
        return doc.jobs.groupBy { it.name }
            .filterValues { it.size > 1 }
            .filterKeys { !it.startsWith(".") || it in extended }
            .flatMap { (name, jobs) ->
                // Report on every occurrence after the first, at each occurrence's own location.
                jobs.drop(1).map { job ->
                    Finding(name, "Job '$name' is defined more than once; the later definition overrides the earlier one.", job.location)
                }
            }
    }
}
