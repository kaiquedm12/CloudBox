package com.cloudbox.agent.client;

import java.util.UUID;

public record PendingCommand(
        UUID containerId,
        String imageName,
        Integer cpuCores,
        Integer memoryMb,
        Integer diskMb) {
}
