package dev.gaphunter.gitlabcicompanion.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import dev.gaphunter.gitlabcicompanion.credentials.GitlabCredentialsStore
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.table.DefaultTableModel

class GitlabCiCompanionConfigurable : Configurable {

    private val checkboxes = GitlabCiRule.entries.associateWith { JBCheckBox(it.displayName) }

    // Row = [Instance URL, Token]. The Token column is write-only: it is
    // never pre-filled with a previously saved token (PasswordSafe reads
    // are async and a saved secret shouldn't round-trip back into a
    // visible text field anyway) -- leaving it blank on Apply keeps
    // whatever token that instance already has.
    private val instancesModel = object : DefaultTableModel(arrayOf("Instance URL", "New Token (leave blank to keep existing)"), 0) {
        override fun isCellEditable(row: Int, column: Int) = true
    }
    private var instancesAtOpen: List<String> = emptyList()

    override fun getDisplayName(): String = "GitLab CI Companion"

    override fun createComponent(): JComponent {
        val settings = GitlabCiCompanionSettings.getInstance()

        val rulesPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        for (rule in GitlabCiRule.entries) {
            val checkbox = checkboxes.getValue(rule)
            checkbox.isSelected = settings.isEnabled(rule)
            rulesPanel.add(checkbox)
        }

        loadInstancesIntoModel(settings)

        val instancesTable = JTable(instancesModel)
        val instancesButtons = JPanel().apply {
            add(JButton("Add Instance").apply {
                addActionListener {
                    if (instancesTable.isEditing) instancesTable.cellEditor.stopCellEditing()
                    instancesModel.addRow(arrayOf("https://gitlab.com", ""))
                }
            })
            add(JButton("Remove Selected").apply {
                addActionListener {
                    if (instancesTable.isEditing) instancesTable.cellEditor.stopCellEditing()
                    instancesTable.selectedRows.sortedDescending().forEach { instancesModel.removeRow(it) }
                }
            })
        }

        val instancesPanel = JPanel(BorderLayout()).apply {
            add(JLabel("GitLab instances (pipeline status, Tools window):"), BorderLayout.NORTH)
            add(JScrollPane(instancesTable), BorderLayout.CENTER)
            add(instancesButtons, BorderLayout.SOUTH)
        }

        return JPanel(BorderLayout()).apply {
            add(rulesPanel, BorderLayout.NORTH)
            add(instancesPanel, BorderLayout.CENTER)
        }
    }

    override fun isModified(): Boolean {
        val settings = GitlabCiCompanionSettings.getInstance()
        if (GitlabCiRule.entries.any { checkboxes.getValue(it).isSelected != settings.isEnabled(it) }) return true
        if (currentUrls() != instancesAtOpen) return true
        return (0 until instancesModel.rowCount).any { row -> tokenAt(row).isNotBlank() }
    }

    override fun apply() {
        val settings = GitlabCiCompanionSettings.getInstance()
        for (rule in GitlabCiRule.entries) {
            settings.setEnabled(rule, checkboxes.getValue(rule).isSelected)
        }

        val newUrls = currentUrls()
        for (removedUrl in instancesAtOpen - newUrls.toSet()) {
            GitlabCredentialsStore.removeToken(removedUrl)
        }
        for (row in 0 until instancesModel.rowCount) {
            val token = tokenAt(row)
            if (token.isNotBlank()) GitlabCredentialsStore.setToken(urlAt(row), token)
        }
        settings.setInstanceUrls(newUrls)
        instancesAtOpen = newUrls
        clearTokenColumn()
    }

    override fun reset() {
        val settings = GitlabCiCompanionSettings.getInstance()
        for (rule in GitlabCiRule.entries) {
            checkboxes.getValue(rule).isSelected = settings.isEnabled(rule)
        }
        loadInstancesIntoModel(settings)
    }

    private fun loadInstancesIntoModel(settings: GitlabCiCompanionSettings) {
        instancesModel.rowCount = 0
        for (url in settings.getInstanceUrls()) instancesModel.addRow(arrayOf(url, ""))
        instancesAtOpen = settings.getInstanceUrls()
    }

    private fun urlAt(row: Int): String = (instancesModel.getValueAt(row, 0) as? String)?.trim() ?: ""
    private fun tokenAt(row: Int): String = (instancesModel.getValueAt(row, 1) as? String)?.trim() ?: ""

    private fun currentUrls(): List<String> =
        (0 until instancesModel.rowCount).map { urlAt(it) }.filter { it.isNotBlank() }

    private fun clearTokenColumn() {
        for (row in 0 until instancesModel.rowCount) instancesModel.setValueAt("", row, 1)
    }
}
