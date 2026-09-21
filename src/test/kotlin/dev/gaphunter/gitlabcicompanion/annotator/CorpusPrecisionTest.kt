package dev.gaphunter.gitlabcicompanion.annotator

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/**
 * Precision check on real pipelines, not just hand-written traps: runs every
 * `<corpus>/<n>/.gitlab-ci.yml` through the real annotator and writes every
 * warning, with the file's origin, to build/corpus-findings.txt for manual
 * review. Each file sits where it sat in its repository, so a top-level
 * pipeline and an included fragment are told apart exactly as in a real
 * project. Skipped unless the
 * GITLAB_CI_CORPUS environment variable points at a corpus directory (the
 * corpus itself -- public files from other repositories -- is never
 * committed here).
 */
class CorpusPrecisionTest : BasePlatformTestCase() {

    fun testFindingsOnARealCorpus() {
        val corpus = System.getenv("GITLAB_CI_CORPUS")?.let(::File)?.takeIf { it.isDirectory } ?: return
        val report = StringBuilder()
        var files = 0
        var findings = 0
        for (dir in corpus.listFiles().orEmpty().filter { it.isDirectory }.sortedBy { it.name }) {
            val yml = File(dir, ".gitlab-ci.yml").takeIf { it.isFile } ?: continue
            val source = File(dir, "SOURCE.txt").takeIf { it.isFile }?.readText()?.trim() ?: dir.name
            // SOURCE.txt is owner/repo/path: a repository's own top-level file
            // goes at the project root, anything else keeps its real path.
            val pathInRepo = (source.split('/', limit = 3).getOrNull(2) ?: ".gitlab-ci.yml").replace(Regex("[:*?\"<>|]"), "_")
            val target = if (pathInRepo == ".gitlab-ci.yml") pathInRepo else "c${dir.name}/$pathInRepo"
            val added = myFixture.addFileToProject(target, yml.readText()).virtualFile
            myFixture.configureFromTempProjectFile(target)
            val warnings = myFixture.doHighlighting(HighlightSeverity.WARNING)
                .filter { it.severity == HighlightSeverity.WARNING }
                .mapNotNull { it.description }
            files++
            findings += warnings.size
            warnings.forEach { report.append(dir.name).append(" | ").append(source).append(" | ").append(it).append('\n') }
            // The next top-level file takes this one's place.
            FileEditorManager.getInstance(project).closeFile(added)
            WriteAction.runAndWait<Throwable> { added.delete(this) }
        }
        File("build/corpus-findings.txt").writeText("files=$files findings=$findings\n$report")
        println("corpus: $files files, $findings findings -> build/corpus-findings.txt")
    }
}
