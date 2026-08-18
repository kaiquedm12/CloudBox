package com.cloudbox.agent.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MetricsCollectorTest {

    @Test
    void shouldRepresentUnavailableTemperatureAsNull() {
        assertThat(MetricsCollector.availableTemperature(0.0)).isNull();
        assertThat(MetricsCollector.availableTemperature(-1.0)).isNull();
        assertThat(MetricsCollector.availableTemperature(Double.NaN)).isNull();
    }

    @Test
    void shouldKeepValidTemperature() {
        assertThat(MetricsCollector.availableTemperature(47.5)).isEqualTo(47.5);
    }

    @Test
    void shouldKeepCpuPercentageWithinValidRange() {
        assertThat(MetricsCollector.clampPercent(-5.0)).isZero();
        assertThat(MetricsCollector.clampPercent(42.5)).isEqualTo(42.5);
        assertThat(MetricsCollector.clampPercent(105.0)).isEqualTo(100.0);
    }
}
