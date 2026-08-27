package com.example.aiagent

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import java.awt.Graphics
import java.awt.Rectangle
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.example.aiagent.settings.SyntaxAISettingsState
import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicReference
import java.util.Timer
import kotlin.concurrent.schedule

object GhostTextManager {
    var activeInlay: Inlay<*>? = null
    var activeCompletionText: String? = null
    var activeEditor: Editor? = null
    var currentTimer = AtomicReference<java.util.TimerTask?>(null)

    fun clear() {
        ApplicationManager.getApplication().invokeLater {
            activeInlay?.dispose()
            activeInlay = null
            activeCompletionText = null
            activeEditor = null
        }
    }

    fun requestCompletion(editor: Editor, offset: Int) {
        val document = editor.document
        val textBeforeCaret = document.getText(com.intellij.openapi.util.TextRange(maxOf(0, offset - 1000), offset))
        val textAfterCaret = document.getText(com.intellij.openapi.util.TextRange(offset, minOf(document.textLength, offset + 500)))
        
        val settings = SyntaxAISettingsState.instance
        if (!settings.enableGhostText) return

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val uri = java.net.URI(settings.ollamaUrl).toURL()
                val conn = uri.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 15000
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                if (settings.apiProvider == "OpenAI" && settings.apiKey.isNotEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
                }
                
                val prompt = "You are a code completion engine (like Copilot). Provide ONLY the exact code that should be inserted at the cursor position. NO markdown formatting, NO explanations. Just raw code.\n\nCode Before Cursor:\n$textBeforeCaret\n\nCode After Cursor:\n$textAfterCaret"
                
                val reqBody = mapOf(
                    "model" to settings.modelName,
                    "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
                    "stream" to false
                )
                
                conn.outputStream.write(Gson().toJson(reqBody).toByteArray(Charsets.UTF_8))
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                
                val jsonObject = JsonParser.parseString(resp).asJsonObject
                var generated = if (jsonObject.has("choices")) jsonObject.getAsJsonArray("choices").get(0).asJsonObject.getAsJsonObject("message").get("content").asString else jsonObject.getAsJsonObject("message").get("content").asString
                generated = generated.replace(Regex("^```[a-zA-Z]*\\n"), "").replace(Regex("\\n```$"), "").trimEnd()
                
                if (generated.isNotEmpty()) {
                    ApplicationManager.getApplication().invokeLater {
                        if (editor.caretModel.offset == offset && !editor.isDisposed) {
                            showGhostText(editor, offset, generated)
                        }
                    }
                }
            } catch (e: Exception) {}
        }
    }

    private fun showGhostText(editor: Editor, offset: Int, text: String) {
        clear()
        activeEditor = editor
        activeCompletionText = text
        
        val firstLine = text.lines().firstOrNull() ?: return
        
        val renderer = object : EditorCustomElementRenderer {
            override fun calcWidthInPixels(inlay: Inlay<*>): Int {
                val metrics = editor.contentComponent.getFontMetrics(editor.colorsScheme.getFont(com.intellij.openapi.editor.colors.EditorFontType.PLAIN))
                return metrics.stringWidth(firstLine)
            }
            override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
                g.color = JBColor.GRAY
                g.font = editor.colorsScheme.getFont(com.intellij.openapi.editor.colors.EditorFontType.ITALIC)
                g.drawString(firstLine, targetRegion.x, targetRegion.y + editor.ascent)
            }
        }
        
        activeInlay = editor.inlayModel.addInlineElement(offset, true, renderer)
    }

    fun applyCompletion(editor: Editor) {
        val text = activeCompletionText ?: return
        val offset = editor.caretModel.offset
        clear()
        WriteCommandAction.runWriteCommandAction(editor.project) {
            editor.document.insertString(offset, text)
            editor.caretModel.moveToOffset(offset + text.length)
        }
    }
}

class SyntaxAITypedHandler(private val originalHandler: TypedActionHandler) : TypedActionHandler {
    override fun execute(editor: Editor, charTyped: Char, dataContext: DataContext) {
        GhostTextManager.clear()
        originalHandler.execute(editor, charTyped, dataContext)
        
        GhostTextManager.currentTimer.get()?.cancel()
        GhostTextManager.currentTimer.set(Timer().schedule(1200) {
            GhostTextManager.requestCompletion(editor, editor.caretModel.offset)
        })
    }
}

class SyntaxAITabHandler(private val originalHandler: EditorActionHandler) : EditorActionHandler() {
    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        if (GhostTextManager.activeCompletionText != null && GhostTextManager.activeEditor == editor) {
            GhostTextManager.applyCompletion(editor)
        } else {
            originalHandler.execute(editor, caret, dataContext)
        }
    }
}

class SyntaxAIGhostTextStartupActivity : com.intellij.openapi.startup.StartupActivity {
    override fun runActivity(project: com.intellij.openapi.project.Project) {
        val actionManager = EditorActionManager.getInstance()
        
        val typedAction = actionManager.typedAction
        val oldTypedHandler = typedAction.rawHandler
        if (oldTypedHandler !is SyntaxAITypedHandler) {
            typedAction.setupRawHandler(SyntaxAITypedHandler(oldTypedHandler))
        }
        
        val tabAction = actionManager.getActionHandler(com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_TAB)
        if (tabAction !is SyntaxAITabHandler) {
            actionManager.setActionHandler(com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_TAB, SyntaxAITabHandler(tabAction))
        }
    }
}
