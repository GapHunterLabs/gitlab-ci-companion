package dev.gaphunter.gitlabcicompanion.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/** Every rule on by default, every rule independently disableable -- same discipline as api-security-companion's SecuritySettings. */
@State(name = "GitlabCiCompanionSettings", storages = [Storage("gitlabCiCompanion.xml")])
class GitlabCiCompanionSettings : PersistentStateComponent<GitlabCiCompanionSettings.State> {

    class State {
        var disabledRuleIds: MutableSet<String> = mutableSetOf()
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    fun isEnabled(rule: GitlabCiRule): Boolean = rule.id !in state.disabledRuleIds

    fun setEnabled(rule: GitlabCiRule, enabled: Boolean) {
        if (enabled) state.disabledRuleIds.remove(rule.id) else state.disabledRuleIds.add(rule.id)
    }

    companion object {
        fun getInstance(): GitlabCiCompanionSettings = service()
    }
}
