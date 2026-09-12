package com.cloudbox.master.node.dto;

import java.math.BigDecimal;
import com.cloudbox.master.common.NetworkAddress;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record NodeRegisterRequest(
        @NotBlank String name,
        @NotNull @PositiveOrZero BigDecimal cpuTotal,
        @NotNull @PositiveOrZero Integer ramTotalMb,
        @NotNull @PositiveOrZero Integer diskTotalMb,
        String advertiseAddress) {

    public NodeRegisterRequest {
        advertiseAddress = NetworkAddress.normalizeOptional(advertiseAddress);
    }

    public NodeRegisterRequest(String name, BigDecimal cpuTotal, Integer ramTotalMb, Integer diskTotalMb) {
        this(name, cpuTotal, ramTotalMb, diskTotalMb, null);
    }

    @AssertTrue(message = "advertiseAddress deve ser hostname DNS ou IP literal, sem esquema, caminho ou porta")
    @JsonIgnore
    public boolean isAdvertiseAddressValid() {
        return NetworkAddress.isValidAdvertiseAddress(advertiseAddress);
    }
}
