package dev.gaphunter.gitlabcicompanion.api

import dev.gaphunter.gitlabcicompanion.json.GitlabJsonNode
import dev.gaphunter.gitlabcicompanion.json.GitlabJsonParser
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

class GitlabApiException(message: String) : Exception(message)

data class GitlabPipeline(
    val id: Long,
    val status: String,
    val ref: String,
    val webUrl: String,
    val createdAt: String,
    val name: String?,
)

data class GitlabJob(
    val id: Long,
    val name: String,
    val stage: String,
    val status: String,
)

/**
 * Talks to a real GitLab instance's REST API v4. Extracts ONLY the
 * fields this plugin actually displays, ignoring anything unknown or
 * missing rather than failing -- confirmed necessary, not just tidy,
 * against a real response captured live from gitlab.com/api/v4/projects:
 * a job object carries full nested user/commit objects. The real
 * competitor bug this answers (`Field 'kind' doesn't exist on type
 * 'CiJob'`) comes directly from assuming a fixed, exhaustive shape.
 *
 * v1 scope: GitLab instances at the root of their domain
 * (`https://host/api/v4/...`) only -- self-hosted installs mounted
 * under a subpath are a deliberate cut, not a bug.
 */
class GitlabApiClient(
    instanceUrl: String,
    private val token: String,
    private val httpGet: (url: String) -> String = { url -> defaultGet(url, token) },
) {
    private val apiBase = "${instanceUrl.trimEnd('/')}/api/v4"

    fun listPipelines(projectPath: String): List<GitlabPipeline> {
        val encoded = URLEncoder.encode(projectPath, StandardCharsets.UTF_8)
        return parseArrayOrThrow(httpGet("$apiBase/projects/$encoded/pipelines?per_page=20"))
            .mapNotNull { it.toPipelineOrNull() }
    }

    fun listJobs(projectPath: String, pipelineId: Long): List<GitlabJob> {
        val encoded = URLEncoder.encode(projectPath, StandardCharsets.UTF_8)
        return parseArrayOrThrow(httpGet("$apiBase/projects/$encoded/pipelines/$pipelineId/jobs?per_page=100"))
            .mapNotNull { it.toJobOrNull() }
    }

    private fun parseArrayOrThrow(body: String): List<GitlabJsonNode.Obj> {
        val parsed = try {
            GitlabJsonParser.parse(body)
        } catch (e: Exception) {
            throw GitlabApiException("GitLab returned a response that isn't valid JSON: ${e.message}")
        }
        if (parsed is GitlabJsonNode.Arr) {
            return parsed.items.filterIsInstance<GitlabJsonNode.Obj>()
        }
        val message = ((parsed as? GitlabJsonNode.Obj)?.entries?.get("message") as? GitlabJsonNode.Str)?.value
        throw GitlabApiException(message ?: "Unexpected GitLab API response shape")
    }

    private fun GitlabJsonNode.Obj.toPipelineOrNull(): GitlabPipeline? {
        val id = (entries["id"] as? GitlabJsonNode.Num)?.value?.toLong() ?: return null
        val status = (entries["status"] as? GitlabJsonNode.Str)?.value ?: return null
        return GitlabPipeline(
            id = id,
            status = status,
            ref = (entries["ref"] as? GitlabJsonNode.Str)?.value ?: "",
            webUrl = (entries["web_url"] as? GitlabJsonNode.Str)?.value ?: "",
            createdAt = (entries["created_at"] as? GitlabJsonNode.Str)?.value ?: "",
            name = (entries["name"] as? GitlabJsonNode.Str)?.value,
        )
    }

    private fun GitlabJsonNode.Obj.toJobOrNull(): GitlabJob? {
        val id = (entries["id"] as? GitlabJsonNode.Num)?.value?.toLong() ?: return null
        val name = (entries["name"] as? GitlabJsonNode.Str)?.value ?: return null
        return GitlabJob(
            id = id,
            name = name,
            stage = (entries["stage"] as? GitlabJsonNode.Str)?.value ?: "",
            status = (entries["status"] as? GitlabJsonNode.Str)?.value ?: "",
        )
    }

    companion object {
        private val client = HttpClient.newHttpClient()

        private fun defaultGet(url: String, token: String): String {
            val request = HttpRequest.newBuilder(URI.create(url))
                .header("PRIVATE-TOKEN", token)
                .GET()
                .build()
            return client.send(request, HttpResponse.BodyHandlers.ofString()).body()
        }
    }
}
