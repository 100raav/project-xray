package com.projectxray.core.index;

import java.util.*;

/**
 * Active resolver backed by the persistent compiler-context symbol store.
 *
 * Resolution is deterministic: exact ID, exact qualified name, then
 * unambiguous simple-name lookup. Ambiguous names are reported rather than
 * guessed. Relationship traversal can then follow the persisted graph.
 */
public final class PersistentSymbolResolver {
    public record Resolution(
        String query, PersistentSymbolStore.SymbolRecord symbol,
        List<PersistentSymbolStore.SymbolRecord> candidates,
        String strategy, boolean resolved
    ) {}

    private final PersistentSymbolStore store = new PersistentSymbolStore();

    public Resolution resolve(PersistentSymbolStore.StoreDocument document, String query) {
        if (query == null || query.isBlank())
            return new Resolution(query, null, List.of(), "empty", false);

        var byId = store.byId(document);
        var exactId = byId.get(query);
        if (exactId != null)
            return new Resolution(query, exactId, List.of(exactId), "symbol-id", true);

        var byQualified = store.byQualifiedName(document);
        var qualified = byQualified.get(query);
        if (qualified != null)
            return new Resolution(query, qualified, List.of(qualified), "qualified-name", true);

        var candidates = store.bySimpleName(document).getOrDefault(query, List.of());
        if (candidates.size() == 1)
            return new Resolution(query, candidates.get(0), List.copyOf(candidates),
                "unambiguous-simple-name", true);

        return new Resolution(query, null, List.copyOf(candidates),
            candidates.isEmpty() ? "not-found" : "ambiguous-simple-name", false);
    }

    public List<PersistentSymbolStore.SymbolRecord> dependents(
        PersistentSymbolStore.StoreDocument document, String symbolId, int maxDepth) {
        if (symbolId == null || maxDepth < 1) return List.of();
        var incoming = store.incoming(document);
        var symbols = store.byId(document);
        LinkedHashSet<String> visited = new LinkedHashSet<>();
        ArrayDeque<Node> q = new ArrayDeque<>();
        q.add(new Node(symbolId, 0));
        while (!q.isEmpty()) {
            Node n=q.removeFirst();
            if (n.depth >= maxDepth) continue;
            for (var r : incoming.getOrDefault(n.id, List.of())) {
                if (visited.add(r.sourceId())) q.addLast(new Node(r.sourceId(), n.depth+1));
            }
        }
        List<PersistentSymbolStore.SymbolRecord> out=new ArrayList<>();
        for (String id:visited) {
            var s=symbols.get(id);
            if(s!=null) out.add(s);
        }
        return List.copyOf(out);
    }

    public List<PersistentSymbolStore.SymbolRecord> dependencies(
        PersistentSymbolStore.StoreDocument document, String symbolId, int maxDepth) {
        if (symbolId == null || maxDepth < 1) return List.of();
        var outgoing = store.outgoing(document);
        var symbols = store.byId(document);
        LinkedHashSet<String> visited = new LinkedHashSet<>();
        ArrayDeque<Node> q = new ArrayDeque<>();
        q.add(new Node(symbolId, 0));
        while (!q.isEmpty()) {
            Node n=q.removeFirst();
            if (n.depth >= maxDepth) continue;
            for (var r : outgoing.getOrDefault(n.id, List.of())) {
                if (visited.add(r.targetId())) q.addLast(new Node(r.targetId(), n.depth+1));
            }
        }
        List<PersistentSymbolStore.SymbolRecord> out=new ArrayList<>();
        for (String id:visited) {
            var s=symbols.get(id);
            if(s!=null) out.add(s);
        }
        return List.copyOf(out);
    }

    private record Node(String id, int depth) {}
}
