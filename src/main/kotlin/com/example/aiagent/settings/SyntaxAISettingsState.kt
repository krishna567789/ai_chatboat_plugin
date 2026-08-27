package com.example.aiagent.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "com.example.aiagent.settings.SyntaxAISettingsState",
    storages = [Storage("SyntaxAISettings.xml")]
)
class SyntaxAISettingsState : PersistentStateComponent<SyntaxAISettingsState> {

    var ollamaUrl: String = "http://localhost:11434/api/chat"
    var modelName: String = "llama3"
    var apiProvider: String = "Ollama"
    var apiKey: String = ""
    var enableGhostText: Boolean = true

    override fun getState(): SyntaxAISettingsState {
        return this
    }

    override fun loadState(state: SyntaxAISettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        val instance: SyntaxAISettingsState
            get() = ApplicationManager.getApplication().getService(SyntaxAISettingsState::class.java)
    }
}
