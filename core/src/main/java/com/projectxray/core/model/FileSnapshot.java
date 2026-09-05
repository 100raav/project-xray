package com.projectxray.core.model;
public record FileSnapshot(String path,long size,long modifiedEpochMs,String sha256) {}
