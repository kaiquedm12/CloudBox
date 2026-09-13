package com.cloudbox.master.container.dto;

import com.cloudbox.master.common.NetworkAddress;
import com.cloudbox.master.container.PortProtocol;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ObservedEndpointRequest(
        @NotNull @Min(1) @Max(65535) Integer containerPort,
        @NotNull @Min(1) @Max(65535) Integer hostPort,
        @NotNull PortProtocol protocol,
        String address) {

    public ObservedEndpointRequest {
        address = NetworkAddress.normalizeOptional(address);
    }

    @AssertTrue(message = "address deve ser um endereço IPv4 ou IPv6 literal")
    @JsonIgnore
    public boolean isAddressValid() {
        return address == null || NetworkAddress.isIpLiteral(address);
    }
}
