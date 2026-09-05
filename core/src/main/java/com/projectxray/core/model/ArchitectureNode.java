package com.projectxray.core.model;

/** A real architectural node derived from the analyzed repository. */
public record ArchitectureNode(
    String id,
    String kind,
    String name,
    String path,
    int entityCount,
    int inboundRelations,
    int outboundRelations,
    String layer
) {}
