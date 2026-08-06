package dev.gaphunter.gitlabcicompanion.toolwindow

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.util.Alarm
import dev.gaphunter.gitlabcicompanion.api.GitlabApiClient
import dev.gaphunter.gitlabcicompanion.api.GitlabJob
import dev.gaphunter.gitlabcicompanion.api.GitlabPipeline
import dev.gaphunter.gitlabcicompanion.credentials.GitlabCredentialsStore
import dev.gaphunter.gitlabcicompanion.detect.GitlabRemoteDetector
import dev.gaphunter.gitlabcicompanion.settings.GitlabCiCompanionConfigurable
import dev.gaphunter.gitlabcicompanion.settings.GitlabCiCompanionSettings
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.event.ListSelectionEvent
import javax.swing.table.DefaultTableModel

private const val POLL_INTERVAL_MS = 15_000

/**
 * Opt-in, zero-network-by-default (CONSTITUTION.md 100% local promise
 * of v0.1 stays true for everyone who never configures an instance):
 * this panel only ever calls a real GitLab API once an instance +
 * token are configured in Settings. Polling only runs while the tool
 * window is actually showing, via [Alarm] tied to the ToolWindow's own
 * Disposable -- it stops automatically when the tool window closes.
 */
class GitlabPipelineToolWindow(private val project: Project, private val toolWindow: ToolWindow) {

    val component: JPanel = JPanel(BorderLayout())

    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, toolWindow.disposable)

    private val pipelinesModel = object : DefaultTableModel(arrayOf("Status", "Ref", "Created", "Name"), 0) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val jobsModel = object : DefaultTableModel(arrayOf("Stage", "Job", "Status"), 0) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val pipelinesTable = JTable(pipelinesModel)
    private val jobsTable = JTable(jobsModel)

    private var client: GitlabApiClient? = null
    private var projectPath: String = ""
    private var pipelines: List<GitlabPipeline> = emptyList()

    init {
        rebuild()
    }

    private fun rebuild() {
        component.removeAll()
        val settings = GitlabCiCompanionSettings.getInstance()
        if (settings.getInstanceUrls().isEmpty()) {
            showSetupPrompt("No GitLab instance configured yet.")
            return
        }

        component.add(JLabel("Detecting GitLab remote...", SwingConstants.CENTER), BorderLayout.CENTER)
        ApplicationManager.getApplication().executeOnPooledThread {
            val remote = detectRemote()
            ApplicationManager.getApplication().invokeLater {
                if (remote == null) {
                    showSetupPrompt("Could not detect a GitLab remote in this project's .git/config.")
                    return@invokeLater
                }
                val instanceUrl = settings.getInstanceUrls().firstOrNull { matchesHost(it, remote.host) }
                if (instanceUrl == null) {
                    showSetupPrompt("Remote host \"${remote.host}\" has no matching instance configured.")
                    return@invokeLater
                }
                projectPath = remote.projectPath
                loadTokenAndStart(instanceUrl)
            }
        }
    }

    private fun loadTokenAndStart(instanceUrl: String) {
        component.removeAll()
        component.add(JLabel("Loading credentials...", SwingConstants.CENTER), BorderLayout.CENTER)
        component.revalidate()
        component.repaint()

        // PasswordSafe access is blocking I/O -- off the EDT, same pattern
        // as everywhere else in this catalog. The first poll must never
        // fire before this completes, or it races ahead with no token.
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = runCatching { GitlabCredentialsStore.getToken(instanceUrl) }
            ApplicationManager.getApplication().invokeLater {
                val token = result.getOrNull()
                when {
                    result.isFailure -> showSetupPrompt("Could not load the saved token: ${result.exceptionOrNull()?.message}")
                    token.isNullOrBlank() -> showSetupPrompt("No token saved for $instanceUrl yet.")
                    else -> {
                        client = GitlabApiClient(instanceUrl, token)
                        showTables()
                        schedulePoll()
                    }
                }
            }
        }
    }

    private fun showTables() {
        component.removeAll()
        pipelinesTable.selectionModel.addListSelectionListener { event: ListSelectionEvent ->
            if (!event.valueIsAdjusting) loadJobsForSelectedPipeline()
        }
        pipelinesTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount != 2) return
                val row = pipelinesTable.selectedRow
                if (row < 0 || row >= pipelines.size) return
                val url = pipelines[row].webUrl
                if (url.isNotBlank()) BrowserUtil.browse(url)
            }
        })

        val split = JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            JScrollPane(pipelinesTable),
            JScrollPane(jobsTable),
        )
        split.resizeWeight = 0.6
        component.add(split, BorderLayout.CENTER)
        component.revalidate()
        component.repaint()
        refreshPipelines()
    }

    private fun schedulePoll() {
        alarm.cancelAllRequests()
        alarm.addRequest({
            if (toolWindow.isVisible) refreshPipelines()
            schedulePoll()
        }, POLL_INTERVAL_MS)
    }

    private fun refreshPipelines() {
        val activeClient = client ?: return
        val path = projectPath
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = runCatching { activeClient.listPipelines(path) }
            ApplicationManager.getApplication().invokeLater {
                result.onSuccess { fetched ->
                    pipelines = fetched
                    pipelinesModel.rowCount = 0
                    for (p in fetched) {
                        pipelinesModel.addRow(arrayOf(p.status, p.ref, p.createdAt, p.name ?: ""))
                    }
                }
            }
        }
    }

    private fun loadJobsForSelectedPipeline() {
        val activeClient = client ?: return
        val row = pipelinesTable.selectedRow
        if (row < 0 || row >= pipelines.size) {
            jobsModel.rowCount = 0
            return
        }
        val pipelineId = pipelines[row].id
        val path = projectPath
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = runCatching { activeClient.listJobs(path, pipelineId) }
            ApplicationManager.getApplication().invokeLater {
                result.onSuccess { jobs: List<GitlabJob> ->
                    jobsModel.rowCount = 0
                    for (j in jobs) jobsModel.addRow(arrayOf(j.stage, j.name, j.status))
                }
            }
        }
    }

    private fun showSetupPrompt(message: String) {
        component.removeAll()
        val panel = JPanel(BorderLayout())
        panel.add(JLabel(message, SwingConstants.CENTER), BorderLayout.CENTER)
        val button = JButton("Open GitLab CI Companion Settings")
        button.addActionListener {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, GitlabCiCompanionConfigurable::class.java)
            rebuild()
        }
        panel.add(button, BorderLayout.SOUTH)
        component.add(panel, BorderLayout.CENTER)
        component.revalidate()
        component.repaint()
    }

    private fun detectRemote(): GitlabRemoteDetector.Remote? {
        val basePath = project.basePath ?: return null
        val configPath = Path.of(basePath, ".git", "config")
        if (!Files.isRegularFile(configPath)) return null
        val text = runCatching { Files.readString(configPath) }.getOrNull() ?: return null
        val originUrl = GitlabRemoteDetector.findOriginUrl(text) ?: return null
        return GitlabRemoteDetector.parseOriginUrl(originUrl)
    }

    private fun matchesHost(instanceUrl: String, host: String): Boolean =
        runCatching { URI(instanceUrl).host }.getOrNull().equals(host, ignoreCase = true)
}
