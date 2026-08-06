package dev.gaphunter.gitlabcicompanion.json

sealed class GitlabJsonNode {
    object Null : GitlabJsonNode()
    data class Bool(val value: Boolean) : GitlabJsonNode()
    data class Num(val value: Double) : GitlabJsonNode()
    data class Str(val value: String) : GitlabJsonNode()
    data class Arr(val items: List<GitlabJsonNode>) : GitlabJsonNode()
    data class Obj(val entries: LinkedHashMap<String, GitlabJsonNode>) : GitlabJsonNode()
}

class GitlabJsonException(message: String) : Exception(message)
