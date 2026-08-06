package dev.gaphunter.gitlabcicompanion.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitlabRemoteDetectorTest {

    @Test
    fun parsesSshRemote() {
        val remote = GitlabRemoteDetector.parseOriginUrl("git@gitlab.com:group/sub/project.git")
        assertEquals(GitlabRemoteDetector.Remote("gitlab.com", "group/sub/project"), remote)
    }

    @Test
    fun parsesHttpsRemote() {
        val remote = GitlabRemoteDetector.parseOriginUrl("https://gitlab.com/group/project.git")
        assertEquals(GitlabRemoteDetector.Remote("gitlab.com", "group/project"), remote)
    }

    @Test
    fun parsesHttpsRemoteWithoutTrailingGitSuffix() {
        val remote = GitlabRemoteDetector.parseOriginUrl("https://gitlab.example.com/group/project")
        assertEquals(GitlabRemoteDetector.Remote("gitlab.example.com", "group/project"), remote)
    }

    @Test
    fun parsesHttpsRemoteWithEmbeddedCredentials() {
        val remote = GitlabRemoteDetector.parseOriginUrl("https://oauth2:token123@gitlab.com/group/project.git")
        assertEquals(GitlabRemoteDetector.Remote("gitlab.com", "group/project"), remote)
    }

    @Test
    fun unrecognizedUrlReturnsNull() {
        assertNull(GitlabRemoteDetector.parseOriginUrl("not a remote url"))
    }

    @Test
    fun findsOriginUrlAmongMultipleRemotes() {
        val config = """
            [core]
                repositoryformatversion = 0
            [remote "upstream"]
                url = https://gitlab.com/other/project.git
                fetch = +refs/heads/*:refs/remotes/upstream/*
            [remote "origin"]
                url = git@gitlab.com:group/project.git
                fetch = +refs/heads/*:refs/remotes/origin/*
            [branch "main"]
                remote = origin
        """.trimIndent()

        assertEquals("git@gitlab.com:group/project.git", GitlabRemoteDetector.findOriginUrl(config))
    }

    @Test
    fun noOriginRemoteReturnsNull() {
        val config = """
            [core]
                repositoryformatversion = 0
            [remote "upstream"]
                url = https://gitlab.com/other/project.git
        """.trimIndent()

        assertNull(GitlabRemoteDetector.findOriginUrl(config))
    }
}
