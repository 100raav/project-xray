package com.projectxray.core.architecture;

import com.projectxray.core.model.*;

import java.util.*;

/**
 * Builds an architecture graph exclusively from entities and relations found in
 * the current analysis. Package nodes are created only when real source types
 * exist in that package. Edges are aggregates of concrete source relations.
 */
public final class ArchitectureGraphBuilder {
    public ArchitectureGraph build(String project, List<CodeEntity> entities, List<CodeRelation> relations) {
        Map<String, CodeEntity> byId = new HashMap<>();
        for (CodeEntity e : entities) byId.put(e.id(), e);

        Map<String, Set<String>> packageTypes = new TreeMap<>();
        Map<String, CodeEntity> packageRepresentatives = new HashMap<>();

        for (CodeEntity e : entities) {
            if (!isType(e)) continue;

            String pkg = packageName(e.qualifiedName());

            packageTypes.computeIfAbsent(pkg, k -> new TreeSet<>()).add(e.id());

            CodeEntity current = packageRepresentatives.get(pkg);
            if (current == null
                    || e.file().compareTo(current.file()) < 0
                    || (e.file().equals(current.file()) && e.line() < current.line())) {
                packageRepresentatives.put(pkg, e);
            }
        }

        List<ArchitectureNode> nodes = new ArrayList<>();
        String rootId = "project:" + project;
        nodes.add(new ArchitectureNode(rootId, "project", project, "", entities.size(), 0, 0, "system"));

        Map<String,String> packageId = new HashMap<>();
        for (Map.Entry<String,Set<String>> entry : packageTypes.entrySet()) {
            String pkg = entry.getKey();
            String id = "package:" + (pkg.isBlank() ? "<default>" : pkg);
            packageId.put(pkg, id);
            CodeEntity representative = packageRepresentatives.get(pkg);
            String navigationPath = representative == null ? "" : representative.file();

            nodes.add(new ArchitectureNode(
                id,
                "package",
                pkg.isBlank() ? "<default>" : pkg,
                navigationPath,
                entry.getValue().size(),
                0,
                0,
                packageLayer(pkg)
            ));
        }

        // Package dependency edges are created only from actual relations between real source entities.
        Map<EdgeKey,Integer> counts = new TreeMap<>(Comparator.comparing((EdgeKey k) -> k.source)
            .thenComparing(k -> k.target).thenComparing(k -> k.kind));
        Map<String,Integer> inbound = new HashMap<>(), outbound = new HashMap<>();
        for (CodeRelation r : relations) {
            CodeEntity a = byId.get(r.sourceId()), b = byId.get(r.targetId());
            if (a == null || b == null || !isTypeOrMethod(a) || !isTypeOrMethod(b)) continue;
            String pa = packageName(a.qualifiedName()), pb = packageName(b.qualifiedName());
            if (pa.equals(pb)) continue;
            String sa = packageId.get(pa), sb = packageId.get(pb);
            if (sa == null || sb == null || sa.equals(sb)) continue;
            EdgeKey key = new EdgeKey(sa, sb, normalizeKind(r.kind()));
            counts.merge(key, 1, Integer::sum);
            outbound.merge(sa, 1, Integer::sum);
            inbound.merge(sb, 1, Integer::sum);
        }

        List<ArchitectureEdge> edges = new ArrayList<>();
        for (Map.Entry<EdgeKey,Integer> e : counts.entrySet()) {
            edges.add(new ArchitectureEdge(e.getKey().source, e.getKey().target, e.getKey().kind, e.getValue()));
        }

        // Add project->package containment as a structural relationship; packages remain real nodes.
        for (ArchitectureNode n : new ArrayList<>(nodes)) {
            if ("package".equals(n.kind())) edges.add(new ArchitectureEdge(rootId, n.id(), "contains", n.entityCount()));
        }

        // Rebuild nodes with actual graph degrees.
        List<ArchitectureNode> finalNodes = new ArrayList<>();
        for (ArchitectureNode n : nodes) {
            if (n.kind().equals("package")) {
                finalNodes.add(new ArchitectureNode(n.id(), n.kind(), n.name(), n.path(), n.entityCount(),
                    inbound.getOrDefault(n.id(),0), outbound.getOrDefault(n.id(),0), n.layer()));
            } else {
                finalNodes.add(n);
            }
        }

        List<List<String>> cycles = findCycles(packageId.values(), edges);
        return new ArchitectureGraph(rootId, List.copyOf(finalNodes), List.copyOf(edges), List.copyOf(cycles), 2);
    }

    private static boolean isType(CodeEntity e) {
        return e.kind().equals("class") || e.kind().equals("interface");
    }
    private static boolean isTypeOrMethod(CodeEntity e) {
        return isType(e) || e.kind().equals("method");
    }
    private static String packageName(String qn) {
        if (qn == null || qn.isBlank()) return "";
        int hash = qn.indexOf('#');
        String typeName = hash >= 0 ? qn.substring(0, hash) : qn;
        int dot = typeName.lastIndexOf('.');
        return dot < 0 ? "" : typeName.substring(0, dot);
    }
    private static String packageLayer(String pkg) {
        String x = pkg.toLowerCase(Locale.ROOT);
        if (x.contains("controller") || x.contains("api") || x.contains("web")) return "presentation";
        if (x.contains("service") || x.contains("usecase") || x.contains("application")) return "application";
        if (x.contains("repository") || x.contains("persistence") || x.contains("data")) return "data";
        if (x.contains("domain") || x.contains("model") || x.contains("entity")) return "domain";
        return "other";
    }
    private static String normalizeKind(String kind) {
        return kind == null ? "relation" : kind;
    }

    private record EdgeKey(String source, String target, String kind) {}

    private static List<List<String>> findCycles(Collection<String> nodes, List<ArchitectureEdge> edges) {
        Map<String,List<String>> g = new HashMap<>();
        for (String n : nodes) g.put(n,new ArrayList<>());
        for (ArchitectureEdge e : edges) if (!e.kind().equals("contains") && g.containsKey(e.sourceId()) && g.containsKey(e.targetId()))
            g.get(e.sourceId()).add(e.targetId());
        List<List<String>> cycles = new ArrayList<>();
        Set<String> seenCanonical = new HashSet<>();
        for (String start : g.keySet()) {
            ArrayDeque<String> path = new ArrayDeque<>();
            Set<String> visiting = new HashSet<>();
            dfs(start,start,g,path,visiting,cycles,seenCanonical);
        }
        return cycles.stream().limit(100).toList();
    }
    private static void dfs(String start,String cur,Map<String,List<String>> g,ArrayDeque<String> path,
                            Set<String> visiting,List<List<String>> cycles,Set<String> seen) {
        path.addLast(cur); visiting.add(cur);
        for(String next:g.getOrDefault(cur,List.of())) {
            if(next.equals(start) && path.size()>1){
                List<String> c=new ArrayList<>(path); c.sort(String::compareTo);
                String key=String.join("|",c);
                if(seen.add(key)) cycles.add(List.copyOf(path));
            } else if(!visiting.contains(next) && path.size()<20){
                dfs(start,next,g,path,visiting,cycles,seen);
            }
        }
        visiting.remove(cur); path.removeLast();
    }
}
