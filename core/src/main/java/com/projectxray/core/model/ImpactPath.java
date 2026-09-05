package com.projectxray.core.model;

import java.util.List;

public record ImpactPath(
    String targetId,
    String direction,
    int distance,
    List<String> path,
    List<String> relationKinds,
    List<String> evidenceFiles,
    List<Integer> evidenceLines
) {}
