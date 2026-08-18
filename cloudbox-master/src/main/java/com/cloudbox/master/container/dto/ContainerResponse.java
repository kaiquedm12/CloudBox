package com.cloudbox.master.container.dto;

import java.time.Instant;
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
        Instant createdAt) {
}
