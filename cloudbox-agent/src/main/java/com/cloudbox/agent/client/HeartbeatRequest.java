package com.cloudbox.agent.client;

import java.math.BigDecimal;

public record HeartbeatRequest(
        BigDecimal cpuFree,
        int ramFreeMb,
        int diskFreeMb,
        BigDecimal temperatureCelsius) {
}
