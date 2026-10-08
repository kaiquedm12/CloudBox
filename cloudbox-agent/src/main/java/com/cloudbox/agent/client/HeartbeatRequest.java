package com.cloudbox.agent.client;

import java.math.BigDecimal;

public record HeartbeatRequest(
        BigDecimal cpuFree,
        int ramFreeMb,
        int diskFreeMb,
        BigDecimal temperatureCelsius,
        AgentCapabilities capabilities) {

    public HeartbeatRequest(BigDecimal cpuFree, int ramFreeMb, int diskFreeMb, BigDecimal temperatureCelsius) {
        this(cpuFree, ramFreeMb, diskFreeMb, temperatureCelsius, AgentCapabilities.CURRENT);
    }
}
