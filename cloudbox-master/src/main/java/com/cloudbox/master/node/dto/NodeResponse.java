package com.cloudbox.master.node.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.cloudbox.master.node.NodeStatus;

public record NodeResponse(
        UUID id,
        String name,
        NodeStatus status,
        BigDecimal cpuTotal,
        BigDecimal cpuFree,
        Integer ramTotalMb,
        Integer ramFreeMb,
        Integer diskTotalMb,
        Integer diskFreeMb,
        BigDecimal temperatureCelsius,
        Instant lastHeartbeat) {
}