package com.cloudbox.agent.metrics;

public record SystemMetrics(
        double cpuFreePercent,
        long ramFreeBytes,
        long ramTotalBytes,
        long diskFreeBytes,
        long diskTotalBytes,
        Double temperatureCelsius) {
}
