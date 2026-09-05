package com.projectxray.core.model;

/** An aggregated architectural dependency derived from concrete code relations. */
public record ArchitectureEdge(
    String sourceId,
    String targetId,
    String kind,
    int evidenceCount
) {}
