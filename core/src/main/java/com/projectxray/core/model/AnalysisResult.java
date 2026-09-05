package com.projectxray.core.model;

import java.time.Instant;
import java.util.List;

public record AnalysisResult(
    String schemaVersion,
    String analyzedAt,
    String project,
    String rootPath,
    String language,
    long durationMs,
    int filesScanned,
    List<CodeEntity> entities,
    List<CodeRelation> relations,
    List<CodeEndpoint> endpoints,
    List<String> warnings,
    BuildInfo build,
    GitInfo git,
    List<FileSnapshot> files,
    ArchitectureGraph architectureGraph,
    DependencyGalaxy dependencyGalaxy,
    GitTimeMachine gitTimeMachine,
    CodeHealthRadar codeHealthRadar,
    ScanDiagnostics scanDiagnostics
) {
    public static final String CURRENT_SCHEMA = "1.0";

    public AnalysisResult(String project, String rootPath, String language,
                          long durationMs, int filesScanned,
                          List<CodeEntity> entities, List<CodeRelation> relations,
                          List<CodeEndpoint> endpoints, List<String> warnings,
                          BuildInfo build, GitInfo git, List<FileSnapshot> files) {
        this(CURRENT_SCHEMA, Instant.now().toString(), project, rootPath, language,
            durationMs, filesScanned, entities, relations, endpoints, warnings,
            build, git, files, ArchitectureGraph.empty(), DependencyGalaxy.empty(),
            GitTimeMachine.empty(), CodeHealthRadar.empty(), ScanDiagnostics.complete(0, 0));
    }

    public AnalysisResult(String project, String rootPath, String language,
                          long durationMs, int filesScanned,
                          List<CodeEntity> entities, List<CodeRelation> relations,
                          List<CodeEndpoint> endpoints, List<String> warnings,
                          BuildInfo build, GitInfo git, List<FileSnapshot> files,
                          ArchitectureGraph architectureGraph) {
        this(CURRENT_SCHEMA, Instant.now().toString(), project, rootPath, language,
            durationMs, filesScanned, entities, relations, endpoints, warnings,
            build, git, files, architectureGraph, DependencyGalaxy.empty(),
            GitTimeMachine.empty(), CodeHealthRadar.empty(), ScanDiagnostics.complete(0, 0));
    }

    public AnalysisResult(String project, String rootPath, String language,
                          long durationMs, int filesScanned,
                          List<CodeEntity> entities, List<CodeRelation> relations,
                          List<CodeEndpoint> endpoints, List<String> warnings,
                          BuildInfo build, GitInfo git, List<FileSnapshot> files,
                          ArchitectureGraph architectureGraph,
                          DependencyGalaxy dependencyGalaxy) {
        this(CURRENT_SCHEMA, Instant.now().toString(), project, rootPath, language,
            durationMs, filesScanned, entities, relations, endpoints, warnings,
            build, git, files, architectureGraph, dependencyGalaxy, GitTimeMachine.empty(), CodeHealthRadar.empty(), ScanDiagnostics.complete(0, 0));
    }

    public AnalysisResult(String project, String rootPath, String language,
                          long durationMs, int filesScanned,
                          List<CodeEntity> entities, List<CodeRelation> relations,
                          List<CodeEndpoint> endpoints, List<String> warnings,
                          BuildInfo build, GitInfo git, List<FileSnapshot> files,
                          ArchitectureGraph architectureGraph,
                          DependencyGalaxy dependencyGalaxy,
                          GitTimeMachine gitTimeMachine,
                          CodeHealthRadar codeHealthRadar) {
        this(CURRENT_SCHEMA, Instant.now().toString(), project, rootPath, language,
            durationMs, filesScanned, entities, relations, endpoints, warnings,
            build, git, files, architectureGraph, dependencyGalaxy, gitTimeMachine,
            codeHealthRadar, ScanDiagnostics.complete(0, 0));
    }

    public AnalysisResult(String project, String rootPath, String language,
                          long durationMs, int filesScanned,
                          List<CodeEntity> entities, List<CodeRelation> relations,
                          List<CodeEndpoint> endpoints, List<String> warnings,
                          BuildInfo build, GitInfo git, List<FileSnapshot> files,
                          ArchitectureGraph architectureGraph,
                          DependencyGalaxy dependencyGalaxy,
                          GitTimeMachine gitTimeMachine,
                          CodeHealthRadar codeHealthRadar,
                          ScanDiagnostics scanDiagnostics) {
        this(CURRENT_SCHEMA, Instant.now().toString(), project, rootPath, language,
            durationMs, filesScanned, entities, relations, endpoints, warnings,
            build, git, files, architectureGraph, dependencyGalaxy, gitTimeMachine,
            codeHealthRadar, scanDiagnostics);
    }
}
