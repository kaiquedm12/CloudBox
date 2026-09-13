package com.cloudbox.agent.client;

import java.util.List;

public record ContainerStatusUpdateRequest(
        String status,
        String dockerContainerId,
        String errorMessage,
        List<ContainerEndpoint> endpoints) {

    public ContainerStatusUpdateRequest {
        endpoints = endpoints == null ? null : List.copyOf(endpoints);
    }

    public ContainerStatusUpdateRequest(String status, String dockerContainerId, String errorMessage) {
        this(status, dockerContainerId, errorMessage, null);
    }
}
