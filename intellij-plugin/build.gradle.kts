plugins {
    id("java")
    id("org.jetbrains.intellij.platform")
}

import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
        import org.jetbrains.intellij.platform.gradle.models.ProductRelease

        group = "com.projectxray"
version = "1.0.0"

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        local("/Applications/IntelliJ IDEA.app")
        localPlugin("/Applications/IntelliJ IDEA.app/Contents/plugins/java")
    }

    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.0")
}

intellijPlatform {
    pluginVerification {
        ides {
            select {
                types = listOf(IntelliJPlatformType.IntellijIdea)
                channels = listOf(ProductRelease.Channel.RELEASE)
                sinceBuild = "262"
                untilBuild = "262.*"
            }
        }
    }
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    intellijPlatform {
        pluginConfiguration {
            version = project.version.toString()

            ideaVersion {
                sinceBuild = "262"
            }

            changeNotes = """
            <ul>
              <li>PSI ↔ X-Ray symbol bridge with exact Java method resolution.</li>
              <li>Live debounced editor impact analysis backed by the X-Ray core.</li>
              <li>In-editor dependency/dependent visualization from the persistent symbol graph.</li>
              <li>Architecture graph, health, context and impact views.</li>
              <li>Incremental/background analysis with a single-flight performance guard.</li>
              <li>Plugin verification and IntelliJ IDEA 2026.2 compatibility metadata.</li>
            </ul>
            """.trimIndent()
        }

        pluginVerification {
            ides {
                select {
                    types = listOf(IntelliJPlatformType.IntellijIdea)
                    channels = listOf(ProductRelease.Channel.RELEASE)
                    sinceBuild = "262"
                    untilBuild = "262.*"
                }
            }
        }
    }

    buildSearchableOptions {
        enabled = false
    }
}