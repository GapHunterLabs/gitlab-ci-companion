package dev.gaphunter.gitlabcicompanion.detect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitlabCiFileDetectorTest {

    @Test
    fun `matches a top-level dot-gitlab-ci-yml`() {
        assertTrue(GitlabCiFileDetector.isGitlabCiFile("/home/user/project/.gitlab-ci.yml"))
    }

    @Test
    fun `matches a bare filename with no directory`() {
        assertTrue(GitlabCiFileDetector.isGitlabCiFile(".gitlab-ci.yml"))
    }

    @Test
    fun `matches a yml under a dot-gitlab config directory`() {
        assertTrue(GitlabCiFileDetector.isGitlabCiFile("/home/user/project/.gitlab/ci/build.yml"))
    }

    @Test
    fun `does not match an unrelated yml file`() {
        assertFalse(GitlabCiFileDetector.isGitlabCiFile("/home/user/project/docker-compose.yml"))
    }

    @Test
    fun `does not match a github actions workflow`() {
        assertFalse(GitlabCiFileDetector.isGitlabCiFile("/home/user/project/.github/workflows/ci.yml"))
    }

    @Test
    fun `handles Windows-style backslash paths`() {
        assertTrue(GitlabCiFileDetector.isGitlabCiFile("""C:\project\.gitlab-ci.yml"""))
    }

    @Test
    fun `does not match a non-yaml file even with gitlab-ci in the name`() {
        assertFalse(GitlabCiFileDetector.isGitlabCiFile("/home/user/notes-about-gitlab-ci.txt"))
    }
}
