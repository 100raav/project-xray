package com.projectxray.core.model;

import java.util.List;

/** Repository-derived architecture graph. No synthetic nodes are permitted. */
public record ArchitectureGraph(
    String rootId,
    List<ArchitectureNode> nodes,
    List<ArchitectureEdge> edges,
    List<List<String>> cycles,
    int maxDepth
) {
    public static ArchitectureGraph empty() {
        return new ArchitectureGraph("", List.of(), List.of(), List.of(), 0);
    }
}
