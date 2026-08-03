package dev.gaphunter.gitlabcicompanion.settings

enum class GitlabCiRule(val id: String, val displayName: String) {
    STAGE_REFERENCE("stageReference", "Job references a stage not declared in 'stages:'"),
    SCRIPT_PRESENCE("scriptPresence", "Job has none of script:/trigger:/extends:"),
    RULES_SYNTAX("rulesSyntax", "'rules:' entry uses an unrecognized key"),
    ONLY_EXCEPT_RULES_CONFLICT("onlyExceptRulesConflict", "Job combines only:/except: with rules:"),
    DUPLICATE_JOB_NAME("duplicateJobName", "Job name defined more than once"),
}
