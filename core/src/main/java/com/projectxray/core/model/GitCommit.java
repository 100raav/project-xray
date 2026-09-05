package com.projectxray.core.model;

public record GitCommit(
    String hash,
    String shortHash,
    String author,
    String authoredAt,
    String subject,
    int changedFiles
) {}
