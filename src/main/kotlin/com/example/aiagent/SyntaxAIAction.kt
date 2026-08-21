package com.example.aiagent

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.wm.ToolWindowManager
import javax.swing.JTextArea

class SyntaxAIAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selectedText = editor.selectionModel.selectedText ?: return

        // Find our tool window
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("SyntaxAI")
        toolWindow?.show {
            // Find the chat area and put the selected code there as a prompt
            val chatArea = toolWindow.contentManager.getContent(0)?.component?.getComponent(0) as? javax.swing.JScrollPane
            val textArea = chatArea?.viewport?.view as? JTextArea
            textArea?.append("\n--- Selected Code ---\n$selectedText\n--------------------\nAI, please explain/fix this code above.\n")
        }
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = editor != null && editor.selectionModel.hasSelection()
    }
}
