package com.cloudbox.master.container.dto;

import com.cloudbox.master.common.NetworkAddress;
import com.cloudbox.master.container.PortExposure;
import com.cloudbox.master.container.PortProtocol;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PortSpec(
        @NotNull @Min(1) @Max(65535) Integer containerPort,
        @Min(1) @Max(65535) Integer hostPort,
        PortProtocol protocol,
        PortExposure exposure,
        String bindAddress) {

    public PortSpec {
        protocol = protocol == null ? PortProtocol.TCP : protocol;
        exposure = exposure == null ? PortExposure.INTERNAL : exposure;
        bindAddress = NetworkAddress.normalizeOptional(bindAddress);
    }

    @AssertTrue(message = "protocolo e exposição da porta são incompatíveis")
    @JsonIgnore
    public boolean isProtocolCompatibleWithExposure() {
        return switch (exposure) {
            case INTERNAL -> true;
            case HTTP, TCP -> protocol == PortProtocol.TCP;
            case UDP -> protocol == PortProtocol.UDP;
        };
    }

    @AssertTrue(message = "porta INTERNAL não aceita hostPort ou bindAddress")
    @JsonIgnore
    public boolean isInternalConfigurationValid() {
        return exposure != PortExposure.INTERNAL || (hostPort == null && bindAddress == null);
    }

    @AssertTrue(message = "bindAddress deve ser um endereço IPv4 ou IPv6 literal")
    @JsonIgnore
    public boolean isBindAddressValid() {
        return bindAddress == null || NetworkAddress.isIpLiteral(bindAddress);
    }
}
