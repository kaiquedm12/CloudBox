package com.cloudbox.agent.client;

import java.math.BigDecimal;

public record NodeRegisterRequest(
        String name,
        BigDecimal cpuTotal,
        int ramTotalMb,
        int diskTotalMb) {
}
