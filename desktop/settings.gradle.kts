pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        // Server-Terminal (AP-118): JediTerm is not on Maven Central, only in the JetBrains repository of IntelliJ's
        // dependencies – used for nothing else.
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies") {
            content { includeGroup("org.jetbrains.jediterm") }
        }
    }
}
rootProject.name = "ADEU-LLM-Desktop"
