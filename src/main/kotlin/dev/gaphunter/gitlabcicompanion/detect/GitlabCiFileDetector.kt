package dev.gaphunter.gitlabcicompanion.detect

/**
 * Deliberately filename/path only, never content-sniffed -- avoids any
 * FileTypeOverrider surface entirely (see ansible-companion's
 * KNOWN_ISSUES.md for the real recursion/desync bugs that mechanism
 * costs). Pure Kotlin, no VirtualFile/PSI dependency, so it's testable
 * without spinning up the platform.
 */
object GitlabCiFileDetector {

    private val EXTRA_CONFIG_DIR = "/.gitlab/"

    fun isGitlabCiFile(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        if (normalized.endsWith("/.gitlab-ci.yml") || normalized == ".gitlab-ci.yml") return true
        if (!normalized.endsWith(".yml") && !normalized.endsWith(".yaml")) return false
        return normalized.contains(EXTRA_CONFIG_DIR)
    }
}
