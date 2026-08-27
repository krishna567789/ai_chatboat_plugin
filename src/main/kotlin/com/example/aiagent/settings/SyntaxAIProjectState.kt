package com.example.aiagent.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "com.example.aiagent.settings.SyntaxAIProjectState",
    storages = [Storage("SyntaxAIChatHistory.xml")]
)
@Service(Service.Level.PROJECT)
class SyntaxAIProjectState : PersistentStateComponent<SyntaxAIProjectState> {

    // Must be public var for XML serialization
    var chatHistory: MutableList<ChatMessageState> = mutableListOf()

    override fun getState(): SyntaxAIProjectState {
        return this
    }

    override fun loadState(state: SyntaxAIProjectState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(project: Project): SyntaxAIProjectState {
            return project.getService(SyntaxAIProjectState::class.java)
        }
    }
}

// Data class specifically for serialization (must have default constructor)
class ChatMessageState {
    var role: String = ""
    var content: String = ""

    constructor() // Required for XML serialization
    
    constructor(role: String, content: String) {
        this.role = role
        this.content = content
    }
}
