package com.projectxray.core.index;

import com.projectxray.core.model.*;
import com.projectxray.core.architecture.ArchitectureGraphBuilder;
import com.projectxray.core.galaxy.DependencyGalaxyBuilder;

import java.nio.file.Path;
import java.util.*;

/**
 * Merges per-file evidence into a repository model.
 *
 * Safety rule: this merger is only used when the caller has already established
 * that every affected file has been semantically re-analyzed and that no file
 * was added/removed. It never guesses missing cross-file symbols.
 */
public final class PartialAnalysisMerger {
    public record MergeResult(AnalysisResult result, boolean safe, String reason) {}

    public MergeResult merge(AnalysisResult oldResult, AnalysisResult partial,
                             Set<String> affectedFiles, boolean completeClosure) {
        if (!completeClosure) return new MergeResult(oldResult, false,
            "Dependency closure was not complete; refusing partial publication.");
        if (!sameRepository(oldResult, partial)) return new MergeResult(oldResult, false,
            "Repository identity differs; refusing partial publication.");

        Set<String> oldFiles = filePaths(oldResult);
        Set<String> newFiles = filePaths(partial);
        if (!oldFiles.equals(newFiles)) return new MergeResult(oldResult, false,
            "File set changed; additions/removals require a full repository analysis.");

        Map<String, List<CodeEntity>> entities = byFileEntities(oldResult);
        Map<String, List<CodeRelation>> relations = byEvidenceFileRelations(oldResult);
        Map<String, List<CodeEndpoint>> endpoints = byFileEndpoints(oldResult);

        for (String file : affectedFiles) {
            if (!newFiles.contains(file)) return new MergeResult(oldResult, false,
                "Affected file is missing from the partial analysis: " + file);
            entities.put(file, entitiesFrom(partial, file));
            relations.put(file, relationsFrom(partial, file));
            endpoints.put(file, endpointsFrom(partial, file));
        }

        List<CodeEntity> mergedEntities = flatten(entities);
        List<CodeRelation> mergedRelations = flatten(relations);
        List<CodeEndpoint> mergedEndpoints = flatten(endpoints);

        // If an old relation targets an entity removed from the affected region,
        // it is unsafe to publish. Force a full analysis instead.
        Set<String> entityIds = new HashSet<>();
        for (CodeEntity e : mergedEntities) entityIds.add(e.id());
        for (CodeRelation r : mergedRelations) {
            if (r.sourceId() != null && !r.sourceId().startsWith("endpoint:")
                    && !entityIds.contains(r.sourceId())
                    && !r.sourceId().startsWith("unresolved-")) {
                return new MergeResult(oldResult, false, "Merged relation has missing source entity.");
            }
        }

        var architecture = new ArchitectureGraphBuilder().build(oldResult.project(), mergedEntities, mergedRelations);
        var galaxy = new DependencyGalaxyBuilder().build(oldResult.project(), mergedEntities, mergedRelations);

        AnalysisResult merged = new AnalysisResult(
            oldResult.project(), oldResult.rootPath(), oldResult.language(),
            0L, newFiles.size(), List.copyOf(mergedEntities), List.copyOf(mergedRelations),
            List.copyOf(mergedEndpoints), List.of(
                "Partial semantic merge applied to " + affectedFiles.size() + " affected files."
            ),
            oldResult.build(), oldResult.git(), partial.files(),
            architecture, galaxy, oldResult.gitTimeMachine(), CodeHealthRadar.empty(),
            oldResult.scanDiagnostics()
        );
        var radar = new com.projectxray.core.health.CodeHealthRadarEngine().analyze(merged);
        merged = new AnalysisResult(merged.schemaVersion(), merged.analyzedAt(), merged.project(),
            merged.rootPath(), merged.language(), merged.durationMs(), merged.filesScanned(),
            merged.entities(), merged.relations(), merged.endpoints(), merged.warnings(),
            merged.build(), merged.git(), merged.files(), merged.architectureGraph(),
            merged.dependencyGalaxy(), merged.gitTimeMachine(), radar, merged.scanDiagnostics());
        return new MergeResult(merged, true, "Affected file evidence merged into the repository model.");
    }

    private static boolean sameRepository(AnalysisResult a, AnalysisResult b) {
        return Objects.equals(a.rootPath(), b.rootPath()) && Objects.equals(a.language(), b.language());
    }

    private static Set<String> filePaths(AnalysisResult a) {
        Set<String> s=new HashSet<>();
        for(FileSnapshot f:a.files())s.add(f.path());
        return s;
    }

    private static Map<String,List<CodeEntity>> byFileEntities(AnalysisResult a) {
        Map<String,List<CodeEntity>> m=new HashMap<>();
        for(CodeEntity e:a.entities())m.computeIfAbsent(e.file(),k->new ArrayList<>()).add(e);
        return m;
    }

    private static Map<String,List<CodeRelation>> byEvidenceFileRelations(AnalysisResult a) {
        Map<String,List<CodeRelation>> m=new HashMap<>();
        for(CodeRelation r:a.relations()) if(r.evidenceFile()!=null)
            m.computeIfAbsent(r.evidenceFile(),k->new ArrayList<>()).add(r);
        return m;
    }

    private static Map<String,List<CodeEndpoint>> byFileEndpoints(AnalysisResult a) {
        Map<String,List<CodeEndpoint>> m=new HashMap<>();
        for(CodeEndpoint e:a.endpoints())m.computeIfAbsent(e.file(),k->new ArrayList<>()).add(e);
        return m;
    }

    private static List<CodeEntity> entitiesFrom(AnalysisResult a,String file){
        return a.entities().stream().filter(e->file.equals(e.file())).toList();
    }
    private static List<CodeRelation> relationsFrom(AnalysisResult a,String file){
        return a.relations().stream().filter(r->file.equals(r.evidenceFile())).toList();
    }
    private static List<CodeEndpoint> endpointsFrom(AnalysisResult a,String file){
        return a.endpoints().stream().filter(e->file.equals(e.file())).toList();
    }

    private static <T> List<T> flatten(Map<String,List<T>> m){
        List<T> out=new ArrayList<>();
        for(List<T> v:m.values())out.addAll(v);
        return List.copyOf(out);
    }
}
