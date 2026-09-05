# Project X-Ray 1.0.0

Real-repository architecture intelligence for Java/Spring projects.

## Roadmap position

0.2 Real repository scanning
→ 0.3 Java symbol resolution
→ 0.4 Spring/build/Git evidence
→ **0.5 Real architecture graph**
→ **0.6 Dependency Galaxy**
→ 0.6 Dependency Galaxy
→ 0.7 Change-impact engine
→ 0.8 Git / Code Time Machine
→ 0.9 Code Health Radar
→ 1.0 Serious VS Code MVP

## 0.6 adds

- A source-backed Dependency Galaxy containing only real analyzed classes, interfaces and methods.
- Aggregated relationship edges with evidence counts.
- Degree-based node sizing, package clustering and cycle detection.
- Search and framework-role filters in the VS Code visualization.
- Focus mode for a selected real node and direct source navigation.
- No placeholder/demo nodes are inserted into the galaxy.

## 0.5 adds

- Repository-derived architecture graph
- Real package nodes based on Java source packages
- Aggregated cross-package dependency edges based on concrete code relations
- Structural project→package containment
- Architecture layers inferred from package naming only as a descriptive classification
- Inbound/outbound dependency counts
- Cycle detection over package dependency edges
- Versioned `architectureGraph` in analysis JSON
- VS Code architecture view driven by the real analysis result
- Search, layer filtering, dependency filtering and node-to-source navigation

## Trust rule

No sample entities, package names, dependency counts, architecture edges or metrics are injected into a user's analysis. If the real repository contains no package or cross-package relation, the corresponding graph remains empty.

## Build

Requires JDK 21 and Maven.

```bash
mvn clean test package
```

## Analyze a real repository

```bash
java -jar core/target/xray-core-0.6.0-SNAPSHOT.jar \
  "/absolute/path/to/project" \
  "/absolute/path/to/project/.xray/analysis.json"
```

## VS Code

```bash
cd vscode-extension
npm install
npm run compile
```

Open the extension folder in VS Code and press F5. In the Extension Development Host, open the real project and run `Project X-Ray: Analyze Project`.

## Important limitation

0.5 is an architecture-graph foundation, not a full architectural truth engine. Package-layer labels are descriptive heuristics; they are not proof of architectural boundaries. The graph edges are backed by relationships the analyzer can resolve. Runtime reflection, generated code, dynamic proxies, configuration-driven wiring and external services can still be outside static source analysis.


## 0.7 Change-impact engine

Select any real analyzed class/interface/method in the Dependency Galaxy and choose **Analyze real change impact**. The VS Code extension invokes the Java core against the current repository and traverses concrete, resolved graph relationships. It reports direct and transitive callers/dependencies, related Spring endpoints and test entities when they are present in the analyzed model.

Impact is deliberately described as **potential static-graph impact**, not guaranteed runtime impact. Unresolved/external relationship targets are excluded rather than being presented as fake affected components.

### 0.7 CLI

```bash
java -jar core/target/xray-core-0.7.0-SNAPSHOT.jar \
  "/absolute/path/to/your/project" \
  --impact "type:com.example.UserService"
```

The entity ID must come from the current `.xray/analysis.json` or the current analysis result. No demo IDs are valid.


## 0.8 Git / Code Time Machine

When the supplied project is a real Git repository, X-Ray reconstructs historical Java snapshots directly from real commits using `git archive`. It does not modify the working tree and does not fabricate historical data.

The default history view loads the latest 12 commits. For each available commit, X-Ray can analyze the archived source and record:

- commit hash, author, date and subject
- changed-file count
- historical file/entity/relation/endpoint/package/cycle counts
- analyzer warnings

X-Ray also compares `HEAD` with the immediately previous commit using real source analysis and Git diff information. The comparison reports changed files, added/removed entities and packages, and deltas for relationships, endpoints and architecture cycles.

### Time Machine CLI

Normal analysis includes the `gitTimeMachine` field in `analysis.json` when the project is a Git repository.

The VS Code command `Project X-Ray: Open Code Time Machine` opens the same real timeline through the analyzer-backed view. The visualization is a representation of commit data and reconstructed snapshots; it is not a pre-authored timeline.

### Important limitations

Historical reconstruction is source reconstruction, not runtime reconstruction. A commit can be incomplete for generated sources, external dependencies or build-time resources. X-Ray surfaces analyzer warnings rather than claiming a perfect historical runtime state.


## 0.9 Code Health Radar

The Code Health Radar is a conservative, explainable static-analysis layer. It uses only evidence already present in the X-Ray repository model.

Current metrics include:

- architecture cycles
- average fan-out
- average fan-in
- relationship density
- maximum analyzed entities in one file
- average methods per analyzed type
- HEAD change surface when Git is available
- an overall bounded static score with explicit deductions

Findings identify high symbol concentration and detected architecture cycles. Every finding includes evidence and, where available, a real file/entity reference.

The radar intentionally does not invent test coverage, runtime performance, production error rates, or reflection behavior. Coverage is not scored unless a trustworthy coverage report is integrated in a future milestone.

The VS Code Galaxy now displays the radar score, metrics and structural findings. You can also run `Project X-Ray: Open Code Health Radar`.


## 1.0 Serious VS Code MVP

1.0 consolidates the existing repository-backed engines into a single VS Code workflow:

- repository analysis
- Java symbol resolution
- Spring endpoint/dependency mapping
- architecture graph
- Dependency Galaxy
- change impact
- Git / Code Time Machine
- Code Health Radar
- Activity Bar explorer state
- progress/status feedback
- on-save re-analysis
- configurable core JAR and large-repository safeguards

### Large repository policy

X-Ray does not impose a small default file-count cap. It discovers all non-ignored `.java` files under the repository and attempts repository-wide semantic analysis.

For CI or memory-constrained machines, `XRAY_MAX_JAVA_FILES` can be set explicitly. When that happens the analyzer marks the result as partial and records the discovered/analyzed counts in `scanDiagnostics`.

The current Java analyzer keeps source units in memory for cross-file symbol resolution. Therefore very large monorepos may eventually need a persistent index/incremental engine; 1.0 deliberately reports this architectural limitation instead of claiming unlimited scale.


## 1.1 Incremental Repository Index Foundation

1.1 adds a conservative incremental coordinator. The persisted `.xray/analysis.json` contains SHA-256 fingerprints for analyzed source files. On a subsequent analysis, X-Ray compares the current Java fingerprints with that persisted model.

- If every analyzed Java source is unchanged, the existing semantic model is reused without reparsing the repository.
- If any Java source is added, removed, or changed, X-Ray performs a complete semantic analysis rather than producing an incomplete graph.
- The result explicitly records cache reuse in warnings and the VS Code dashboard labels the scan as `reused` or `fresh`.
- `XRAY_MAX_JAVA_FILES` remains available as an explicit safeguard; partial scans are marked as partial.

This is the foundation for a future persistent symbol/relationship index. It is deliberately conservative: 1.1 does not claim file-level incremental graph mutation yet.


## 1.2 Persistent Symbol & Relationship Index

X-Ray now materializes a repository-backed `code-index.json` under `.xray/`.

Each indexed source file records:

- SHA-256 fingerprint
- size and modification timestamp
- symbols/entities discovered in that file
- relationships whose evidence originates in that file
- Spring endpoints discovered in that file

The index is created/refreshed after a complete semantic analysis. The incremental coordinator can reuse an unchanged analysis model, while changed repositories still take the conservative complete-analysis path.

VS Code includes `Project X-Ray: Open Persistent Code Index`, and the CLI includes `--index`.

This is an index foundation, not yet dependency-aware file-level mutation. That remains the next optimization stage for massive monorepos.


## 1.3 Dependency-Aware Incremental Planning

1.3 adds a repository-backed reverse-dependency invalidation planner.

When source fingerprints change, X-Ray uses the persistent index to map symbols to source files and walks reverse cross-file relationships. The resulting closure identifies the changed files and their direct/transitive dependents that would need re-analysis.

For correctness, 1.3 still performs the complete semantic analysis after calculating the plan. The plan is persisted to `.xray/invalidation-plan.json` so the system can be inspected and validated without pretending that a partial graph is authoritative.

This is the bridge toward true file-level incremental re-analysis.


## 1.4 Semantic No-Op Incremental Reuse

1.4 adds a conservative semantic fingerprint to each indexed Java file. The fingerprint removes comments and insignificant whitespace while preserving string and character literal contents.

If a file's raw SHA-256 changes but its normalized semantic fingerprint remains identical, X-Ray can reuse the authoritative semantic model without reparsing the repository. The decision is surfaced as `semanticNoOp` in the CLI and `semantic reuse` in the VS Code cache status.

Real code changes, additions, or removals still cross the correctness boundary and trigger complete semantic analysis after dependency-aware invalidation planning.

This is a genuine file-level optimization, not a claim of arbitrary partial semantic graph mutation.


## 1.5 True Partial Semantic Re-analysis

1.5 introduces a scoped semantic-analysis path. The repository is still parsed and type-registered globally so Java symbol resolution retains complete source context, but relationship/dependency/endpoint extraction is restricted to the dependency-aware affected-file closure.

A `PartialAnalysisMerger` replaces the affected per-file evidence buckets and rebuilds architecture/Galaxy views from the merged evidence. It refuses to publish a partial model if repository identity differs, files were added/removed, or closure safety is not established.

If any safety check fails, X-Ray falls back to complete semantic analysis. This is intentionally conservative.


## 1.6 Persistent Compiler-Context Symbol Store

X-Ray now persists a dedicated `.xray/symbol-store.json` containing real CodeEntity symbols and CodeRelation adjacency. The store supports lookup by symbol ID, qualified name, simple name, source file, outgoing relation, and incoming relation.

The store is derived only from the repository analysis. It is not a fixture database. The CLI supports `--symbols`, and VS Code provides `Open Persistent Symbol Store`.

This is the foundation for avoiding repeated source parsing in future versions. The current Java analyzer still builds complete symbol context when a semantic analysis is requested.


## 1.7 Persistent Symbol Resolution Engine

The persistent symbol store is now an active lookup layer. `PersistentSymbolResolver` resolves symbols by exact ID, exact qualified name, or an unambiguous simple name. Ambiguous simple names are reported rather than guessed.

The resolver also traverses persisted incoming/outgoing relationships to answer dependency and dependent queries with bounded depth. CLI commands include `--resolve`, `--dependencies`, and `--dependents`; VS Code provides a symbol-resolution command.

This is a persistent lookup/resolution layer built from real repository analysis. The JavaParser symbol solver remains the authoritative compiler-aware resolver for fresh semantic parsing; 1.7 does not claim that a JSON store replaces the Java compiler.


## Compiler-Context Resolution

X-Ray now has a compiler-context facade over the persistent symbol store. Project types are resolved deterministically by exact/qualified name, same-package name, or imports. JDK/platform types are classified as external, and unknown or ambiguous project types remain unresolved instead of being guessed.

A compiler-context report is persisted at `.xray/compiler-context.json`. The CLI supports `--context`, and VS Code provides `Open Compiler Context`.

The persistent resolver complements JavaParser's authoritative symbol solver; it does not pretend a JSON index can replace the Java compiler.


## Persistent Method Resolution

X-Ray now resolves persisted methods using owner type + method name + arity. If overloads remain ambiguous because parameter types are not persisted, the resolver reports the ambiguity instead of guessing.

The CLI supports `--method <owner> <method> <arity>`. This is the bridge toward exact call-target resolution and method-level change impact.


## IntelliJ IDEA 1.0.0 Frontend

Project X-Ray now includes an IntelliJ Platform plugin module under `intellij-plugin/`.

The plugin is a real frontend over the same X-Ray core: it invokes the core JAR for repository analysis and persistent symbol resolution, then reads the generated `.xray` artifacts. It includes a tool window, Analyze action, symbol resolution action, architecture graph view, dependency/impact/context/health data views, and artifact refresh.

## VS Code compatibility

The existing VS Code extension remains supported. VS Code and IntelliJ are separate frontends over the same X-Ray core and `.xray` repository artifacts.

- VS Code: `vscode-extension/`
- IntelliJ IDEA: `intellij-plugin/`
- Shared engine: `core/`

Do not treat the IntelliJ plugin JAR as a VS Code extension or vice versa; each IDE requires its own frontend package.

## Final release

The final product release is **1.0.0**. The IntelliJ module implements the report-defined second IDE integration and the final hardening milestones: exact overload resolution, PSI bridge, live impact, in-editor dependency visualization, large-repository controls and verification-ready packaging.
