package com.projectxray.core.model;

import java.util.List;

public record TimeMachineDiff(
    String fromCommit,
    String toCommit,
    List<String> addedEntities,
    List<String> removedEntities,
    List<String> changedFiles,
    List<String> addedPackages,
    List<String> removedPackages,
    int relationDelta,
    int endpointDelta,
    int cycleDelta
) {
    public static TimeMachineDiff empty(String fromCommit, String toCommit) {
        return new TimeMachineDiff(fromCommit, toCommit, List.of(), List.of(), List.of(),
            List.of(), List.of(), 0, 0, 0);
    }
}
