package com.cloudbox.agent.client;

import java.util.List;
import java.util.UUID;

public record PendingCommand(
        UUID containerId,
        String imageName,
        Integer cpuCores,
        Integer memoryMb,
        Integer diskMb,
        List<PortSpec> ports) {

    public PendingCommand {
        ports = ports == null ? List.of() : List.copyOf(ports);
    }

    public PendingCommand(
            UUID containerId,
            String imageName,
            Integer cpuCores,
            Integer memoryMb,
            Integer diskMb) {
        this(containerId, imageName, cpuCores, memoryMb, diskMb, List.of());
    }
}
