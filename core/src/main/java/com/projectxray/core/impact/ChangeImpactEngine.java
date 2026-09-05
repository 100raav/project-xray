package com.projectxray.core.impact;

import com.projectxray.core.model.*;

import java.util.*;

/**
 * Conservative source-backed change-impact traversal.
 *
 * It never claims runtime certainty. Results mean "reachable through the
 * analyzed relationship graph". External/unresolved edges are excluded from
 * traversal because they have no concrete target entity.
 */
public final class ChangeImpactEngine {

    public ChangeImpact analyze(String sourceId,
                                List<CodeEntity> entities,
                                List<CodeRelation> relations,
                                List<CodeEndpoint> endpoints) {
        Map<String, CodeEntity> byId = new HashMap<>();
        for (CodeEntity e : entities) byId.put(e.id(), e);

        CodeEntity source = byId.get(sourceId);
        if (source == null) {
            ChangeImpact base = ChangeImpact.empty(sourceId, sourceId);
            return withWarning(base, "Source entity was not found in the current analysis.");
        }

        Map<String, List<CodeRelation>> outgoing = new HashMap<>();
        Map<String, List<CodeRelation>> incoming = new HashMap<>();
        Set<String> realIds = byId.keySet();

        for (CodeRelation r : relations) {
            if (!realIds.contains(r.sourceId()) || !realIds.contains(r.targetId())) continue;
            outgoing.computeIfAbsent(r.sourceId(), k -> new ArrayList<>()).add(r);
            incoming.computeIfAbsent(r.targetId(), k -> new ArrayList<>()).add(r);
        }

        List<ImpactPath> callers = traverse(sourceId, incoming, true);
        List<ImpactPath> dependencies = traverse(sourceId, outgoing, false);

        Set<String> callerIds = new HashSet<>();
        for (ImpactPath p : callers) callerIds.add(p.targetId());
        Set<String> dependencyIds = new HashSet<>();
        for (ImpactPath p : dependencies) dependencyIds.add(p.targetId());

        List<String> affectedEndpoints = endpoints.stream()
            .filter(e -> callerIds.contains(e.methodId()) || callerIds.contains(e.controllerId())
                || dependencyIds.contains(e.methodId()) || dependencyIds.contains(e.controllerId()))
            .map(e -> e.httpMethod() + " " + e.route())
            .distinct().sorted().toList();

        List<String> affectedTests = entities.stream()
            .filter(e -> (callerIds.contains(e.id()) || dependencyIds.contains(e.id()))
                && isTest(e))
            .map(CodeEntity::qualifiedName).distinct().sorted().toList();

        int directCallers = (int) callers.stream().filter(p -> p.distance() == 1).count();
        int directDeps = (int) dependencies.stream().filter(p -> p.distance() == 1).count();

        List<String> warnings = new ArrayList<>();
        warnings.add("Impact is a static-graph reachability result, not a guarantee of runtime behavior.");
        if (relations.stream().anyMatch(r -> r.sourceId().equals(sourceId) && r.kind().contains("unresolved"))) {
            warnings.add("The selected entity has unresolved relationships that were excluded from traversal.");
        }

        return new ChangeImpact(
            sourceId, source.name(),
            directCallers, callers.size(),
            directDeps, dependencies.size(),
            List.copyOf(callers), List.copyOf(dependencies),
            affectedEndpoints, affectedTests, List.copyOf(warnings)
        );
    }

    private static List<ImpactPath> traverse(String start,
                                             Map<String, List<CodeRelation>> graph,
                                             boolean incoming) {
        record State(String id, List<String> path, List<String> kinds,
                     List<String> files, List<Integer> lines) {}
        ArrayDeque<State> queue = new ArrayDeque<>();
        queue.add(new State(start, List.of(start), List.of(), List.of(), List.of()));
        Set<String> visited = new HashSet<>();
        visited.add(start);
        List<ImpactPath> result = new ArrayList<>();

        while (!queue.isEmpty()) {
            State s = queue.removeFirst();
            for (CodeRelation r : graph.getOrDefault(s.id(), List.of())) {
                String next = incoming ? r.sourceId() : r.targetId();
                if (visited.contains(next)) continue;
                List<String> path = new ArrayList<>(s.path()); path.add(next);
                List<String> kinds = new ArrayList<>(s.kinds()); kinds.add(r.kind());
                List<String> files = new ArrayList<>(s.files()); if (r.evidenceFile()!=null) files.add(r.evidenceFile());
                List<Integer> lines = new ArrayList<>(s.lines()); if (r.evidenceLine()>=0) lines.add(r.evidenceLine());
                visited.add(next);
                result.add(new ImpactPath(next, incoming ? "caller" : "dependency", path.size()-1, List.copyOf(path),
                    List.copyOf(kinds), List.copyOf(files), List.copyOf(lines)));
                queue.addLast(new State(next, List.copyOf(path), List.copyOf(kinds), List.copyOf(files), List.copyOf(lines)));
            }
        }
        return List.copyOf(result);
    }

    private static boolean isTest(CodeEntity e) {
        String q = e.qualifiedName().toLowerCase(Locale.ROOT);
        String f = e.file().toLowerCase(Locale.ROOT);
        return q.contains("test") || f.contains("/test/") || f.startsWith("test/");
    }

    // Tiny immutable convenience method for the missing-entity case.
    private static ChangeImpact withWarning(ChangeImpact base, String warning) {
        List<String> w = new ArrayList<>(base.warnings()); w.add(warning);
        return new ChangeImpact(base.sourceId(), base.sourceName(),
            base.directDependents(), base.transitiveDependents(),
            base.directDependencies(), base.transitiveDependencies(),
            base.affectedCallers(), base.affectedDependencies(),
            base.affectedEndpoints(), base.affectedTests(), List.copyOf(w));
    }
}
