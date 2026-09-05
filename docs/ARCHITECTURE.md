# Project X-Ray Architecture — 1.0.0

## Trust boundary

All architecture nodes and dependency edges shown by X-Ray are derived from the repository supplied to the analyzer. No sample project is bundled into the analysis result.

## Architecture graph

The 0.5 graph is package-centric. A package node is created only if at least one real Java class/interface is discovered in that package. A cross-package edge is created only when a concrete analyzed relation connects source entities in different packages.

The following relationship types may contribute to an aggregated edge: imports, calls, calls-unmapped, calls-unresolved, injects, uses-type, extends and implements. Unresolved relationships remain explicitly marked and are not silently treated as resolved.

## Cycle detection

Cycles are calculated over the package dependency graph. The result is a list of package-node IDs forming detected cycles. This is a static source-level signal and should not be interpreted as a complete runtime architecture proof.

## Layer labels

Layer labels are descriptive heuristics based on package naming (presentation, application, data, domain, other). They are filters for visualization, not claims about the actual architecture.

## VS Code

The extension receives the complete analysis JSON from the core JAR and renders the architecture graph. Package mode shows package nodes and aggregated cross-package edges. Type mode shows real class/interface entities and source relationships. Selecting a type can open the actual source file and line.


## Dependency Galaxy (0.6)

The Dependency Galaxy is a second graph layer built from the same repository-backed analysis model. It creates nodes only for real analyzed classes, interfaces and methods. An edge exists only when both relationship endpoints exist as real entities in the current analysis. Unresolved or external placeholder relationship targets are deliberately excluded from the Galaxy rather than rendered as if they were source entities.

The VS Code Galaxy view adds package halos, directional relationship lines, evidence-weighted line thickness, framework-role filtering, search, focus mode and source navigation. The visual placement is deterministic and derived from the package membership and graph structure; it is not a hard-coded project illustration.


## Change Impact (0.7)

The change-impact engine is an on-demand traversal over the current source-backed relationship graph. For a selected real entity, incoming relationships are traversed to find potential callers/dependents and outgoing relationships are traversed to find reachable dependencies. Only endpoints whose controller/method entities are in the resulting set are reported as affected endpoints. Test entities are reported only when they are actually present in the analyzed repository and reachable through the graph.

The engine explicitly avoids runtime certainty. Dynamic reflection, generated code, runtime configuration, external systems and unresolved symbols can make static impact incomplete. Such limitations are surfaced as warnings instead of hidden.


## Git / Code Time Machine (0.8)

The Time Machine uses the repository's own Git object database as the historical source of truth. It obtains commit metadata through Git commands and reconstructs selected commits with `git archive` into temporary directories. The existing Java analyzer then analyzes those source trees with history disabled, preventing recursive history analysis.

The working tree is never checked out or reset. This makes historical analysis safer for a developer's current uncommitted work.

The current comparison is between `HEAD` and its immediate parent. Entity and package additions/removals are derived from the two analyzer models; file changes are derived from `git diff`. Relation, endpoint and cycle deltas are calculated from the reconstructed/current models.


## Code Health Radar (0.9)

The health layer consumes the current `AnalysisResult`, architecture graph, resolved relationships and Git Time Machine diff. It calculates transparent structural metrics and produces findings with evidence. The score is intentionally not presented as a security score, runtime score, or production-readiness guarantee.

The current implementation avoids unsupported metrics. In particular, it does not fabricate test coverage because the repository model does not yet ingest a coverage report. Dynamic runtime behavior is likewise outside the score.


## Serious VS Code MVP (1.0)

The VS Code extension is the product shell around the local Java analysis core. It exposes a repository-backed explorer, unified Galaxy visualization, Code Health Radar, Change Impact and Git Time Machine commands. Analysis runs against the selected workspace and persists the resulting JSON under `.xray/analysis.json`.

Large-repository behavior is explicit. The default scanner has no low artificial file cap; an optional `XRAY_MAX_JAVA_FILES` environment variable can intentionally cap a run. Partial scans are marked through `scanDiagnostics.complete=false`. Cross-file Java semantic resolution currently requires source units in memory, so a future incremental persistent index is the path to truly massive monorepos.


## Incremental Analysis Foundation (1.1)

The CLI now routes analysis through `IncrementalAnalysisEngine`. The engine uses the persisted X-Ray file fingerprints as a cache key. An exact Java-source fingerprint match reuses the previous semantic model; otherwise it falls back to the complete Java analyzer.

This two-path design is intentional. Reusing an unchanged model is safe because its source evidence is identical. For changed repositories, 1.1 refuses to splice partial symbol graphs and instead performs a full analysis. A future persistent index can replace this conservative fallback with dependency-aware file-level updates.


## Persistent Symbol/Relationship Index (1.2)

`PersistentCodeIndex` materializes the analysis evidence into `.xray/code-index.json`. The index is partitioned by source-file fingerprint and retains entities, evidence-backed relations, and endpoints per file.

The current invalidation policy remains conservative: a source change causes a complete semantic analysis and index rebuild. This prevents stale cross-file relationships from being presented as authoritative. A future dependency-aware invalidation layer can use the per-file buckets to update only affected graph regions.


## Dependency-Aware Invalidation (1.3)

`DependencyAwareInvalidationEngine` constructs a reverse file-dependency graph from persisted source-backed `CodeRelation` records. A changed/added/removed file seeds the invalidation set, and reverse traversal computes direct and transitive dependents.

The result is an explicit invalidation plan. The semantic analyzer still runs repository-wide after planning. This conservative boundary is intentional: until symbol-level cache mutation and dependency invalidation are implemented, X-Ray must not publish a supposedly complete graph assembled from stale and newly parsed fragments.


## Semantic No-Op Reuse (1.4)

Each indexed Java file now stores a normalized semantic fingerprint. If a source file changes only in comments or insignificant whitespace, its raw fingerprint changes but its semantic fingerprint remains stable. The coordinator can therefore reuse the existing repository-derived semantic model.

String and character literal contents are preserved during normalization. Any code-level fingerprint change, addition, or removal continues through the conservative dependency-aware plan and complete semantic analyzer.


## True Partial Semantic Re-analysis (1.5)

The analyzer now accepts an optional semantic-file scope. Pass 1 remains repository-wide to establish real Java type/method context. Pass 2 is scoped to the affected dependency closure, limiting relationship, Spring endpoint, injection, and call-edge extraction to files that need updated evidence.

`PartialAnalysisMerger` then replaces only affected file evidence in the persistent model and regenerates architecture/Galaxy projections. Any unsafe repository/file-set condition causes a complete-analysis fallback.


## Persistent Compiler-Context Symbol Store (1.6)

`PersistentSymbolStore` materializes the repository's `CodeEntity` and `CodeRelation` records into `.xray/symbol-store.json`. It maintains secondary lookup views by ID, qualified name, simple name, file, outgoing relation, and incoming relation.

The store is authoritative only for the analysis from which it was built. It is regenerated whenever X-Ray produces an authoritative updated model. Future analyzer versions can use it as a compiler-context cache rather than reparsing unaffected source units.


## Persistent Symbol Resolution Engine (1.7)

`PersistentSymbolResolver` consumes the persisted symbol store for deterministic ID/qualified/simple-name resolution and bounded graph traversal. It deliberately rejects ambiguous simple names instead of choosing arbitrarily.

The resolver complements, rather than replaces, JavaParser's compiler-aware `JavaSymbolSolver`. A future compiler-context layer can use the persistent store to avoid rebuilding unaffected source context while still escalating to authoritative source resolution when required.


## Compiler-Context Resolution (1.8)

`CompilerContextResolver` provides deterministic project-type resolution against the persistent symbol store. It checks exact/qualified names, same-package symbols, and imported symbols; JDK/platform names are classified as external. Ambiguous and unknown project types are reported without arbitrary selection.

The compiler-context report records the actual indexed project symbols and unresolved-call evidence from the authoritative analysis.


## Persistent Method Resolution (1.9)

`PersistentMethodResolver` consumes persisted method entities and resolves an owner/name/arity candidate. It refuses to select an overloaded method when the persisted context cannot distinguish parameter types. JavaParser remains authoritative for fresh compiler-aware overload resolution.
