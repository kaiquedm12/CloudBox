package com.cloudbox.agent.docker;

import java.util.List;

import com.cloudbox.agent.client.ContainerEndpoint;

public record ContainerLaunchResult(
        String dockerContainerId,
        List<ContainerEndpoint> endpoints) {

    public ContainerLaunchResult {
        endpoints = endpoints == null ? List.of() : List.copyOf(endpoints);
    }
}
