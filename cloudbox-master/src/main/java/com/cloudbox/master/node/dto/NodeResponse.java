package com.cloudbox.master.node.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.cloudbox.master.node.NodeStatus;

public record NodeResponse(
        UUID id,
        String name,
        String advertiseAddress,
        NodeStatus status,
        BigDecimal cpuTotal,
        BigDecimal cpuFree,
        Integer ramTotalMb,
        Integer ramFreeMb,
        Integer diskTotalMb,
        Integer diskFreeMb,
        BigDecimal temperatureCelsius,
        Instant lastHeartbeat) {

    public NodeResponse(UUID id, String name, NodeStatus status, BigDecimal cpuTotal, BigDecimal cpuFree,
                        Integer ramTotalMb, Integer ramFreeMb, Integer diskTotalMb, Integer diskFreeMb,
                        BigDecimal temperatureCelsius, Instant lastHeartbeat) {
        this(id, name, null, status, cpuTotal, cpuFree, ramTotalMb, ramFreeMb,
                diskTotalMb, diskFreeMb, temperatureCelsius, lastHeartbeat);
    }
}
