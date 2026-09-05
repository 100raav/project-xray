package com.projectxray.core.galaxy;

import com.projectxray.core.model.*;

import java.util.*;

/**
 * Dependency Galaxy is a source-backed graph. It never creates placeholder
 * classes/packages. Nodes are real analyzed classes/interfaces/methods and
 * edges are aggregated only from relations whose endpoints exist in the model.
 */
public final class DependencyGalaxyBuilder {

    public DependencyGalaxy build(String project, List<CodeEntity> entities, List<CodeRelation> relations) {
        Map<String, CodeEntity> byId = new HashMap<>();
        for (CodeEntity e : entities) byId.put(e.id(), e);

        Set<String> realNodeIds = new HashSet<>();
        List<DependencyGalaxyNode> nodes = new ArrayList<>();

        for (CodeEntity e : entities) {
            if (!isVisualEntity(e)) continue;
            realNodeIds.add(e.id());
            nodes.add(new DependencyGalaxyNode(
                e.id(), e.kind(), e.name(), e.qualifiedName(), e.file(), e.line(),
                packageName(e.qualifiedName()), e.frameworkRole(), 0, 0
            ));
        }

        Map<EdgeKey, Integer> counts = new TreeMap<>(
            Comparator.comparing((EdgeKey k) -> k.source)
                .thenComparing(k -> k.target)
                .thenComparing(k -> k.kind)
        );
        Map<String,Integer> in = new HashMap<>(), out = new HashMap<>();

        for (CodeRelation r : relations) {
            if (!realNodeIds.contains(r.sourceId()) || !realNodeIds.contains(r.targetId())) continue;
            EdgeKey k = new EdgeKey(r.sourceId(), r.targetId(), r.kind());
            counts.merge(k, 1, Integer::sum);
            out.merge(r.sourceId(), 1, Integer::sum);
            in.merge(r.targetId(), 1, Integer::sum);
        }

        List<DependencyGalaxyEdge> edges = new ArrayList<>();
        for (var e : counts.entrySet()) {
            edges.add(new DependencyGalaxyEdge(
                e.getKey().source, e.getKey().target, e.getKey().kind, e.getValue()
            ));
        }

        List<DependencyGalaxyNode> finalNodes = nodes.stream()
            .map(n -> new DependencyGalaxyNode(
                n.id(), n.kind(), n.name(), n.qualifiedName(), n.file(), n.line(),
                n.packageName(), n.frameworkRole(),
                in.getOrDefault(n.id(), 0), out.getOrDefault(n.id(), 0)
            ))
            .toList();

        List<List<String>> cycles = findCycles(realNodeIds, edges);
        int depth = computeMaxDepth(realNodeIds, edges);
        return new DependencyGalaxy(
            "galaxy:" + project,
            List.copyOf(finalNodes),
            List.copyOf(edges),
            List.copyOf(cycles),
            depth
        );
    }

    private static boolean isVisualEntity(CodeEntity e) {
        return e.kind().equals("class")
            || e.kind().equals("interface")
            || e.kind().equals("method");
    }

    private static String packageName(String qn) {
        if (qn == null || qn.isBlank()) return "";
        int hash = qn.indexOf('#');
        String typeName = hash >= 0 ? qn.substring(0, hash) : qn;
        int dot = typeName.lastIndexOf('.');
        return dot < 0 ? "" : typeName.substring(0, dot);
    }

    private record EdgeKey(String source, String target, String kind) {}

    private static List<List<String>> findCycles(Set<String> nodes, List<DependencyGalaxyEdge> edges) {
        Map<String,List<String>> graph = new HashMap<>();
        for (String n : nodes) graph.put(n, new ArrayList<>());
        for (DependencyGalaxyEdge e : edges) graph.get(e.sourceId()).add(e.targetId());

        List<List<String>> cycles = new ArrayList<>();
        Set<String> canonical = new HashSet<>();
        for (String start : graph.keySet()) {
            dfs(start, start, graph, new ArrayDeque<>(), new HashSet<>(), cycles, canonical);
        }
        return cycles.stream().limit(100).toList();
    }

    private static void dfs(String start, String current, Map<String,List<String>> graph,
                             ArrayDeque<String> path, Set<String> visiting,
                             List<List<String>> cycles, Set<String> canonical) {
        path.addLast(current);
        visiting.add(current);
        for (String next : graph.getOrDefault(current, List.of())) {
            if (next.equals(start) && path.size() > 1) {
                List<String> cycle = new ArrayList<>(path);
                List<String> normalized = new ArrayList<>(cycle);
                normalized.sort(String::compareTo);
                String key = String.join("|", normalized);
                if (canonical.add(key)) cycles.add(List.copyOf(cycle));
            } else if (!visiting.contains(next) && path.size() < 24) {
                dfs(start, next, graph, path, visiting, cycles, canonical);
            }
        }
        visiting.remove(current);
        path.removeLast();
    }

    private static int computeMaxDepth(Set<String> nodes, List<DependencyGalaxyEdge> edges) {
        Map<String,List<String>> g = new HashMap<>();
        for (String n : nodes) g.put(n, new ArrayList<>());
        for (DependencyGalaxyEdge e : edges) g.get(e.sourceId()).add(e.targetId());

        int max = 0;
        for (String start : nodes) {
            ArrayDeque<String> q = new ArrayDeque<>();
            Map<String,Integer> dist = new HashMap<>();
            q.add(start); dist.put(start, 0);
            while (!q.isEmpty()) {
                String n = q.removeFirst();
                int d = dist.get(n);
                max = Math.max(max, d);
                for (String next : g.getOrDefault(n, List.of())) {
                    if (!dist.containsKey(next) && d < 100) {
                        dist.put(next, d + 1);
                        q.addLast(next);
                    }
                }
            }
        }
        return max;
    }
}
