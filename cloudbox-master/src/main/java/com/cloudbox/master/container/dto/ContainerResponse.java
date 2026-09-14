package com.cloudbox.master.container.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.cloudbox.master.container.ContainerStatus;

public record ContainerResponse(
        UUID id,
        String imageName,
        Integer cpuCores,
        Integer memoryMb,
        Integer diskMb,
        ContainerStatus status,
        UUID nodeId,
        String dockerContainerId,
        String errorMessage,
        Instant createdAt,
        List<PortSpec> ports,
        List<EndpointResponse> endpoints) {

    public ContainerResponse {
        ports = ports == null ? List.of() : List.copyOf(ports);
        endpoints = endpoints == null ? List.of() : List.copyOf(endpoints);
    }

    public ContainerResponse(UUID id, String imageName, Integer cpuCores, Integer memoryMb,
                             Integer diskMb, ContainerStatus status, UUID nodeId,
                             String dockerContainerId, String errorMessage, Instant createdAt) {
        this(id, imageName, cpuCores, memoryMb, diskMb, status, nodeId,
                dockerContainerId, errorMessage, createdAt, List.of(), List.of());
    }
}
