import org.gradle.api.initialization.resolve.RepositoriesMode
import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    }
}

plugins {
    id("org.jetbrains.intellij.platform.settings") version "2.18.1"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
        intellijPlatform {
            defaultRepositories()
        }
    }
}

rootProject.name = "project-xray-intellij"