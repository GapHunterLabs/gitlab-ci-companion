package dev.gaphunter.gitlabcicompanion.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import dev.gaphunter.gitlabcicompanion.detect.GitlabCiFileDetector
import dev.gaphunter.gitlabcicompanion.settings.GitlabCiCompanionSettings
import dev.gaphunter.gitlabcicompanion.settings.GitlabCiRule
import dev.gaphunter.gitlabcicompanion.validate.DuplicateJobNameValidator
import dev.gaphunter.gitlabcicompanion.validate.ExtendsValidator
import dev.gaphunter.gitlabcicompanion.validate.Finding
import dev.gaphunter.gitlabcicompanion.validate.GitlabCiDocument
import dev.gaphunter.gitlabcicompanion.validate.JobDef
import dev.gaphunter.gitlabcicompanion.validate.KNOWN_RULES_KEYS
import dev.gaphunter.gitlabcicompanion.validate.NeedRef
import dev.gaphunter.gitlabcicompanion.validate.NeedsValidator
import dev.gaphunter.gitlabcicompanion.validate.OnlyExceptRulesConflictValidator
import dev.gaphunter.gitlabcicompanion.validate.RESERVED_TOP_LEVEL_KEYS
import dev.gaphunter.gitlabcicompanion.validate.RuleEntry
import dev.gaphunter.gitlabcicompanion.validate.RulesSyntaxValidator
import dev.gaphunter.gitlabcicompanion.validate.ScriptPresenceValidator
import dev.gaphunter.gitlabcicompanion.validate.SourceRef
import dev.gaphunter.gitlabcicompanion.validate.StageReferenceValidator
import dev.gaphunter.gitlabcicompanion.validate.isInterpolated
import dev.gaphunter.gitlabcicompanion.review.ReviewPrompt
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
        val topLevel = configMapping(file) ?: return

        val doc = buildDocument(topLevel, elementsByKey).copy(mayBeIncluded = mayBeIncluded(file))

        val findings = mutableListOf<Finding>()
        if (settings.isEnabled(GitlabCiRule.STAGE_REFERENCE)) findings += StageReferenceValidator.validate(doc)
        if (settings.isEnabled(GitlabCiRule.SCRIPT_PRESENCE)) findings += ScriptPresenceValidator.validate(doc)
        if (settings.isEnabled(GitlabCiRule.RULES_SYNTAX)) findings += RulesSyntaxValidator.validate(doc)
        if (settings.isEnabled(GitlabCiRule.ONLY_EXCEPT_RULES_CONFLICT)) findings += OnlyExceptRulesConflictValidator.validate(doc)
        if (settings.isEnabled(GitlabCiRule.DUPLICATE_JOB_NAME)) findings += DuplicateJobNameValidator.validate(doc)
        if (settings.isEnabled(GitlabCiRule.EXTENDS_GRAPH)) findings += ExtendsValidator.validate(doc)
        if (settings.isEnabled(GitlabCiRule.NEEDS_ORDER)) findings += NeedsValidator.validate(doc)

        for (finding in findings) {
            val target = elementsByKey[finding.location.elementKey] ?: continue
            val range: TextRange = target.textRange
            holder.newAnnotation(HighlightSeverity.WARNING, finding.message).range(range).create()
            val lineNumber = file.viewProvider.document?.getLineNumber(range.startOffset)?.plus(1) ?: 0
            ReviewPrompt.recordHit(file.project, "${file.virtualFile?.path}:$lineNumber:${finding.message}")
        }
    }

    /**
     * The document GitLab reads as the pipeline, by GitLab's own rule
     * (lib/gitlab/ci/config/yaml/result.rb): with a `spec:` header (CI/CD
     * inputs) the config is the last document, otherwise the first. Empty
     * documents don't count, and more than two is a file GitLab rejects
     * outright, so nothing here is judged.
     */
    private fun configMapping(file: YAMLFile): YAMLMapping? {
        val documents = file.documents.filter { it.topLevelValue != null }
        if (documents.size > 2) return null
        val first = documents.firstOrNull()?.topLevelValue as? YAMLMapping
        val hasHeader = documents.size > 1 && first?.keyValues?.any { it.keyText == "spec" } == true
        return if (hasHeader) documents.last().topLevelValue as? YAMLMapping else first
    }

    /**
     * Only a `.gitlab-ci.yml` at the top of the project is known to be a
     * pipeline's entry point. Any other file this plugin checks (under
     * `.gitlab/`, or a `.gitlab-ci.yml` further down) is usually pulled in by
     * an `include:` from that root file -- found on a corpus of real
     * pipelines, where such files use stages only the root file declares.
     */
    private fun mayBeIncluded(file: YAMLFile): Boolean {
        val virtualFile = file.virtualFile ?: return true
        val parent = virtualFile.parent ?: return true
        if (virtualFile.name != ".gitlab-ci.yml") return true
        val project = file.project
        return parent != ProjectFileIndex.getInstance(project).getContentRootForFile(virtualFile) &&
            parent != project.guessProjectDir()
    }

    private fun buildDocument(topLevel: YAMLMapping, elementsByKey: MutableMap<String, PsiElement>): GitlabCiDocument {
        val stagesKv = topLevel.keyValues.firstOrNull { it.keyText == "stages" }
        val declaredStages = stagesKv?.value?.let { readStringSequence(it) }

        val jobs = topLevel.keyValues
            .filter { it.keyText !in RESERVED_TOP_LEVEL_KEYS }
            .mapNotNull { kv -> buildJob(kv, elementsByKey) }

        return GitlabCiDocument(
            declaredStages = declaredStages.orEmpty(),
            jobs = jobs,
            hasStagesKey = stagesKv != null,
            hasInclude = topLevel.keyValues.any { it.keyText == "include" },
            stagesReadable = stagesKv == null || declaredStages != null,
        )
    }

    /** A plain, untagged, uninterpolated scalar's text; null for anything else (an alias, a `!reference`, `$[[ inputs.x ]]`). */
    private fun plainText(value: PsiElement?): String? =
        (value as? YAMLScalar)?.takeIf { it.tag == null }?.let(::scalarText)?.takeIf { !isInterpolated(it) }

    /**
     * An untagged scalar's value. For an unquoted one, a trailing ` # comment`
     * is cut off: found on a real pipeline, `- docker    ## build ...` came
     * back from the YAML PSI with the comment still attached to the name.
     */
    private fun scalarText(scalar: YAMLScalar): String {
        val quotedOrBlock = scalar.text.firstOrNull() in QUOTE_OR_BLOCK_INDICATORS
        return if (quotedOrBlock) scalar.textValue else scalar.textValue.replace(TRAILING_COMMENT, "")
    }

    /** `extends: tpl` or `extends: [a, b]`; null for anything else (e.g. a `!reference` tag). */
    private fun readExtends(value: PsiElement?): List<String>? = when (value) {
        null -> emptyList()
        is YAMLScalar -> if (value.tag == null) listOf(scalarText(value)) else null
        is YAMLSequence -> value.items.map { item -> (item.value as? YAMLScalar)?.takeIf { it.tag == null }?.let(::scalarText) ?: return null }
        else -> null
    }

    /**
     * `needs:` items are `- job` or `- job: name` (+ artifacts/optional/
     * parallel, or pipeline/project for a cross-pipeline need).
     * `dependencies:` items are plain names. Null when the shape isn't
     * statically readable.
     */
    private fun readNeeds(value: PsiElement?, elementsByKey: MutableMap<String, PsiElement>): List<NeedRef>? {
        if (value == null) return emptyList()
        val sequence = value as? YAMLSequence ?: return null
        return sequence.items.map { item ->
            when (val v = item.value) {
                is YAMLScalar -> if (v.tag != null) return null else NeedRef(scalarText(v), SourceRef(remember(elementsByKey, v)))
                is YAMLMapping -> {
                    val keys = v.keyValues.map { it.keyText }.toSet()
                    val jobKv = v.keyValues.firstOrNull { it.keyText == "job" } ?: return null
                    val name = (jobKv.value as? YAMLScalar)?.takeIf { it.tag == null }?.let(::scalarText) ?: return null
                    NeedRef(
                        name = name,
                        location = SourceRef(remember(elementsByKey, jobKv.value ?: jobKv)),
                        crossPipeline = "pipeline" in keys || "project" in keys,
                        hasParallel = "parallel" in keys,
                    )
                }
                else -> return null
            }
        }
    }

    private fun buildJob(kv: YAMLKeyValue, elementsByKey: MutableMap<String, PsiElement>): JobDef? {
        val jobMapping = kv.value as? YAMLMapping ?: return null
        val jobKey = remember(elementsByKey, kv)

        val stageKv = jobMapping.keyValues.firstOrNull { it.keyText == "stage" }
        val extendsKv = jobMapping.keyValues.firstOrNull { it.keyText == "extends" }
        val stageValue = plainText(stageKv?.value)
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
                    // `<<: *if-protected` is YAML's merge key, not a GitLab key (seen on a real pipeline).
                    unknownKeys = keys.filterNot { it in KNOWN_RULES_KEYS || it == "<<" },
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
            extends = readExtends(extendsKv?.value),
            extendsLocation = extendsKv?.let { SourceRef(remember(elementsByKey, it)) },
            needs = readNeeds(jobMapping.keyValues.firstOrNull { it.keyText == "needs" }?.value, elementsByKey),
            dependencies = readNeeds(jobMapping.keyValues.firstOrNull { it.keyText == "dependencies" }?.value, elementsByKey),
            hasMergeKey = jobMapping.keyValues.any { it.keyText == "<<" },
            stageReadable = stageKv == null || stageValue != null,
        )
    }

    /**
     * Reads both flow (`[a, b]`) and block (`- a\n- b`) YAML sequence syntax uniformly -- YAMLSequence.items covers both, confirmed against the bundled YAML plugin's real implementation.
     * Null unless every item is a plain name: a list built from aliases or inputs can't be known here.
     */
    private fun readStringSequence(value: PsiElement): List<String>? =
        (value as? YAMLSequence)?.items?.map { plainText(it.value) ?: return null }

    private companion object {
        val QUOTE_OR_BLOCK_INDICATORS = setOf('"', '\'', '|', '>')
        val TRAILING_COMMENT = Regex("\\s+#[\\s\\S]*$")
    }

    private var keyCounter = 0
    private fun remember(map: MutableMap<String, PsiElement>, element: PsiElement): String {
        val key = "k${keyCounter++}"
        map[key] = element
        return key
    }
}
