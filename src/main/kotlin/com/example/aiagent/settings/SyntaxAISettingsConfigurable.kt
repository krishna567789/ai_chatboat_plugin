package com.example.aiagent.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBCheckBox
import com.intellij.openapi.ui.ComboBox
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class SyntaxAISettingsConfigurable : Configurable {

    private var myMainPanel: JPanel? = null
    private val providerComboBox = ComboBox(arrayOf("Ollama", "OpenAI"))
    private val urlField = JBTextField()
    private val modelField = JBTextField()
    private val apiKeyField = JBPasswordField()
    private val enableGhostTextField = JBCheckBox("Enable Ghost Text Completions")

    override fun getDisplayName(): String = "Syntax AI"

    override fun createComponent(): JComponent? {
        myMainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("AI Provider:"), providerComboBox, 1, false)
            .addLabeledComponent(JBLabel("API URL:"), urlField, 1, false)
            .addLabeledComponent(JBLabel("Model Name:"), modelField, 1, false)
            .addLabeledComponent(JBLabel("API Key:"), apiKeyField, 1, false)
            .addComponent(enableGhostTextField)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        return myMainPanel
    }

    override fun isModified(): Boolean {
        val settings = SyntaxAISettingsState.instance
        return urlField.text != settings.ollamaUrl || 
               modelField.text != settings.modelName || 
               providerComboBox.selectedItem != settings.apiProvider || 
               String(apiKeyField.password) != settings.apiKey ||
               enableGhostTextField.isSelected != settings.enableGhostText
    }

    override fun apply() {
        val settings = SyntaxAISettingsState.instance
        settings.ollamaUrl = urlField.text
        settings.modelName = modelField.text
        settings.apiProvider = providerComboBox.selectedItem as String
        settings.apiKey = String(apiKeyField.password)
        settings.enableGhostText = enableGhostTextField.isSelected
    }

    override fun reset() {
        val settings = SyntaxAISettingsState.instance
        urlField.text = settings.ollamaUrl
        modelField.text = settings.modelName
        providerComboBox.selectedItem = settings.apiProvider
        apiKeyField.text = settings.apiKey
        enableGhostTextField.isSelected = settings.enableGhostText
    }

    override fun disposeUIResources() {
        myMainPanel = null
    }
}
