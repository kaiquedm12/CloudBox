package com.cloudbox.agent.client;

public record ContainerEndpoint(
        int containerPort,
        int hostPort,
        PortProtocol protocol,
        String address) {
}
