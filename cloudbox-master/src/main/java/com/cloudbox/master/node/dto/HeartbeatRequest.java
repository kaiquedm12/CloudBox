package com.cloudbox.master.node.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record HeartbeatRequest(
        @NotNull @PositiveOrZero BigDecimal cpuFree,
        @NotNull @PositiveOrZero Integer ramFreeMb,
        @NotNull @PositiveOrZero Integer diskFreeMb,
        @PositiveOrZero BigDecimal temperatureCelsius) {
}