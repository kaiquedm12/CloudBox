package com.cloudbox.master.node.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record NodeRegisterRequest(
        @NotBlank String name,
        @NotNull @PositiveOrZero BigDecimal cpuTotal,
        @NotNull @PositiveOrZero Integer ramTotalMb,
        @NotNull @PositiveOrZero Integer diskTotalMb) {
}