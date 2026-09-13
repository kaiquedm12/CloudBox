package com.cloudbox.agent.client;

import java.util.List;
import java.util.UUID;

public record PendingCommand(
        UUID containerId,
        String action,
        String imageName,
        Integer cpuCores,
        Integer memoryMb,
        Integer diskMb,
        List<PortSpec> ports,
        String dockerContainerId) {

    public PendingCommand {
        ports = ports == null ? List.of() : List.copyOf(ports);
    }

    public PendingCommand(
            UUID containerId,
            String imageName,
            Integer cpuCores,
            Integer memoryMb,
            Integer diskMb) {
        this(containerId, "START", imageName, cpuCores, memoryMb, diskMb, List.of(), null);
    }

    public PendingCommand(
            UUID containerId,
            String imageName,
            Integer cpuCores,
            Integer memoryMb,
            Integer diskMb,
            List<PortSpec> ports) {
        this(containerId, "START", imageName, cpuCores, memoryMb, diskMb, ports, null);
    }

    public PendingCommand(
            UUID containerId,
            String action,
            String imageName,
            Integer cpuCores,
            Integer memoryMb,
            Integer diskMb,
            String dockerContainerId) {
        this(containerId, action, imageName, cpuCores, memoryMb, diskMb, List.of(), dockerContainerId);
    }
}
