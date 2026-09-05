package com.projectxray.core.model;

public record CodeEntity(
    String id,
    String kind,
    String name,
    String qualifiedName,
    String file,
    int line,
    String language,
    String frameworkRole
) {}
