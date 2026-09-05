# Project X-Ray 1.0.0 — Marketplace Release Checklist

## Product
- [x] Stable plugin ID
- [x] Marketplace-readable name and description
- [x] Vendor identity
- [x] Version 1.0.0
- [x] Change notes
- [x] Local-first privacy statement
- [x] Conservative compatibility range

## Technical
- [x] IntelliJ Platform Gradle Plugin 2.x
- [x] IntelliJ IDEA 2026.2 target
- [x] Java 25 toolchain declaration
- [x] Java bundled plugin dependency
- [x] Plugin verification configuration
- [x] Background analysis
- [x] Debounced live impact
- [x] PSI bridge
- [x] Source navigation
- [x] Persistent symbol graph
- [x] Rendering cap for large graphs

## Required release commands

```bash
./gradlew clean buildPlugin
./gradlew verifyPluginProjectConfiguration
./gradlew runPluginVerifier
```

Only publish the ZIP after all three commands pass on the release environment.

## Compatibility evidence

Record the exact Plugin Verifier output and the tested IDE build number in `verification/`. A source-code claim of compatibility is not a substitute for an actual verification run.
