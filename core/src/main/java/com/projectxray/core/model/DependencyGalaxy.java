package com.projectxray.core.model;

import java.util.List;

public record DependencyGalaxy(
    String rootId,
    List<DependencyGalaxyNode> nodes,
    List<DependencyGalaxyEdge> edges,
    List<List<String>> cycles,
    int maxDepth
) {
    public static DependencyGalaxy empty() {
        return new DependencyGalaxy("", List.of(), List.of(), List.of(), 0);
    }
}
