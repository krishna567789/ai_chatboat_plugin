plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.example"
version = "1.3.2"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.commonmark:commonmark:0.21.0")
    implementation("com.google.code.gson:gson:2.10.1")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
    }
}

intellij {
    version.set("2023.2.5")
    type.set("IC")
    plugins.set(listOf("android"))
}

tasks {
    patchPluginXml {
        sinceBuild.set("232")
        untilBuild.set("262.*")
        changeNotes.set("""
      <h3>Version 1.3.2 - Networking & API Error Handling</h3>
      <ul>
        <li><b>Fix:</b> Handled network timeouts to prevent the UI from freezing indefinitely when offline.</li>
        <li><b>Fix:</b> Proper API error message handling (e.g. invalid API key, 401 Unauthorized) now displays directly in the chat instead of hanging on "Thinking Process".</li>
      </ul>
      <h3>Version 1.3.1 - Compatibility Update</h3>
      <ul>
        <li><b>Fix:</b> Resolved issue where plugin would freeze on "Thinking Process" in newer IDEs (2024.1+) due to missing Gson library.</li>
        <li><b>Fix:</b> Updated IDE compatibility to support up to version 262.* (e.g., Android Studio Koala/Ladybug).</li>
      </ul>
      <h3>Version 1.3.0 - Premium UX Update</h3>
      <ul>
        <li><b>Welcome Screen:</b> Beautiful empty state with quick action chips.</li>
        <li><b>Context Transparency:</b> See exactly what file/line the AI is reading via Context Chips.</li>
        <li><b>Micro-Animations:</b> Smooth animated typing indicators and auto-scrolling chat history.</li>
        <li><b>Hover Polish:</b> Premium hover effects for buttons and glassmorphism UI tweaks.</li>
        <li><b>Inline Editor Integration:</b> Highlight code, right-click, and select "Ask Syntax AI..." to rewrite code.</li>
        <li><b>Vision API Support:</b> Upload screenshots by clicking the '📎' button.</li>
        <li><b>Project Scaffolding:</b> AI can now instantly generate multiple directories and files for you in one command.</li>
      </ul>
      """)
    }
    
    buildSearchableOptions {
        enabled = false
    }
}
