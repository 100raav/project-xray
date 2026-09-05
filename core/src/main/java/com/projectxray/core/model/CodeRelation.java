package com.projectxray.core.model;

/** A relationship discovered from repository evidence. */
public record CodeRelation(
    String sourceId,
    String targetId,
    String kind,
    String evidenceFile,
    int evidenceLine,
    String evidence
) {
    public CodeRelation(String sourceId, String targetId, String kind) {
        this(sourceId, targetId, kind, null, -1, null);
    }
}
