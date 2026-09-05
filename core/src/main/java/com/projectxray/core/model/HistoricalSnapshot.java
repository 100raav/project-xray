package com.projectxray.core.model;

import java.util.List;

public record HistoricalSnapshot(
    String commitHash,
    String subject,
    String analyzedAt,
    int filesScanned,
    int entities,
    int relations,
    int endpoints,
    int packages,
    int cycles,
    List<String> warnings
) {}
