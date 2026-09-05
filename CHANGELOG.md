## 1.0.0 — Final IntelliJ Release

- Exact method overload resolution using parameter types.
- Native PSI ↔ X-Ray symbol bridge.
- Debounced live editor change-impact workflow.
- In-editor dependency/dependent visualization and gutter markers.
- Background/single-flight analysis and graph rendering limits for large repositories.
- IntelliJ IDEA 2026.2 compatibility target and Plugin Verifier configuration.
- Marketplace-oriented metadata, documentation, privacy statement and release checklist.

## 1.9.0-SNAPSHOT

- Added persistent method resolution by owner, name, and arity.
- Added ambiguity-safe overload handling.
- Added CLI `--method` command and resolver tests.
- Added final IntelliJ plugin module targeting IntelliJ Platform 2026.2 with Gradle Plugin 2.x.

## 1.8.0-SNAPSHOT

- Added compiler-context type resolution facade over the persistent symbol store.
- Added deterministic same-package/imported type resolution and external JDK classification.
- Added persisted `.xray/compiler-context.json` report.
- Added CLI `--context` and VS Code compiler-context inspection.
- Added compiler-context tests.

## 1.7.0-SNAPSHOT

- Added active persistent symbol resolver.
- Added deterministic ID/qualified/simple-name resolution with ambiguity handling.
- Added bounded dependency/dependent traversal over persisted relationships.
- Added CLI resolver commands and VS Code symbol-resolution command.
- Added resolver tests.

## 1.6.0-SNAPSHOT

- Added persistent compiler-context symbol store.
- Added symbol lookup indexes by ID, qualified name, simple name, file, incoming and outgoing relations.
- Added CLI `--symbols` and VS Code symbol-store inspection command.
- Added persistent symbol-store tests.

## 1.5.0-SNAPSHOT

- Added scoped semantic extraction for dependency-aware affected files.
- Added PartialAnalysisMerger with safety gates and complete-analysis fallback.
- Added partial-analysis tests.
- Updated VS Code cache state to surface partial semantic re-analysis.

## 1.4.0-SNAPSHOT

- Added per-file semantic fingerprints to the persistent code index.
- Added safe semantic-no-op reuse for comment/whitespace-only source changes.
- Added CLI and VS Code visibility for semantic reuse.
- Added semantic fingerprint regression tests for comments, whitespace, code changes, and string literals.

## 1.3.0-SNAPSHOT

- Added dependency-aware reverse invalidation planning from the persistent index.
- Added transitive dependent closure calculation.
- Added persisted `.xray/invalidation-plan.json` and VS Code inspection command.
- Kept complete semantic re-analysis as the correctness boundary.

## 1.2.0-SNAPSHOT

- Added persistent per-file symbol/relationship/endpoint index under `.xray/code-index.json`.
- Added atomic index writes and schema validation.
- Added CLI `--index` inspection command.
- Added VS Code `Open Persistent Code Index` command.
- Preserved conservative full re-analysis on source changes.

## 1.1.0-SNAPSHOT

- Added conservative incremental analysis using persisted SHA-256 source fingerprints.
- Reuses an unchanged Java semantic model without reparsing.
- Falls back to complete analysis whenever source files change.
- Added explicit cache reuse visibility in VS Code.
- Added foundation for a future persistent symbol/relationship index without claiming partial-graph completeness.

## 1.0.0-SNAPSHOT

- Consolidated X-Ray analysis engines into the serious VS Code MVP workflow.
- Added Activity Bar repository state and actionable Explorer entries.
- Added analysis progress/status feedback.
- Added explicit large-repository scan diagnostics and optional XRAY_MAX_JAVA_FILES safeguard.
- Added scan completeness metadata so partial runs cannot masquerade as complete analysis.

## 0.9.0-SNAPSHOT

- Added explainable Code Health Radar.
- Added architecture cycle, fan-in/fan-out, relationship-density, symbol-concentration and method-density metrics.
- Added Git HEAD change-surface metric when history is available.
- Added structural findings with source-backed evidence.
- Added VS Code health score/metrics/findings panel and Health Radar command.
- Explicitly excluded unsupported runtime and coverage claims.

## 0.8.0-SNAPSHOT

- Added real Git commit timeline reconstruction.
- Added historical Java source analysis from Git commits without modifying the working tree.
- Added HEAD-vs-parent architecture/entity/package/relation/endpoint/cycle diff metrics.
- Added VS Code Code Time Machine view with real commit and snapshot data.
- Added recursion guard for historical snapshot analysis.

## 0.7.0-SNAPSHOT

- Added on-demand source-backed change-impact analysis.
- Added direct/transitive caller and dependency traversal.
- Added endpoint and test impact correlation from the real model.
- Added VS Code action to analyze real impact for a selected entity.
- Added unresolved-relationship safeguards and explicit static-analysis warnings.

## 0.6.0-SNAPSHOT

- Added real repository-backed Dependency Galaxy.
- Added dependency graph nodes for actual Java classes, interfaces and methods.
- Added evidence-count aggregation, graph depth and cycle detection.
- Added futuristic VS Code Galaxy visualization with real-data search, filters, focus mode and source navigation.
- Added tests preventing unresolved symbols from becoming fake graph nodes.

# Changelog

## 0.5.0-SNAPSHOT

- Added repository-derived architecture graph.
- Added package-level dependency aggregation from concrete source relations.
- Added cycle detection for cross-package dependency graph.
- Added architecture graph schema to analysis output.
- Added real package/type visualization in the VS Code extension.
- Added architecture filters and source-backed node inspection.
- Removed the previous synthetic X-Ray center node from the architecture visualization.

## 0.4.0-SNAPSHOT

- Added Java semantic analysis, Spring endpoints/dependencies, build metadata, Git context and source fingerprints.
