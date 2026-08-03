package dev.gaphunter.gitlabcicompanion.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import dev.gaphunter.gitlabcicompanion.detect.GitlabCiFileDetector
import dev.gaphunter.gitlabcicompanion.settings.GitlabCiCompanionSettings
import dev.gaphunter.gitlabcicompanion.settings.GitlabCiRule
import dev.gaphunter.gitlabcicompanion.validate.DuplicateJobNameValidator
import dev.gaphunter.gitlabcicompanion.validate.Finding
import dev.gaphunter.gitlabcicompanion.validate.GitlabCiDocument
import dev.gaphunter.gitlabcicompanion.validate.JobDef
import dev.gaphunter.gitlabcicompanion.validate.KNOWN_RULES_KEYS
import dev.gaphunter.gitlabcicompanion.validate.OnlyExceptRulesConflictValidator
import dev.gaphunter.gitlabcicompanion.validate.RESERVED_TOP_LEVEL_KEYS
import dev.gaphunter.gitlabcicompanion.validate.RuleEntry
import dev.gaphunter.gitlabcicompanion.validate.RulesSyntaxValidator
import dev.gaphunter.gitlabcicompanion.validate.ScriptPresenceValidator
import dev.gaphunter.gitlabcicompanion.validate.SourceRef
import dev.gaphunter.gitlabcicompanion.validate.StageReferenceValidator
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence

/**
 * Fires once per file (on the file's root PSI element, same shape as
 * api-security-companion's OpenApiSpecAnnotator), gated by
 * GitlabCiFileDetector -- never by FileType identity (see
 * ansible-companion's KNOWN_ISSUES.md Round 3 for why that check can
 * silently desync). Deliberately thin: this class only walks PSI to build
 * a GitlabCiDocument and remembers which PsiElement each SourceRef points
 * to; every actual rule lives in validate/, unit-testable without PSI.
 */
class GitlabCiAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val file = element.containingFile as? YAMLFile ?: return
        if (element !== file) return // fire exactly once, on the file root
        if (!GitlabCiFileDetector.isGitlabCiFile(file.virtualFile?.path ?: return)) return

        val settings = GitlabCiCompanionSettings.getInstance()
        val elementsByKey = mutableMapOf<String, PsiElement>()
        val topLevel = file.documents.firstOrNull()?.topLevelValue as? YAMLMapping ?: return

        val doc = buildDocument(topLevel, elementsByKey)

        val findings = mutableListOf<Finding>()
        if (settings.isEnabled(GitlabCiRule.STAGE_REFERENCE)) findings += StageReferenceValidator.validate(doc)
        if (settings.isEnabled(GitlabCiRule.SCRIPT_PRESENCE)) findings += ScriptPresenceValidator.validate(doc)
        if (settings.isEnabled(GitlabCiRule.RULES_SYNTAX)) findings += RulesSyntaxValidator.validate(doc)
        if (settings.isEnabled(GitlabCiRule.ONLY_EXCEPT_RULES_CONFLICT)) findings += OnlyExceptRulesConflictValidator.validate(doc)
        if (settings.isEnabled(GitlabCiRule.DUPLICATE_JOB_NAME)) findings += DuplicateJobNameValidator.validate(doc)

        for (finding in findings) {
            val target = elementsByKey[finding.location.elementKey] ?: continue
            val range: TextRange = target.textRange
            holder.newAnnotation(HighlightSeverity.WARNING, finding.message).range(range).create()
        }
    }

    private fun buildDocument(topLevel: YAMLMapping, elementsByKey: MutableMap<String, PsiElement>): GitlabCiDocument {
        val stagesKv = topLevel.keyValues.firstOrNull { it.keyText == "stages" }
        val declaredStages = stagesKv?.value?.let { readStringSequence(it) } ?: emptyList()

        val jobs = topLevel.keyValues
            .filter { it.keyText !in RESERVED_TOP_LEVEL_KEYS }
            .mapNotNull { kv -> buildJob(kv, elementsByKey) }

        return GitlabCiDocument(declaredStages, jobs)
    }

    private fun buildJob(kv: YAMLKeyValue, elementsByKey: MutableMap<String, PsiElement>): JobDef? {
        val jobMapping = kv.value as? YAMLMapping ?: return null
        val jobKey = remember(elementsByKey, kv)

        val stageKv = jobMapping.keyValues.firstOrNull { it.keyText == "stage" }
        val stageValue = (stageKv?.value as? YAMLScalar)?.textValue
        val stageLocationKey = stageKv?.let { remember(elementsByKey, it) }

        val rules = (jobMapping.keyValues.firstOrNull { it.keyText == "rules" }?.value as? YAMLSequence)
            ?.items
            ?.mapNotNull { item ->
                val ruleMapping = item.value as? YAMLMapping ?: return@mapNotNull null
                val keys = ruleMapping.keyValues.map { it.keyText }
                val ref = remember(elementsByKey, item.value ?: return@mapNotNull null)
                RuleEntry(
                    hasIf = "if" in keys,
                    hasWhen = "when" in keys,
                    unknownKeys = keys.filterNot { it in KNOWN_RULES_KEYS },
                    location = SourceRef(ref),
                )
            }
            ?: emptyList()

        return JobDef(
            name = kv.keyText,
            stage = stageValue,
            stageLocation = stageLocationKey?.let { SourceRef(it) },
            hasScript = jobMapping.keyValues.any { it.keyText == "script" },
            hasTrigger = jobMapping.keyValues.any { it.keyText == "trigger" },
            hasExtends = jobMapping.keyValues.any { it.keyText == "extends" },
            hasOnly = jobMapping.keyValues.any { it.keyText == "only" },
            hasExcept = jobMapping.keyValues.any { it.keyText == "except" },
            rules = rules,
            location = SourceRef(jobKey),
        )
    }

    /** Reads both flow (`[a, b]`) and block (`- a\n- b`) YAML sequence syntax uniformly -- YAMLSequence.items covers both, confirmed against the bundled YAML plugin's real implementation. */
    private fun readStringSequence(value: com.intellij.psi.PsiElement): List<String> =
        (value as? YAMLSequence)?.items?.mapNotNull { (it.value as? YAMLScalar)?.textValue } ?: emptyList()

    private var keyCounter = 0
    private fun remember(map: MutableMap<String, PsiElement>, element: PsiElement): String {
        val key = "k${keyCounter++}"
        map[key] = element
        return key
    }
}
