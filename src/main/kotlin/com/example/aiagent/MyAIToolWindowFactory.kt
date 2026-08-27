package com.example.aiagent

import com.intellij.icons.AllIcons
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.geom.RoundRectangle2D
import java.net.HttpURLConnection
import java.net.URL
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.text.RegexOption
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.util.Base64
import java.io.File
import com.example.aiagent.settings.SyntaxAISettingsState
import com.example.aiagent.settings.SyntaxAIProjectState
import com.example.aiagent.settings.ChatMessageState

class MyAIToolWindowFactory : ToolWindowFactory {

    companion object {
        val projectHandlers = java.util.concurrent.ConcurrentHashMap<Project, (String) -> Unit>()
    }

    data class ChatMessage(val role: String, val content: String)
    private val conversationHistory = java.util.Collections.synchronizedList(mutableListOf<ChatMessage>())
    data class FileOperation(val type: String, val path: String, val content: String?)

    @Volatile
    private var activeConnection: HttpURLConnection? = null
    @Volatile
    private var isCancelled = false

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val projectState = SyntaxAIProjectState.getInstance(project)

        val mainPanel = JPanel(BorderLayout())
        mainPanel.background = Color(30, 31, 34)

        val newChatBtn = JButton("➕ New Chat").apply {
            foreground = Color(53, 116, 240)
            isContentAreaFilled = false
            isBorderPainted = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
        val headerPanel = JPanel(BorderLayout()).apply {
            background = Color(43, 45, 48)
            border = EmptyBorder(8, 15, 8, 15)
            val titleLabel = JLabel("Syntax AI").apply {
                foreground = Color.WHITE
                font = Font("SansSerif", Font.BOLD, 14)
            }
            add(titleLabel, BorderLayout.WEST)
            add(newChatBtn, BorderLayout.EAST)
        }
        mainPanel.add(headerPanel, BorderLayout.NORTH)

        val messageContainer = object : JPanel(), javax.swing.Scrollable {
            init {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                background = Color(30, 31, 34)
                border = EmptyBorder(10, 10, 10, 10)
            }
            override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
            override fun getScrollableUnitIncrement(vR: java.awt.Rectangle, o: Int, d: Int): Int = 20
            override fun getScrollableBlockIncrement(vR: java.awt.Rectangle, o: Int, d: Int): Int = 100
            override fun getScrollableTracksViewportWidth(): Boolean = true
            override fun getScrollableTracksViewportHeight(): Boolean = false
        }

        val scrollPane = JBScrollPane(messageContainer)
        scrollPane.border = null

        val inputPanel = object : JPanel(BorderLayout(0, 5)) {
            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = Color(38, 40, 43)
                g2.fillRoundRect(0, 0, width, height, 16, 16)
                g2.color = Color(60, 63, 65)
                g2.drawRoundRect(0, 0, width - 1, height - 1, 16, 16)
                g2.dispose()
                super.paintComponent(g)
            }
        }.apply {
            isOpaque = false
            border = EmptyBorder(10, 15, 10, 15)
        }

        val inputField = JBTextField().apply {
            emptyText.text = "Ask anything, @ to mention, / for actions"
            background = Color(38, 40, 43)
            foreground = Color.WHITE
            border = null
            isOpaque = false
            font = Font("SansSerif", Font.PLAIN, 13)
        }

        val bottomToolbar = JPanel(BorderLayout()).apply { isOpaque = false }
        val leftActions = JPanel(FlowLayout(FlowLayout.LEFT, 10, 0)).apply { isOpaque = false }
        val rightActions = JPanel(FlowLayout(FlowLayout.RIGHT, 10, 0)).apply { isOpaque = false }

        val plusBtn = JLabel("+").apply {
            foreground = Color(160, 160, 160)
            font = Font("SansSerif", Font.BOLD, 14)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
        val modelLabel = JLabel("Syntax AI Pro ^").apply {
            foreground = Color(140, 140, 140)
            font = Font("SansSerif", Font.PLAIN, 11)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
        leftActions.add(plusBtn)
        leftActions.add(modelLabel)

        val uploadBtn = JLabel("📎").apply {
            foreground = Color(160, 160, 160)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "Attach Image for Vision API"
        }
        var selectedImageBase64: String? = null
        uploadBtn.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                val chooser = JFileChooser()
                chooser.fileFilter = FileNameExtensionFilter("Images", "jpg", "jpeg", "png")
                if (chooser.showOpenDialog(mainPanel) == JFileChooser.APPROVE_OPTION) {
                    val file = chooser.selectedFile
                    val bytes = java.nio.file.Files.readAllBytes(file.toPath())
                    selectedImageBase64 = Base64.getEncoder().encodeToString(bytes)
                    uploadBtn.text = "🖼️"
                    uploadBtn.toolTipText = file.name
                }
            }
        })

        val submitBtn = object : JPanel() {
            init {
                isOpaque = false
                preferredSize = Dimension(24, 24)
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            }
            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = Color(60, 63, 65)
                g2.fillOval(0, 0, width, height)
                g2.color = Color.WHITE
                g2.stroke = BasicStroke(1.5f)
                g2.drawLine(6, 12, 16, 12)
                g2.drawLine(12, 7, 17, 12)
                g2.drawLine(12, 17, 17, 12)
                g2.dispose()
                super.paintComponent(g)
            }
        }
        rightActions.add(uploadBtn)
        rightActions.add(submitBtn)

        bottomToolbar.add(leftActions, BorderLayout.WEST)
        bottomToolbar.add(rightActions, BorderLayout.EAST)

        inputPanel.add(inputField, BorderLayout.CENTER)
        inputPanel.add(bottomToolbar, BorderLayout.SOUTH)
        
        val inputContainer = JPanel(BorderLayout()).apply {
            background = Color(30, 31, 34)
            border = EmptyBorder(10, 15, 15, 15)
            add(inputPanel, BorderLayout.CENTER)
        }
        
        mainPanel.add(scrollPane, BorderLayout.CENTER)
        val statusLabel = JLabel("Thinking...", AllIcons.General.ContextHelp, SwingConstants.LEFT).apply {
            foreground = Color(180, 180, 180)
            font = Font("SansSerif", Font.ITALIC, 12)
            border = EmptyBorder(5, 15, 0, 15)
        }
        
        val stopBtn = JButton("Stop ■").apply {
            foreground = Color(244, 67, 54)
            isOpaque = false
            isContentAreaFilled = false
            isBorderPainted = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener {
                isCancelled = true
                activeConnection?.disconnect()
            }
        }
        
        val statusPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            isVisible = false
            add(statusLabel, BorderLayout.WEST)
            add(stopBtn, BorderLayout.EAST)
        }
        
        val inputWrapper = JPanel(BorderLayout())
        inputWrapper.add(statusPanel, BorderLayout.NORTH)
        inputWrapper.add(inputContainer, BorderLayout.CENTER)
        
        val bottomSection = JPanel(BorderLayout())
        bottomSection.add(inputWrapper, BorderLayout.SOUTH)
        mainPanel.add(bottomSection, BorderLayout.SOUTH)

        fun updateState() {
            projectState.chatHistory = conversationHistory.map { ChatMessageState(it.role, it.content) }.toMutableList()
        }

        // Restore history
        conversationHistory.clear()
        projectState.chatHistory.forEach { stateMsg ->
            conversationHistory.add(ChatMessage(stateMsg.role, stateMsg.content))
            if (stateMsg.role == "user" || stateMsg.role == "assistant") {
                val isUser = stateMsg.role == "user"
                // Only show messages that don't look like internal tool calls in the UI
                if (!stateMsg.content.startsWith("TOOL_RESULT:") && !stateMsg.content.contains("<tool_call>")) {
                    val bubble = RoundedMessageBubble(stateMsg.content, isUser)
                    messageContainer.add(bubble)
                    messageContainer.add(Box.createVerticalStrut(15))
                }
            }
        }
        messageContainer.revalidate(); messageContainer.repaint()

        var typingTimer: Timer? = null
        fun updateStatus(text: String?, show: Boolean) {
            SwingUtilities.invokeLater {
                typingTimer?.stop()
                if (text != null) {
                    if (text.startsWith("Thinking")) {
                        var dots = 0
                        typingTimer = Timer(500) {
                            dots = (dots + 1) % 4
                            statusLabel.text = "Thinking" + ".".repeat(dots)
                        }
                        typingTimer?.start()
                    } else {
                        statusLabel.text = text
                    }
                }
                statusPanel.isVisible = show
            }
        }
        
        var welcomePanel: JPanel? = null
        if (conversationHistory.isEmpty()) {
            welcomePanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                border = EmptyBorder(60, 20, 20, 20)
                
                val title = JLabel("✨ Welcome to Syntax AI").apply {
                    font = Font("SansSerif", Font.BOLD, 18)
                    foreground = Color.WHITE
                    alignmentX = Component.CENTER_ALIGNMENT
                }
                add(title)
                add(Box.createVerticalStrut(20))
                
                val subtitle = JLabel("What would you like to do?").apply {
                    font = Font("SansSerif", Font.PLAIN, 12)
                    foreground = Color(150, 150, 150)
                    alignmentX = Component.CENTER_ALIGNMENT
                }
                add(subtitle)
                add(Box.createVerticalStrut(20))
                
                val chipsPanel = JPanel(FlowLayout(FlowLayout.CENTER, 10, 10)).apply { isOpaque = false }
                listOf("🔍 Find Bugs", "📝 Write Tests", "✨ Explain Code").forEach { text ->
                    val btn = JButton(text).apply {
                        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        isContentAreaFilled = false
                        isFocusPainted = false
                        foreground = Color(53, 116, 240)
                        font = Font("SansSerif", Font.PLAIN, 12)
                        border = BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(Color(53, 116, 240), 1, true),
                            EmptyBorder(5, 10, 5, 10)
                        )
                    }
                    btn.addActionListener {
                        inputField.text = text
                    }
                    chipsPanel.add(btn)
                }
                add(chipsPanel)
            }
            messageContainer.add(welcomePanel)
        }
        
        newChatBtn.addActionListener {
            isCancelled = true
            activeConnection?.disconnect()
            conversationHistory.clear()
            projectState.chatHistory.clear()
            messageContainer.removeAll()
            if (welcomePanel != null) {
                messageContainer.add(welcomePanel)
            }
            messageContainer.revalidate()
            messageContainer.repaint()
        }



        fun addMessage(text: String, isUser: Boolean, contextLabel: String? = null) {
            if (welcomePanel != null && messageContainer.isAncestorOf(welcomePanel)) {
                messageContainer.remove(welcomePanel)
            }
            if (contextLabel != null && isUser) {
                val chipPanel = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
                    isOpaque = false
                    val chip = JLabel(contextLabel).apply {
                        font = Font("SansSerif", Font.PLAIN, 10)
                        foreground = Color(160, 160, 160)
                        border = BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(Color(60, 63, 65)),
                            EmptyBorder(2, 6, 2, 6)
                        )
                        isOpaque = true
                        background = Color(43, 45, 48)
                    }
                    add(chip)
                }
                messageContainer.add(chipPanel)
                messageContainer.add(Box.createVerticalStrut(2))
            }
            
            val bubble = RoundedMessageBubble(text, isUser)
            messageContainer.add(bubble)
            messageContainer.add(Box.createVerticalStrut(15))
            messageContainer.revalidate(); messageContainer.repaint()
            
            SwingUtilities.invokeLater { 
                val vsb = scrollPane.verticalScrollBar
                val targetValue = vsb.maximum
                val timer = Timer(15, null)
                timer.addActionListener {
                    if (vsb.value < targetValue) {
                        vsb.value = minOf(vsb.value + 30, targetValue)
                    } else {
                        timer.stop()
                    }
                }
                timer.start()
            }
        }

        fun sendMessage() {
            val query = inputField.text.trim()
            if (query.isEmpty()) return
            
            var activeFileContext = ""
            var displayContext = ""
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            if (editor != null) {
                val file = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getFile(editor.document)
                if (file != null) {
                    val content = editor.document.text
                    val caretModel = editor.caretModel
                    val line = caretModel.logicalPosition.line + 1
                    activeFileContext = "\nCurrently Open File: ${file.path}\nCursor is on line: $line\nFile Content:\n```\n$content\n```\n"
                    displayContext = "📄 ${file.name} : $line"
                }
            }
            
            addMessage(query, true, if (displayContext.isNotEmpty()) displayContext else null)
            inputField.text = ""
            
            conversationHistory.add(ChatMessage("user", query))
            val actionContainerStartIdx = messageContainer.componentCount
            Thread {
                try {
                    var iterations = 0
                    var taskComplete = false
                    val projectDir = project.guessProjectDir()

                    isCancelled = false
                    while (!taskComplete && iterations < 20 && !isCancelled) {
                        iterations++
                        updateStatus("Thinking (Step $iterations)...", true)
                        
                        val projectContext = buildString {
                            append("PROJECT CONTEXT:\n")
                            if (projectDir != null) {
                                val memoryFile = projectDir.findChild(".syntax_ai_memory.md")
                                if (memoryFile != null && !memoryFile.isDirectory) {
                                    append("\nAGENTIC MEMORY (Your Persistent Scratchpad):\n```\n")
                                    append(String(memoryFile.contentsToByteArray()))
                                    append("\n```\n")
                                }
                                
                                val depFiles = listOf("pubspec.yaml", "package.json", "build.gradle.kts", "build.gradle", "pom.xml", "requirements.txt")
                                var projectType = "Unknown"
                                
                                depFiles.forEach { dep ->
                                    val file = projectDir.findChild(dep)
                                    if (file != null && !file.isDirectory) {
                                        if (dep == "pubspec.yaml") projectType = "Flutter/Dart"
                                        if (dep == "package.json") projectType = "Node.js/Web"
                                        if (dep.contains("build.gradle")) projectType = "Gradle/Java/Kotlin"
                                        if (dep == "pom.xml") projectType = "Maven/Java"
                                        if (dep == "requirements.txt") projectType = "Python"
                                        
                                        append("\nDependency File ($dep):\n```\n")
                                        val content = String(file.contentsToByteArray())
                                        append(if (content.length > 2000) content.take(2000) + "\n... (truncated)" else content)
                                        append("\n```\n")
                                    }
                                }
                                
                                append("- Detected Project Type: $projectType\n")
                                
                                append("\nPROJECT BLUEPRINT (Directory Structure):\n")
                                try {
                                    projectDir.children.filter { !it.name.startsWith(".") && it.name != "build" && it.name != "node_modules" }.take(15).forEach { child ->
                                        if (child.isDirectory) {
                                            append("- [DIR] ${child.name}/\n")
                                            child.children.filter { !it.name.startsWith(".") }.take(10).forEach { subChild ->
                                                append("  - ${if (subChild.isDirectory) "[DIR] " else ""}${subChild.name}\n")
                                            }
                                        } else {
                                            append("- [FILE] ${child.name}\n")
                                        }
                                    }
                                } catch (e: Exception) {}
                                append("\n")
                            }
                            if (activeFileContext.isNotEmpty()) {
                                append(activeFileContext)
                            }
                        }

                        val systemPrompt = """
                            You are an Elite, World-Class AI Software Engineer (Senior Staff level) integrated directly into the user's IDE.
                            Your primary goal is to generate ABSOLUTELY PERFECT, PRODUCTION-READY, and STUNNING code.
                            When creating UI, always use premium aesthetics, modern layouts, proper padding, and beautiful color palettes. Avoid plain/generic designs.
                            
                            $projectContext
                            
                            You have four tools available. To use a tool, output exactly this JSON format:
                            <tool_call>{"name": "read_file", "path": "lib/main.dart"}</tool_call>
                            <tool_call>{"name": "list_dir", "path": "lib"}</tool_call>
                            <tool_call>{"name": "search_code", "query": "text to find"}</tool_call>
                            <tool_call>{"name": "run_command", "command": "npm install"}</tool_call>
                            <tool_call>{"name": "write_memory", "content": "project architecture notes..."}</tool_call>
                            <tool_call>{"name": "fetch_url", "url": "https://api.github.com"}</tool_call>
                            
                            IMPORTANT RULES FOR EXECUTION:
                            1. You operate in an autonomous loop of up to 20 iterations.
                            2. You must ALWAYS use tools to research and verify before writing code.
                            3. After outputting a <tool_call>, STOP immediately. Wait for the tool result.
                            4. If a user says "remember this", you MUST use the write_memory tool. For example:
                               <tool_call>{"name": "write_memory", "content": "User prefers snake_case"}</tool_call>
                            
                            CRITICAL RULE FOR CODE CHANGES:
                            When providing code, you MUST NEVER use standard markdown code blocks (like ```dart).
                            You MUST wrap your code in [CODE_START] and [CODE_END] tags.
                            Always prefix your code changes with deep reasoning.
                            
                            FORMAT EXACTLY LIKE THIS:
                            THOUGHT: [Your deep step-by-step reasoning, architectural decisions, and verification plan here...]
                            COMMAND: UPDATE
                            PATH: lib/main.dart
                            [CODE_START]
                            // your complete, production-ready code here
                            [CODE_END]
                            
                            To update or create MULTIPLE files, simply output multiple COMMAND blocks one after another! Do NOT use tools to create files!
                            If you do not use [CODE_START] and [CODE_END], the IDE will not apply the changes. This is absolutely mandatory.
                            Do NOT output multiple tool calls at once. You can output multiple COMMAND blocks at once.
                            Always verify your own work if possible!
                        """.trimIndent()

                        val messagesToSend = mutableListOf<ChatMessage>()
                        messagesToSend.add(ChatMessage("system", systemPrompt))
                        messagesToSend.addAll(conversationHistory)
                        
                        var streamingBubble: RoundedMessageBubble? = null
                        SwingUtilities.invokeLater {
                            streamingBubble = RoundedMessageBubble("", false)
                            messageContainer.add(streamingBubble!!)
                            messageContainer.add(Box.createVerticalStrut(15))
                            messageContainer.revalidate(); messageContainer.repaint()
                        }
                        
                        val currentImage = selectedImageBase64
                        selectedImageBase64 = null
                        SwingUtilities.invokeLater { 
                            uploadBtn.text = "📎"
                            uploadBtn.toolTipText = "Attach Image for Vision API"
                        }
                        
                        val response = callLLM(messagesToSend, currentImage) { chunk ->
                            SwingUtilities.invokeLater {
                                val vsb = scrollPane.verticalScrollBar
                                val isAtBottom = vsb.maximum - (vsb.value + vsb.visibleAmount) < 150
                                streamingBubble?.appendContent(chunk)
                                if (isAtBottom) {
                                    SwingUtilities.invokeLater { vsb.value = vsb.maximum }
                                }
                            }
                        }
                        conversationHistory.add(ChatMessage("assistant", response))
                        updateState()
                        
                        val toolCallMatch = "<tool_call>(.*?)</tool_call>".toRegex(RegexOption.DOT_MATCHES_ALL).find(response)
                        
                        if (toolCallMatch != null) {
                            var jsonString = toolCallMatch.groupValues[1]
                            jsonString = jsonString.replace("```json", "").replace("```", "").trim()
                            updateStatus("Tool Call: $jsonString", true)
                            var toolResult = "Failed to parse tool call."
                            try {
                                val cleanJson = "{" + jsonString.substringAfter("{").substringBeforeLast("}") + "}"
                                val toolObj = JsonParser.parseString(cleanJson).asJsonObject
                                val toolName = toolObj.get("name")?.asString ?: ""
                                
                                if (toolName == "read_file") {
                                    val path = toolObj.get("path")?.asString
                                    if (path != null) {
                                        val file = projectDir?.findFileByRelativePath(path)
                                        toolResult = if (file != null && !file.isDirectory) {
                                            String(file.contentsToByteArray())
                                        } else {
                                            "File not found: $path"
                                        }
                                    }
                                } else if (toolName == "list_dir") {
                                    val path = toolObj.get("path")?.asString
                                    if (path != null) {
                                        val dir = projectDir?.findFileByRelativePath(path) ?: if (path == ".") projectDir else null
                                        toolResult = if (dir != null && dir.isDirectory) {
                                            dir.children.joinToString("\n") { (if (it.isDirectory) "[DIR] " else "[FILE] ") + it.name }
                                        } else {
                                            "Directory not found: $path"
                                        }
                                    }
                                } else if (toolName == "search_code") {
                                    val query = toolObj.get("query")?.asString
                                    if (query != null && projectDir != null) {
                                        val matches = mutableListOf<String>()
                                        val root = java.io.File(projectDir.path)
                                        root.walkTopDown().filter { it.isFile && !it.path.contains("/.git/") && !it.path.contains("/build/") && !it.path.contains("/node_modules/") }.forEach { file ->
                                            try {
                                                file.readLines().forEachIndexed { index, line ->
                                                    if (line.contains(query, ignoreCase = true)) {
                                                        matches.add("${file.relativeTo(root).path}:${index + 1}: ${line.trim()}")
                                                    }
                                                }
                                            } catch (e: Exception) {}
                                        }
                                        toolResult = if (matches.isNotEmpty()) matches.take(50).joinToString("\n") else "No matches found."
                                    }
                                } else if (toolName == "fetch_url") {
                                    val urlStr = toolObj.get("url")?.asString
                                    if (urlStr != null) {
                                        try {
                                            val url = java.net.URL(urlStr)
                                            val connection = url.openConnection() as HttpURLConnection
                                            connection.requestMethod = "GET"
                                            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                                            toolResult = connection.inputStream.bufferedReader().use { it.readText() }.take(5000)
                                        } catch (e: Exception) {
                                            toolResult = "Error fetching URL: ${e.message}"
                                        }
                                    }
                                } else if (toolName == "run_command") {
                                    val cmd = toolObj.get("command")?.asString
                                    if (cmd != null && projectDir != null) {
                                        val process = ProcessBuilder(*cmd.split(" ").toTypedArray())
                                            .directory(java.io.File(projectDir.path))
                                            .redirectErrorStream(true)
                                            .start()
                                        if (process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                                            val outText = process.inputStream.bufferedReader().readText()
                                            toolResult = if (outText.length > 2000) outText.take(2000) + "\n... (truncated)" else outText
                                        } else {
                                            process.destroy()
                                            toolResult = "Command timed out after 10 seconds"
                                        }
                                        if (toolResult.isBlank()) toolResult = "Command executed successfully with no output."
                                    }
                                } else if (toolName == "write_memory") {
                                    val content = toolObj.get("content")?.asString
                                    if (content != null && projectDir != null) {
                                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait {
                                            WriteCommandAction.runWriteCommandAction(project) {
                                                val memFile = projectDir.findChild(".syntax_ai_memory.md") ?: projectDir.createChildData(this, ".syntax_ai_memory.md")
                                                VfsUtil.saveText(memFile, content)
                                            }
                                        }
                                        toolResult = "Memory updated successfully."
                                    }
                                }
                            } catch (e: Exception) {
                                toolResult = "Error executing tool: ${e.message}"
                            }
                            
                            conversationHistory.add(ChatMessage("user", "TOOL_RESULT:\n$toolResult"))
                            updateState()
                            
                            val actionName = if (jsonString.contains("read_file")) "Explored 1 file" 
                                             else if (jsonString.contains("list_dir")) "Analyzed directory"
                                             else if (jsonString.contains("search_code")) "Searched code"
                                             else if (jsonString.contains("run_command")) "Run command ↺"
                                             else if (jsonString.contains("write_memory")) "Wrote to memory 🧠"
                                             else "Used tool"
                            
                            SwingUtilities.invokeLater {
                                val textArea = JTextArea(toolResult).apply {
                                    isEditable = false; isOpaque = false
                                    foreground = Color(140, 140, 140)
                                    font = Font("Monospaced", Font.PLAIN, 11)
                                    border = EmptyBorder(5, 20, 5, 5)
                                    lineWrap = true; wrapStyleWord = true
                                }
                                val actionRow = ActionRowPanel(actionName, textArea)
                                messageContainer.add(actionRow)
                                messageContainer.add(Box.createVerticalStrut(5))
                                messageContainer.revalidate(); messageContainer.repaint()
                            }
                        } else {
                            taskComplete = true
                            SwingUtilities.invokeLater {
                                if (response.contains("THOUGHT:")) {
                                    val thought = response.substringAfter("THOUGHT:").substringBefore("COMMAND:").trim()
                                    updateStatus("Thought: $thought", true)
                                    val textArea = JTextArea(thought).apply {
                                        isEditable = false; isOpaque = false
                                        foreground = Color(140, 140, 140)
                                        font = Font("Monospaced", Font.PLAIN, 11)
                                        border = EmptyBorder(5, 20, 5, 5)
                                        lineWrap = true; wrapStyleWord = true
                                    }
                                    val actionRow = ActionRowPanel("Thought for 1s", textArea)
                                    messageContainer.add(actionRow)
                                    messageContainer.add(Box.createVerticalStrut(5))
                                }
                                
                                val ops = mutableListOf<FileOperation>()
                                
                                val strictPattern = Regex("COMMAND: (CREATE|UPDATE|DELETE)\\s+PATH: (.*?)\\s+\\[CODE_START\\](.*?)\\[CODE_END\\]", RegexOption.DOT_MATCHES_ALL)
                                strictPattern.findAll(response).forEach { match ->
                                    var cleanedContent = match.groupValues[3].trim()
                                    if (cleanedContent.startsWith("```")) {
                                        cleanedContent = cleanedContent.substringAfter("\n")
                                    }
                                    if (cleanedContent.endsWith("```")) {
                                        cleanedContent = cleanedContent.substringBeforeLast("```").trimEnd()
                                    }
                                    ops.add(FileOperation(match.groupValues[1], match.groupValues[2].trim(), cleanedContent))
                                }

                                if (ops.isEmpty()) {
                                    val mdPattern = Regex("```[a-zA-Z]*\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
                                    var counter = 1
                                    mdPattern.findAll(response).forEach { match ->
                                        val beforeText = response.substring(0, match.range.first)
                                        val fileMatch = Regex("(?i)(?:File|PATH):\\s*([\\w./-]+)").findAll(beforeText).lastOrNull() ?: Regex("([a-zA-Z0-9_.-]+\\.(?:dart|kt|java|py|js|ts|css|html))").findAll(beforeText).lastOrNull()
                                        val ext = if (beforeText.lowercase().contains("html")) ".html" else if (beforeText.lowercase().contains("css")) ".css" else ".dart"
                                        val path = fileMatch?.groupValues?.get(1) ?: "lib/generated_snippet_$counter$ext"
                                        
                                        var code = match.groupValues[1].trim()
                                        code = code.replace(Regex("^//\\s*\\[CODE_START\\]\\n?"), "")
                                        code = code.replace(Regex("^\\s*\\[CODE_START\\]\\n?"), "")
                                        code = code.replace(Regex("\\n?//\\s*\\[CODE_END\\]\\s*$"), "")
                                        code = code.replace(Regex("\\n?\\s*\\[CODE_END\\]\\s*$"), "")
                                        
                                        ops.add(FileOperation("UPDATE", path, code.trim()))
                                        counter++
                                    }
                                }

                                SwingUtilities.invokeLater {
                                    if (streamingBubble != null) {
                                        messageContainer.remove(streamingBubble)
                                        // Also try to remove the strut right after it if possible, but it's hard to track. 
                                        // The easiest way is to just remove all components and re-add them? No, just removing streamingBubble is fine.
                                        val components = messageContainer.components
                                        val idx = components.indexOf(streamingBubble)
                                        if (idx != -1) {
                                            messageContainer.remove(idx)
                                            if (idx < messageContainer.componentCount && components[idx + 1] is Box.Filler) {
                                                messageContainer.remove(idx)
                                            }
                                        }
                                    }
                                }

                                if (ops.isNotEmpty()) {
                                    ops.forEach { op ->
                                        val codeBubble = CodeChangeBubble(op, project)
                                        val fileName = op.path.substringAfterLast("/")
                                        val addedLines = (op.content ?: "").lines().size
                                        val actionRow = ActionRowPanel("Edited ◩ $fileName +$addedLines -0", codeBubble, Color(220, 160, 255))
                                        messageContainer.add(actionRow)
                                        messageContainer.add(Box.createVerticalStrut(5))
                                    }
                                    messageContainer.revalidate(); messageContainer.repaint()
                                    val vsb = scrollPane.verticalScrollBar
                                    val isAtBottom = vsb.maximum - (vsb.value + vsb.visibleAmount) < 250
                                    if (isAtBottom) {
                                        SwingUtilities.invokeLater { vsb.value = vsb.maximum }
                                    }
                                }
                                
                                var mainResponse = response
                                mainResponse = mainResponse.replace("COMMAND: (CREATE|UPDATE|DELETE)\\s+PATH: (.*?)\\s+\\[CODE_START\\](.*?)\\[CODE_END\\]".toRegex(RegexOption.DOT_MATCHES_ALL), "")
                                mainResponse = mainResponse.replace("```[a-zA-Z]*\\n(.*?)```".toRegex(RegexOption.DOT_MATCHES_ALL), "")
                                
                                val cleanResponse = mainResponse.replace("(?i)THOUGHT:[\\s\\S]*?(?=\\[CODE_START\\]|```|$)".toRegex(), "").replace("<tool_call>.*?</tool_call>".toRegex(RegexOption.DOT_MATCHES_ALL), "").trim()
                                if (cleanResponse.isNotEmpty()) addMessage(cleanResponse, false)
                                else if (ops.isNotEmpty()) addMessage("I've prepared the changes. Review them above and click Apply.", false)
                            }
                        }
                    }
                    updateStatus(null, false)
                } catch (e: Throwable) {
                    updateStatus(null, false)
                    SwingUtilities.invokeLater { addMessage("Error: ${e.message ?: e.javaClass.name}", false) }
                }
            }.start()
        }

        inputField.addKeyListener(object : KeyAdapter() { override fun keyPressed(e: KeyEvent) { if (e.keyCode == KeyEvent.VK_ENTER) sendMessage() } })

        projectHandlers[project] = { text ->
            SwingUtilities.invokeLater {
                inputField.text = text
                sendMessage()
            }
        }

        val content = ContentFactory.getInstance().createContent(mainPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    private fun callLLM(messages: List<ChatMessage>, imageUrlBase64: String? = null, onChunk: (String) -> Unit): String {
        return try {
            val settings = SyntaxAISettingsState.instance
            val uri = java.net.URI(settings.ollamaUrl)
            val conn = uri.toURL().openConnection() as HttpURLConnection
            activeConnection = conn
            conn.connectTimeout = 10000
            conn.readTimeout = 120000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            
            if (settings.apiProvider == "OpenAI" && settings.apiKey.isNotEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
            }
            
            val gson = Gson()
            val messagesJson = messages.map { msg ->
                if (msg == messages.last() && imageUrlBase64 != null) {
                    mapOf("role" to msg.role, "content" to listOf(
                        mapOf("type" to "text", "text" to msg.content),
                        mapOf("type" to "image_url", "image_url" to mapOf("url" to "data:image/jpeg;base64,$imageUrlBase64"))
                    ))
                } else {
                    mapOf("role" to msg.role, "content" to msg.content)
                }
            }
            val requestBody = mapOf(
                "model" to settings.modelName,
                "messages" to messagesJson,
                "stream" to true
            )
            val jsonInput = gson.toJson(requestBody)
            
            conn.outputStream.write(jsonInput.toByteArray(Charsets.UTF_8))
            
            val responseCode = conn.responseCode
            val isError = responseCode >= 400
            val inputStream = if (isError) conn.errorStream else conn.inputStream
            
            if (inputStream == null) {
                return "Error: HTTP $responseCode"
            }
            
            val fullResponse = StringBuilder()
            val reader = inputStream.bufferedReader()
            var line = reader.readLine()
            
            if (isError) {
                while (line != null) {
                    fullResponse.append(line).append("\n")
                    line = reader.readLine()
                }
                val errText = fullResponse.toString()
                return try {
                    val errJson = JsonParser.parseString(errText).asJsonObject
                    if (errJson.has("error") && errJson.getAsJsonObject("error").has("message")) {
                        "API Error: ${errJson.getAsJsonObject("error").get("message").asString}"
                    } else {
                        "API Error ($responseCode): $errText"
                    }
                } catch (e: Exception) {
                    "API Error ($responseCode): $errText"
                }
            }
            
            while (line != null && !isCancelled) {
                if (line.isNotBlank()) {
                    var chunkText = ""
                    try {
                        if (line.startsWith("data: ")) {
                            val data = line.removePrefix("data: ").trim()
                            if (data != "[DONE]") {
                                val json = JsonParser.parseString(data).asJsonObject
                                if (json.has("choices")) {
                                    val choices = json.getAsJsonArray("choices")
                                    if (choices.size() > 0) {
                                        val delta = choices.get(0).asJsonObject.getAsJsonObject("delta")
                                        if (delta.has("content")) chunkText = delta.get("content").asString
                                    }
                                }
                            }
                        } else {
                            val json = JsonParser.parseString(line).asJsonObject
                            if (json.has("message")) {
                                chunkText = json.getAsJsonObject("message").get("content").asString
                            }
                        }
                    } catch (e: Exception) {}
                    
                    if (chunkText.isNotEmpty()) {
                        fullResponse.append(chunkText)
                        onChunk(chunkText)
                    }
                }
                line = reader.readLine()
            }
            fullResponse.toString()
        } catch (e: Throwable) { "Error: ${e.message ?: e.javaClass.name}" }
    }
}

class RoundedMessageBubble(text: String, private val isUser: Boolean) : JPanel() {
    private var editorPane: JEditorPane? = null
    private val rawText = StringBuilder()

    private fun updateEditorPane() {
        if (editorPane == null) {
            editorPane = JEditorPane().apply {
                contentType = "text/html"
                putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
                font = Font("SansSerif", Font.PLAIN, 13)
                isEditable = false
                isOpaque = false
                
                // CRITICAL FIX: Prevent the JEditorPane from auto-scrolling to the top when text is replaced
                val caret = this.caret as? javax.swing.text.DefaultCaret
                if (caret != null) {
                    caret.updatePolicy = javax.swing.text.DefaultCaret.NEVER_UPDATE
                }
            }
        }
        
        val parser = Parser.builder().build()
        
        var cleanText = rawText.toString()
        if (!isUser) {
            cleanText = "<tool_call>[\\s\\S]*?(?:</tool_call>|$)".toRegex().replace(cleanText, "🛠️ *Using internal tools...*")
            cleanText = "(?i)THOUGHT:[\\s\\S]*?(?=\\[CODE_START\\]|```|$)".toRegex().replace(cleanText, "✨ *Preparing code changes...*\n\n")
            cleanText = "\\[CODE_START\\][\\s\\S]*?(?:\\[CODE_END\\]|$)".toRegex().replace(cleanText, "")
            cleanText = "```[a-zA-Z]*\\n[\\s\\S]*?(?:```|$)".toRegex().replace(cleanText, "") // Strip any rogue markdown blocks so they don't render twice
        }
        
        val document = parser.parse(cleanText)
        val renderer = HtmlRenderer.builder().build()
        val htmlText = renderer.render(document)
        
        val styles = "<style>code { background-color: #3C3F41; padding: 2px 4px; border-radius: 3px; font-family: monospace; font-size: 12px; } pre { background-color: #2B2D30; padding: 8px; border-radius: 5px; }</style>"
        editorPane!!.text = "<html><head>$styles</head><body style='color: #E0E0E0; margin: 0;'>$htmlText</body></html>"
    }

    fun appendContent(chunk: String) {
        rawText.append(chunk)
        updateEditorPane()
        revalidate()
        repaint()
    }

    fun setContent(fullText: String) {
        rawText.clear()
        rawText.append(fullText)
        updateEditorPane()
        revalidate()
        repaint()
    }

    init {
        isOpaque = false
        layout = BorderLayout()
        
        val contentPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = EmptyBorder(8, 12, 8, 12)
        }
        
        val bubbleColor = if (isUser) Color(53, 116, 240) else Color(67, 73, 78)
        
        val wrapper = object : JPanel(BorderLayout()) {
            init {
                isOpaque = false
                add(contentPanel, BorderLayout.CENTER)
            }
            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = bubbleColor
                g2.fillRoundRect(0, 0, width, height, 16, 16)
                g2.dispose()
                super.paintComponent(g)
            }
        }

        if (isUser) {
            val textArea = JTextArea(text).apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
                isOpaque = false
                foreground = Color.WHITE
                font = Font("SansSerif", Font.PLAIN, 13)
            }
            
            val sizingWrapper = object: JPanel(BorderLayout()) {
                init {
                    isOpaque = false
                    add(textArea, BorderLayout.CENTER)
                }
                override fun getPreferredSize(): Dimension {
                    val scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, this)
                    val maxW = if (scrollPane != null) (scrollPane.width * 0.85).toInt() else 280
                    val pref = super.getPreferredSize()
                    if (pref.width > maxW) {
                        textArea.size = Dimension(maxW, Short.MAX_VALUE.toInt())
                        return Dimension(maxW, textArea.preferredSize.height)
                    }
                    return pref
                }
            }
            contentPanel.add(sizingWrapper, BorderLayout.CENTER)
            
            val alignPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                isOpaque = false
                border = EmptyBorder(5, 40, 5, 10)
                add(wrapper)
            }
            add(alignPanel, BorderLayout.CENTER)
        } else {
            rawText.append(text)
            updateEditorPane()
            
            val sizingWrapper = object: JPanel(BorderLayout()) {
                init {
                    isOpaque = false
                    add(editorPane!!, BorderLayout.CENTER)
                }
                override fun getPreferredSize(): Dimension {
                    val scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, this)
                    val maxW = if (scrollPane != null) (scrollPane.width * 0.85).toInt() else 280
                    val pref = super.getPreferredSize()
                    if (pref.width > maxW) {
                        editorPane!!.size = Dimension(maxW, Short.MAX_VALUE.toInt())
                        return Dimension(maxW, editorPane!!.preferredSize.height)
                    }
                    return pref
                }
            }
            contentPanel.add(sizingWrapper, BorderLayout.CENTER)

            val alignPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                isOpaque = false
                border = EmptyBorder(5, 10, 5, 40)
                add(wrapper)
            }
            add(alignPanel, BorderLayout.CENTER)
        }
    }
}

class ActionRowPanel(
    private val title: String, 
    private val expandableContent: JComponent? = null,
    private val titleColor: Color = Color(200, 200, 200)
) : JPanel() {

    private val toggleLabel = JLabel(if (expandableContent != null) "$title  >" else title).apply {
        foreground = titleColor
        font = Font("SansSerif", Font.PLAIN, 13)
        if (expandableContent != null) cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        border = EmptyBorder(8, 12, 8, 12)
    }

    private val contentWrapper = JPanel(BorderLayout()).apply {
        isOpaque = false
        isVisible = false
        if (expandableContent != null) add(expandableContent, BorderLayout.CENTER)
    }

    init {
        isOpaque = false
        layout = BorderLayout()
        
        val headerPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(toggleLabel, BorderLayout.WEST)
        }

        val innerWrapper = object : JPanel(BorderLayout()) {
            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                // Minimal styling for action rows (like Antigravity)
                g2.color = Color(38, 40, 43)
                g2.fillRoundRect(0, 0, width, height, 12, 12)
                g2.dispose()
                super.paintComponent(g)
            }
        }.apply {
            isOpaque = false
            add(headerPanel, BorderLayout.NORTH)
            add(contentWrapper, BorderLayout.CENTER)
        }

        if (expandableContent != null) {
            toggleLabel.addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                    contentWrapper.isVisible = !contentWrapper.isVisible
                    toggleLabel.text = if (contentWrapper.isVisible) "$title  v" else "$title  >"
                    toggleLabel.foreground = if (contentWrapper.isVisible) Color.WHITE else titleColor
                    revalidate()
                }
                override fun mouseEntered(e: java.awt.event.MouseEvent?) {
                    toggleLabel.foreground = Color.WHITE
                }
                override fun mouseExited(e: java.awt.event.MouseEvent?) {
                    if (!contentWrapper.isVisible) toggleLabel.foreground = titleColor
                }
            })
        }
        
        val alignPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            border = EmptyBorder(4, 40, 4, 10)
            add(innerWrapper)
        }
        add(alignPanel, BorderLayout.CENTER)
    }
}


class CodeChangeBubble(private val op: MyAIToolWindowFactory.FileOperation, private val project: Project) : JPanel() {
    init {
        isOpaque = false
        layout = BorderLayout()
        border = EmptyBorder(5, 5, 5, 5)

        var oldContent = ""
        val root = project.guessProjectDir()
        val file = root?.findFileByRelativePath(op.path)
        if (file != null && op.type != "CREATE") {
            try {
                oldContent = String(file.contentsToByteArray())
            } catch (e: Exception) {}
        }
        
        val oldLines = oldContent.lines().size
        val newLines = (op.content ?: "").lines().size
        // Rough heuristic for diff
        val added = maxOf(0, newLines - oldLines) + (if (op.type == "CREATE") newLines else (newLines / 10))
        val removed = maxOf(0, oldLines - newLines) + (if (op.type == "DELETE") oldLines else (oldLines / 10))

        val mainContainer = object : JPanel(BorderLayout()) {
            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = Color(30, 31, 34)
                g2.fillRoundRect(0, 0, width, height, 12, 12)
                g2.color = Color(60, 63, 65)
                g2.drawRoundRect(0, 0, width - 1, height - 1, 12, 12)
                g2.dispose()
                super.paintComponent(g)
            }
        }.apply {
            isOpaque = false
            border = EmptyBorder(10, 12, 10, 12)
        }

        val topRow = JPanel(BorderLayout()).apply { isOpaque = false }
        val infoPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply { isOpaque = false }
        
        val fileCountLabel = JLabel("1 file changed").apply {
            foreground = Color(180, 180, 180)
            font = Font("SansSerif", Font.PLAIN, 12)
        }
        val addedLabel = JLabel("+$added").apply {
            foreground = Color(98, 175, 100)
            font = Font("SansSerif", Font.PLAIN, 12)
        }
        val removedLabel = JLabel("-$removed").apply {
            foreground = Color(244, 103, 94)
            font = Font("SansSerif", Font.PLAIN, 12)
        }
        
        infoPanel.add(fileCountLabel)
        infoPanel.add(addedLabel)
        infoPanel.add(removedLabel)

        val reviewBtn = JButton("Review").apply {
            foreground = Color(180, 180, 180)
            background = Color(60, 63, 65)
            font = Font("SansSerif", Font.PLAIN, 12)
            isOpaque = true
            isContentAreaFilled = true
            isBorderPainted = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color(60, 63, 65)),
                EmptyBorder(4, 10, 4, 10)
            )
            addActionListener {
                try {
                    val diffContentFactory = com.intellij.diff.DiffContentFactory.getInstance()
                    val request = com.intellij.diff.requests.SimpleDiffRequest(
                        "Review Changes: ${op.path}",
                        diffContentFactory.create(project, oldContent),
                        diffContentFactory.create(project, op.content ?: ""),
                        "Current Code",
                        "AI Suggested Code"
                    )
                    com.intellij.diff.DiffManager.getInstance().showDiff(project, request)
                } catch (e: Throwable) {}
            }
        }
        
        topRow.add(infoPanel, BorderLayout.WEST)
        topRow.add(reviewBtn, BorderLayout.EAST)
        
        val filesPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = EmptyBorder(12, 0, 12, 0)
        }
        
        val fileBox = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false }
        val iconLabel = JLabel(AllIcons.FileTypes.Text) // simple icon
        val fileNameLabel = JLabel(op.path.substringAfterLast("/")).apply {
            foreground = Color(200, 200, 200)
            font = Font("SansSerif", Font.BOLD, 12)
        }
        val pathLabel = JLabel(op.path).apply {
            foreground = Color(120, 120, 120)
            font = Font("SansSerif", Font.PLAIN, 11)
        }
        
        fileBox.add(iconLabel)
        fileBox.add(fileNameLabel)
        fileBox.add(pathLabel)
        filesPanel.add(fileBox, BorderLayout.WEST)
        
        val bottomRow = JPanel(FlowLayout(FlowLayout.RIGHT, 10, 0)).apply { isOpaque = false }
        
        val rejectBtn = JButton("Reject all").apply {
            foreground = Color(160, 160, 160)
            font = Font("SansSerif", Font.PLAIN, 12)
            isContentAreaFilled = false
            isBorderPainted = false
            isOpaque = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }

        val acceptBtn = JButton("Accept all").apply {
            background = Color(53, 116, 240)
            foreground = Color.WHITE
            font = Font("SansSerif", Font.BOLD, 12)
            isOpaque = true
            isBorderPainted = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color(53, 116, 240)),
                EmptyBorder(4, 12, 4, 12)
            )
        }

        acceptBtn.addActionListener {
            WriteCommandAction.runWriteCommandAction(project) {
                val root = project.guessProjectDir() ?: return@runWriteCommandAction
                try {
                    var file = root.findFileByRelativePath(op.path)
                    if (file == null && op.type != "DELETE") {
                        val parentPath = op.path.substringBeforeLast("/", "")
                        val fileName = op.path.substringAfterLast("/")
                        val parentDir = if (parentPath.isNotEmpty()) {
                            com.intellij.openapi.vfs.VfsUtil.createDirectoryIfMissing(root, parentPath)
                        } else {
                            root
                        }
                        file = parentDir?.createChildData(this, fileName)
                    }
                    if (file != null) {
                        if (op.type == "DELETE") file.delete(this)
                        else {
                            com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(file)?.setText(op.content ?: "")
                            
                            // Immediately open the file in the editor and highlight the changes
                            SwingUtilities.invokeLater {
                                val descriptor = com.intellij.openapi.fileEditor.OpenFileDescriptor(project, file)
                                val editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
                                if (editor != null) {
                                    editor.selectionModel.setSelection(0, editor.document.textLength)
                                    editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {}
            }
            acceptBtn.text = "Accepted"
            acceptBtn.background = Color(76, 175, 80)
            acceptBtn.isEnabled = false
            rejectBtn.isVisible = false
            mainContainer.revalidate()
        }
        
        rejectBtn.addActionListener {
            rejectBtn.text = "Rejected"
            rejectBtn.foreground = Color(244, 67, 54)
            rejectBtn.isEnabled = false
            acceptBtn.isVisible = false
            mainContainer.revalidate()
        }

        bottomRow.add(rejectBtn)
        bottomRow.add(acceptBtn)

        val alignPanel = object : JPanel(BorderLayout()) {
            override fun getPreferredSize(): Dimension {
                val scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, this)
                val maxW = if (scrollPane != null) (scrollPane.width * 0.95).toInt() else 300
                return Dimension(maxW, super.getPreferredSize().height)
            }
        }.apply {
            isOpaque = false
        }
        
        mainContainer.add(topRow, BorderLayout.NORTH)
        mainContainer.add(filesPanel, BorderLayout.CENTER)
        mainContainer.add(bottomRow, BorderLayout.SOUTH)
        
        alignPanel.add(mainContainer, BorderLayout.CENTER)
        add(alignPanel, BorderLayout.CENTER)
    }
}
