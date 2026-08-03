package dev.gaphunter.gitlabcicompanion.validate

fun job(
    name: String,
    stage: String? = null,
    hasScript: Boolean = true,
    hasTrigger: Boolean = false,
    hasExtends: Boolean = false,
    hasOnly: Boolean = false,
    hasExcept: Boolean = false,
    rules: List<RuleEntry> = emptyList(),
) = JobDef(name, stage, if (stage != null) SourceRef("$name-stage") else null, hasScript, hasTrigger, hasExtends, hasOnly, hasExcept, rules, SourceRef(name))

fun rule(hasIf: Boolean = true, hasWhen: Boolean = false, unknownKeys: List<String> = emptyList(), location: String = "rule") =
    RuleEntry(hasIf, hasWhen, unknownKeys, SourceRef(location))
