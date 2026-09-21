package dev.gaphunter.gitlabcicompanion.validate

/**
 * Two ways GitLab rejects a pipeline because of `extends:`, with the
 * wording of GitLab's own errors (lib/gitlab/ci/config/extendable/entry.rb):
 * "circular dependency detected in `extends`" and "nesting too deep in
 * `extends`" (MAX_NESTING_LEVELS = 10: more than 10 ancestors along one
 * chain).
 *
 * Only jobs defined in this file are followed. A template from an
 * `include:` ends the chain there, so the depth counted is a lower bound
 * and a cycle is only reported when it is entirely visible -- never a
 * guess.
 */
object ExtendsValidator {
    const val MAX_NESTING_LEVELS = 10

    fun validate(doc: GitlabCiDocument): List<Finding> {
        val jobs = doc.uniqueJobs
        return jobs.values.mapNotNull { job ->
            val location = job.extendsLocation ?: return@mapNotNull null
            if (job.extends.isNullOrEmpty()) return@mapNotNull null
            when {
                isOnCycle(job.name, jobs) -> Finding(
                    job.name,
                    "'${job.name}': circular dependency detected in `extends` -- GitLab rejects this pipeline.",
                    location,
                )
                else -> {
                    val depth = longestChain(job.name, jobs, mutableSetOf())
                    if (depth > MAX_NESTING_LEVELS) Finding(
                        job.name,
                        "'${job.name}': nesting too deep in `extends` -- a chain of $depth levels; GitLab allows at most $MAX_NESTING_LEVELS.",
                        location,
                    ) else null
                }
            }
        }
    }

    private fun parents(name: String, jobs: Map<String, JobDef>): List<String> = jobs[name]?.extends.orEmpty()

    /** True when following local `extends` from [start] leads back to it. */
    private fun isOnCycle(start: String, jobs: Map<String, JobDef>): Boolean {
        val seen = mutableSetOf<String>()
        val stack = ArrayDeque(parents(start, jobs))
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            if (current == start) return true
            if (!seen.add(current)) continue
            stack.addAll(parents(current, jobs))
        }
        return false
    }

    /**
     * Edges on the longest chain from [name] whose both ends are defined in
     * this file; nodes already on the path are not revisited. A parent from
     * an include isn't counted: it might not exist at all, and then GitLab
     * reports "unknown keys in `extends`" instead.
     */
    private fun longestChain(name: String, jobs: Map<String, JobDef>, path: MutableSet<String>): Int {
        if (!path.add(name)) return 0
        val best = parents(name, jobs)
            .filter { it !in path && it in jobs }
            .maxOfOrNull { parent -> 1 + longestChain(parent, jobs, path) }
            ?: 0
        path.remove(name)
        return best
    }
}
