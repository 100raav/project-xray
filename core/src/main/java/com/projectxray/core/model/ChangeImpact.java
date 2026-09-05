package com.projectxray.core.model;

import java.util.List;
import java.util.Map;

public record ChangeImpact(
    String sourceId,
    String sourceName,
    int directDependents,
    int transitiveDependents,
    int directDependencies,
    int transitiveDependencies,
    List<ImpactPath> affectedCallers,
    List<ImpactPath> affectedDependencies,
    List<String> affectedEndpoints,
    List<String> affectedTests,
    List<String> warnings
) {
    public static ChangeImpact empty(String sourceId, String sourceName) {
        return new ChangeImpact(sourceId, sourceName, 0, 0, 0, 0,
            List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
