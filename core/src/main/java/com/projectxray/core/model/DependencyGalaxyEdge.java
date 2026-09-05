package com.projectxray.core.model;

public record DependencyGalaxyEdge(
    String sourceId,
    String targetId,
    String kind,
    int evidenceCount
) {}
