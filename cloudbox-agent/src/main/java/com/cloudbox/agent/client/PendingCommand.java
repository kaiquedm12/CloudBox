package com.cloudbox.agent.client;

import java.util.UUID;

public record PendingCommand(
        UUID containerId,
        String action,
        String imageName,
        Integer cpuCores,
        Integer memoryMb,
        Integer diskMb,
        String dockerContainerId) {
}
