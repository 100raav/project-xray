package com.projectxray.core.model;

public record ScanDiagnostics(
    boolean complete,
    String strategy,
    int javaFilesDiscovered,
    int javaFilesAnalyzed,
    long sourceBytesAnalyzed,
    String limitation
) {
    public static ScanDiagnostics complete(int files, long bytes) {
        return new ScanDiagnostics(true, "repository-wide Java semantic scan", files, files, bytes, "");
    }
}
