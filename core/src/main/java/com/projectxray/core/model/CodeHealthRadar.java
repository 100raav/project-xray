package com.projectxray.core.model;

import java.util.List;

public record CodeHealthRadar(
    double score,
    List<HealthMetric> metrics,
    List<HealthFinding> findings,
    List<String> warnings
) {
    public static CodeHealthRadar empty() {
        return new CodeHealthRadar(100.0, List.of(), List.of(), List.of());
    }
}
