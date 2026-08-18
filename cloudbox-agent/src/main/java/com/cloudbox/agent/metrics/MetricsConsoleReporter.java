package com.cloudbox.agent.metrics;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MetricsConsoleReporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsConsoleReporter.class);
    private static final double BYTES_PER_GIB = 1024.0 * 1024.0 * 1024.0;

    private final MetricsCollector collector;

    public MetricsConsoleReporter(MetricsCollector collector) {
        this.collector = collector;
    }

    @Scheduled(
            initialDelayString = "${cloudbox.agent.metrics.initial-delay:1000}",
            fixedDelayString = "${cloudbox.agent.metrics.interval:5000}")
    public void report() {
        SystemMetrics metrics = collector.collect();
        LOGGER.info(
                "Metricas locais | CPU livre: {}% | RAM livre/total: {} | Disco livre/total: {} | Temperatura: {}",
                formatPercent(metrics.cpuFreePercent()),
                formatBytes(metrics.ramFreeBytes(), metrics.ramTotalBytes()),
                formatBytes(metrics.diskFreeBytes(), metrics.diskTotalBytes()),
                formatTemperature(metrics.temperatureCelsius()));
    }

    private static String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String formatBytes(long free, long total) {
        return String.format(Locale.ROOT, "%.2f GiB / %.2f GiB", free / BYTES_PER_GIB, total / BYTES_PER_GIB);
    }

    private static String formatTemperature(Double temperature) {
        return temperature == null ? "indisponivel" : String.format(Locale.ROOT, "%.1f °C", temperature);
    }
}
