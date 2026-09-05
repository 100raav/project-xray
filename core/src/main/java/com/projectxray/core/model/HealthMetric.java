package com.projectxray.core.model;

public record HealthMetric(
    String id,
    String name,
    String category,
    double value,
    String unit,
    String status,
    String explanation,
    String evidence
) {}
