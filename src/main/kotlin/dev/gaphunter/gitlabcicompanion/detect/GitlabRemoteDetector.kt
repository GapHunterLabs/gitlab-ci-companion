package dev.gaphunter.gitlabcicompanion.detect

/**
 * Parses a repo's `.git/config` to find the `origin` remote's host and
 * project path, so the user never types it in by hand. Pure string
 * parsing, no VirtualFile/PSI dependency -- testable standalone, same
 * discipline as GitlabCiFileDetector.
 *
 * Supports both real-world remote URL syntaxes -- SSH and HTTPS are
 * genuinely different grammars, and handling only one would leave
 * roughly half of real users typing the project path in by hand, which
 * is exactly what this exists to avoid. A third, rarer syntax
 * (`ssh://git@host:port/path`, used for non-default SSH ports) is a
 * deliberate cut, not covered here.
 */
object GitlabRemoteDetector {

    data class Remote(val host: String, val projectPath: String)

    // git@gitlab.com:group/sub/project.git
    private val SSH_REMOTE = Regex("""^[\w.-]+@([\w.-]+):(.+?)(\.git)?/?$""")

    // https://gitlab.com/group/sub/project.git (optionally with a user@ prefix)
    private val HTTPS_REMOTE = Regex("""^https?://(?:[^@/]+@)?([\w.-]+)/(.+?)(\.git)?/?$""")

    fun parseOriginUrl(url: String): Remote? {
        val trimmed = url.trim()
        SSH_REMOTE.matchEntire(trimmed)?.let { return Remote(host = it.groupValues[1], projectPath = it.groupValues[2]) }
        HTTPS_REMOTE.matchEntire(trimmed)?.let { return Remote(host = it.groupValues[1], projectPath = it.groupValues[2]) }
        return null
    }

    /** [gitConfigText] is the raw content of a repo's `.git/config` file. */
    fun findOriginUrl(gitConfigText: String): String? {
        var inOriginSection = false
        for (rawLine in gitConfigText.lines()) {
            val line = rawLine.trim()
            if (line.startsWith("[")) {
                inOriginSection = line == "[remote \"origin\"]"
                continue
            }
            if (inOriginSection && line.startsWith("url")) {
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) return parts[1].trim()
            }
        }
        return null
    }
}
