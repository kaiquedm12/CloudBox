package com.cloudbox.agent.client;

import java.math.BigDecimal;

public record NodeRegisterRequest(
        String name,
        BigDecimal cpuTotal,
        int ramTotalMb,
        int diskTotalMb,
        String advertiseAddress,
        AgentCapabilities capabilities) {

    public NodeRegisterRequest(String name, BigDecimal cpuTotal, int ramTotalMb, int diskTotalMb, String advertiseAddress) {
        this(name, cpuTotal, ramTotalMb, diskTotalMb, advertiseAddress, AgentCapabilities.CURRENT);
    }

    public NodeRegisterRequest(String name, BigDecimal cpuTotal, int ramTotalMb, int diskTotalMb) {
        this(name, cpuTotal, ramTotalMb, diskTotalMb, null);
    }
}
