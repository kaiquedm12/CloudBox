package com.cloudbox.agent.client;

public record ContainerStatusUpdateRequest(
        String status,
        String dockerContainerId,
        String errorMessage) {
}
