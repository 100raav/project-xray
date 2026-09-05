package com.projectxray.core.index;

import com.projectxray.core.model.CodeRelation;

import java.util.*;

/**
 * Computes a conservative dependency-aware invalidation set from the persistent
 * per-file index. It walks reverse relationships from symbols found in changed
 * files. This is an invalidation PLAN, not a claim that a partial graph is
 * already safe to publish.
 */
public final class DependencyAwareInvalidationEngine {

    public record Plan(
        Set<String> changedFiles,
        Set<String> directlyAffectedFiles,
        Set<String> transitivelyAffectedFiles,
        Set<String> filesToReanalyze,
        int traversedRelationships,
        String strategy
    ) {}

    public Plan plan(PersistentCodeIndex.IndexDocument index,
                     Set<String> changedFiles,
                     Set<String> addedFiles,
                     Set<String> removedFiles) {
        Map<String, PersistentCodeIndex.IndexedFile> byPath =
            new PersistentCodeIndex().byPath(index);

        Map<String, Set<String>> reverse = new HashMap<>();
        Map<String, String> entityToFile = new HashMap<>();

        for (var file : index.files()) {
            for (var entity : file.entities()) {
                entityToFile.put(entity.id(), file.path());
            }
        }

        int edges = 0;
        for (var file : index.files()) {
            for (CodeRelation r : file.relations()) {
                String targetFile = entityToFile.get(r.targetId());
                String sourceFile = entityToFile.get(r.sourceId());
                if (sourceFile != null && targetFile != null && !sourceFile.equals(targetFile)) {
                    reverse.computeIfAbsent(targetFile, k -> new LinkedHashSet<>()).add(sourceFile);
                    edges++;
                }
            }
        }

        Set<String> direct = new LinkedHashSet<>();
        for (String changed : changedFiles) {
            // The changed file itself must be re-analyzed.
            direct.add(changed);
            PersistentCodeIndex.IndexedFile old = byPath.get(changed);
            if (old == null) continue;
            for (var entity : old.entities()) {
                direct.addAll(reverse.getOrDefault(old.path(), Set.of()));
            }
        }
        direct.addAll(addedFiles);

        // Removed files are not re-analyzed, but their previous dependents are
        // invalidated through the same reverse graph.
        for (String removed : removedFiles) {
            direct.addAll(reverse.getOrDefault(removed, Set.of()));
        }

        Set<String> transitive = new LinkedHashSet<>(direct);
        ArrayDeque<String> queue = new ArrayDeque<>(direct);
        while (!queue.isEmpty()) {
            String file = queue.removeFirst();
            for (String dependent : reverse.getOrDefault(file, Set.of())) {
                if (transitive.add(dependent)) queue.addLast(dependent);
            }
        }

        return new Plan(
            Set.copyOf(changedFiles),
            Set.copyOf(direct),
            Set.copyOf(transitive),
            Set.copyOf(transitive),
            edges,
            "reverse dependency closure over persisted source-backed relationships"
        );
    }
}
