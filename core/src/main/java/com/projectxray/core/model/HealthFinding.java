package com.projectxray.core.model;

public record HealthFinding(
    String severity,
    String category,
    String title,
    String entityId,
    String file,
    int line,
    String explanation,
    String evidence
) {}
