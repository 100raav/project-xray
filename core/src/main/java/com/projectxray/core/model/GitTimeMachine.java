package com.projectxray.core.model;

import java.util.List;

public record GitTimeMachine(
    boolean repository,
    List<GitCommit> commits,
    List<HistoricalSnapshot> snapshots,
    TimeMachineDiff currentDiff,
    List<String> warnings
) {
    public static GitTimeMachine empty() {
        return new GitTimeMachine(false, List.of(), List.of(), TimeMachineDiff.empty("", ""), List.of());
    }
}
