package com.example.aiagent

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.wm.ToolWindowManager

abstract class BaseSyntaxAIAction(private val promptPrefix: String) : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selectedText = editor.selectionModel.selectedText ?: return

        // Open the tool window if it's closed
        ToolWindowManager.getInstance(project).getToolWindow("SyntaxAI")?.show()

        val fullPrompt = "$promptPrefix:\n```\n$selectedText\n```"
        MyAIToolWindowFactory.projectHandlers[project]?.invoke(fullPrompt)
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = editor != null && editor.selectionModel.hasSelection()
    }
}

class ExplainCodeAction : BaseSyntaxAIAction("Please explain this code in detail")
class FindBugsAction : BaseSyntaxAIAction("Please find any bugs or security vulnerabilities in this code")
class InlineEditAction : BaseSyntaxAIAction("Please refactor or edit this code (provide the [CODE_START] block for the changes)")
