package com.cloudbox.agent.client;

public record PortSpec(
        Integer containerPort,
        Integer hostPort,
        PortProtocol protocol,
        PortExposure exposure,
        String bindAddress) {

    public PortSpec {
        protocol = protocol == null ? PortProtocol.TCP : protocol;
        exposure = exposure == null ? PortExposure.INTERNAL : exposure;
        bindAddress = bindAddress == null || bindAddress.isBlank() ? null : bindAddress.trim();
    }
}
