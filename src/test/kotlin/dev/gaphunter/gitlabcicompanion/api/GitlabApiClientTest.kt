package dev.gaphunter.gitlabcicompanion.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GitlabApiClientTest {

    private fun client(response: String) =
        GitlabApiClient(instanceUrl = "https://gitlab.example.com", token = "glpat-fake", httpGet = { response })

    // Shape captured live from gitlab.com/api/v4/projects/.../pipelines, plus extra
    // fields (iid, project_id, sha, source, updated_at, coverage) this plugin never
    // reads -- the real bug this guards against is a parser that breaks the moment
    // an API response carries a field nobody asked for.
    @Test
    fun ignoresUnexpectedFieldsInPipelineResponse() {
        val response = """
            [
              {
                "id": 42,
                "iid": 7,
                "project_id": 123,
                "sha": "abc123def456",
                "ref": "main",
                "status": "success",
                "source": "push",
                "created_at": "2026-08-01T10:00:00.000Z",
                "updated_at": "2026-08-01T10:05:00.000Z",
                "web_url": "https://gitlab.example.com/group/project/-/pipelines/42",
                "name": "Deploy pipeline",
                "coverage": null
              }
            ]
        """.trimIndent()

        val pipelines = client(response).listPipelines("group/project")

        assertEquals(1, pipelines.size)
        assertEquals(GitlabPipeline(42, "success", "main", "https://gitlab.example.com/group/project/-/pipelines/42", "2026-08-01T10:00:00.000Z", "Deploy pipeline"), pipelines[0])
    }

    // Shape captured live from gitlab.com/api/v4/.../jobs: each job carries a FULL
    // nested user object and a full nested commit object (with trailers) alongside
    // the handful of fields this plugin actually shows. A second job is missing
    // "stage" entirely (a field this plugin does read) -- both must not throw.
    @Test
    fun parsesJobsWithHugeNestedObjectsAndToleratesAMissingField() {
        val response = """
            [
              {
                "id": 1001,
                "status": "success",
                "stage": "build",
                "name": "compile",
                "ref": "main",
                "created_at": "2026-08-01T10:00:00.000Z",
                "user": {
                  "id": 55,
                  "username": "joel",
                  "name": "Joel Dev",
                  "avatar_url": "https://gitlab.example.com/avatar/55",
                  "web_url": "https://gitlab.example.com/joel"
                },
                "commit": {
                  "id": "abc123def456",
                  "short_id": "abc123d",
                  "title": "Fix build",
                  "author_name": "Joel Dev",
                  "author_email": "joel@example.com",
                  "trailers": {},
                  "extended_trailers": {}
                },
                "pipeline": {
                  "id": 42,
                  "project_id": 123,
                  "status": "success",
                  "ref": "main",
                  "sha": "abc123def456"
                }
              },
              {
                "id": 1002,
                "status": "failed",
                "name": "test"
              }
            ]
        """.trimIndent()

        val jobs = client(response).listJobs("group/project", 42)

        assertEquals(2, jobs.size)
        assertEquals(GitlabJob(1001, "compile", "build", "success"), jobs[0])
        assertEquals(GitlabJob(1002, "test", "", "failed"), jobs[1])
    }

    @Test
    fun throwsWithGitlabsOwnErrorMessageOnApiError() {
        val response = """{"message": "404 Project Not Found"}"""

        val exception = assertThrows(GitlabApiException::class.java) {
            client(response).listPipelines("group/missing")
        }
        assertEquals("404 Project Not Found", exception.message)
    }

    @Test
    fun throwsOnNonJsonResponse() {
        assertThrows(GitlabApiException::class.java) {
            client("<html>not json</html>").listPipelines("group/project")
        }
    }
}
