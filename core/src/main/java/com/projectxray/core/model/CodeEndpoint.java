package com.projectxray.core.model;

public record CodeEndpoint(
    String id,
    String httpMethod,
    String route,
    String controllerId,
    String methodId,
    String file,
    int line
) {}
