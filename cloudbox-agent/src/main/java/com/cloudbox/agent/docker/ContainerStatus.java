package com.cloudbox.agent.docker;

public record ContainerStatus(
        String containerId,
        String status,
        boolean running,
        Long exitCode) {
}
