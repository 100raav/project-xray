package com.projectxray.core.model;

public record DependencyGalaxyNode(
    String id,
    String kind,
    String name,
    String qualifiedName,
    String file,
    int line,
    String packageName,
    String frameworkRole,
    int inboundRelations,
    int outboundRelations
) {}
