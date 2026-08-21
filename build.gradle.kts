plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.20"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.krishna"
version = "1.0.0"

repositories {
    mavenCentral()
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
        untilBuild.set("242.*")
    }
}
