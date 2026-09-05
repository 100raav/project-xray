# Project X-Ray — IntelliJ IDEA Plugin 1.0.0

Project X-Ray is a local-first Java/Spring Boot codebase intelligence frontend. It follows the project report's separation-of-concerns model: the reusable X-Ray core owns analysis while IntelliJ provides native PSI, navigation, actions and presentation. fileciteturn3file1L71-L86

## Final 1.0.0 capabilities

- **Exact method overload resolution** — PSI provides canonical parameter types; the persistent X-Ray resolver selects an exact stored signature and refuses ambiguous overloads.
- **PSI ↔ X-Ray symbol bridge** — caret/class/method symbols map to stable X-Ray IDs.
- **Live editor change impact** — Java document edits are debounced and analyzed off the UI thread; the core's file-impact result remains explicitly static/repository-backed.
- **In-editor dependency visualization** — X-Ray gutter markers and caret actions show persisted direct dependencies/dependents.
- **Architecture visualization** — repository-derived architecture graph with a configurable rendering cap and double-click source navigation.
- **Large-repository performance layer** — single-flight analysis, background execution, debounce, persisted artifacts and graph rendering limits.
- **Compatibility/verification** — IntelliJ Platform 2026.2 target, Java 25 toolchain, Gradle Plugin 2.x and Plugin Verifier configuration.

## Requirements

- IntelliJ IDEA 2026.2.x
- JDK 25 for plugin development/building
- Project X-Ray core JAR 1.0.0
- Java/Spring Boot project for the first supported analyzer

JetBrains currently documents Java 25 for IntelliJ Platform 2026.2+ and requires IntelliJ Platform Gradle Plugin 2.x for 2024.2+ projects.

## Configure the core

Open **Settings → Tools → Project X-Ray** and select the absolute path to `xray-core-1.0.0.jar`.

Environment fallback: `PROJECT_XRAY_CLI`.

## Development

```bash
./gradlew clean buildPlugin
./gradlew verifyPluginProjectConfiguration
./gradlew runPluginVerifier
./gradlew runIde
```

The generated ZIP is the installable plugin artifact. Do not distribute an unverified development build.

## Compatibility matrix

| IDE | Platform | Java runtime | Status |
|---|---|---|---|
| IntelliJ IDEA 2026.2.x | 262.* | JBR 25 | Primary target |
| Other JetBrains IDEs | 262.* | JBR 25 | Not claimed unless Plugin Verifier confirms the product |
| IntelliJ IDEA < 2026.2 | Earlier platform | Varies | Not claimed by 1.0.0 |

This conservative matrix is intentional: JetBrains notes that IntelliJ Platform APIs can change between releases, so compatibility should be version-tested rather than assumed.

## Privacy

Project X-Ray is local-first. The report explicitly calls for local analysis by default, exclusion of sensitive files/secrets, explicit opt-in for future cloud/AI features and no hidden activity recording. fileciteturn3file6L371-L388

## Architecture alignment

The report specifies a reusable core, IDE-specific clients, a unified code model, dependency/impact analysis and source navigation. fileciteturn4file1L115-L136 The IntelliJ appendix specifically calls for an Action, Tool Window, Editor integration, Project listener, Engine bridge and Compatibility layer. fileciteturn3file1L71-L86
