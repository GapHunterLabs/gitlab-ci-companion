package dev.gaphunter.gitlabcicompanion.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

class GitlabCiCompanionConfigurable : Configurable {

    private val checkboxes = GitlabCiRule.entries.associateWith { JBCheckBox(it.displayName) }
    private var panel: JPanel? = null

    override fun getDisplayName(): String = "GitLab CI Companion"

    override fun createComponent(): JComponent {
        val settings = GitlabCiCompanionSettings.getInstance()
        val newPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        for (rule in GitlabCiRule.entries) {
            val checkbox = checkboxes.getValue(rule)
            checkbox.isSelected = settings.isEnabled(rule)
            newPanel.add(checkbox)
        }
        panel = newPanel
        return newPanel
    }

    override fun isModified(): Boolean {
        val settings = GitlabCiCompanionSettings.getInstance()
        return GitlabCiRule.entries.any { checkboxes.getValue(it).isSelected != settings.isEnabled(it) }
    }

    override fun apply() {
        val settings = GitlabCiCompanionSettings.getInstance()
        for (rule in GitlabCiRule.entries) {
            settings.setEnabled(rule, checkboxes.getValue(rule).isSelected)
        }
    }

    override fun reset() {
        val settings = GitlabCiCompanionSettings.getInstance()
        for (rule in GitlabCiRule.entries) {
            checkboxes.getValue(rule).isSelected = settings.isEnabled(rule)
        }
    }
}
