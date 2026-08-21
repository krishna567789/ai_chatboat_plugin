package com.example.aiagent

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.net.HttpURLConnection
import java.net.URL
import javax.swing.*

class MyAIToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        println("🚀 Syntax AI Tool Window is being created!")
        val panel = JPanel(BorderLayout())
        
        val chatArea = JTextArea().apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
        }
        val scrollPane = JScrollPane(chatArea)
        chatArea.append("System: Welcome to Syntax AI Chat Boat!\n")
        
        val inputField = JTextField()
        val sendButton = JButton("Send")
        
        val inputPanel = JPanel(BorderLayout()).apply {
            add(inputField, BorderLayout.CENTER)
            add(sendButton, BorderLayout.EAST)
        }
        
        panel.add(scrollPane, BorderLayout.CENTER)
        panel.add(inputPanel, BorderLayout.SOUTH)
        
        sendButton.addActionListener {
            val text = inputField.text
            if (text.isNotBlank()) {
                chatArea.append("You: $text\n")
                inputField.text = ""
                
                Thread {
                    try {
                        val response = callOllama(text)
                        SwingUtilities.invokeLater {
                            chatArea.append("AI: $response\n\n")
                        }
                    } catch (e: Exception) {
                        SwingUtilities.invokeLater {
                            chatArea.append("Error: ${e.message}\n")
                        }
                    }
                }.start()
            }
        }
        
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    private fun callOllama(prompt: String): String {
        return try {
            val url = URL("http://localhost:11434/api/generate")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            
            val safePrompt = prompt.replace("\"", "\\\"").replace("\n", "\\n")
            val jsonInput = "{\"model\": \"deepseek-coder:1.3b\", \"prompt\": \"$safePrompt\", \"stream\": false}"
            
            conn.outputStream.write(jsonInput.toByteArray())
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            
            val regex = "\"response\":\"(.*?)\"".toRegex()
            val match = regex.find(responseText)
            match?.groups?.get(1)?.value?.replace("\\n", "\n")?.replace("\\\"", "\"") ?: responseText
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
