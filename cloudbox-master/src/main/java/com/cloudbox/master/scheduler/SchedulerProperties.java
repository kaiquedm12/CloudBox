package com.cloudbox.master.scheduler;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cloudbox.scheduler")
public class SchedulerProperties {

    private BigDecimal maxTemperatureCelsius = BigDecimal.valueOf(75);

    public BigDecimal getMaxTemperatureCelsius() {
        return maxTemperatureCelsius;
    }

    public void setMaxTemperatureCelsius(BigDecimal maxTemperatureCelsius) {
        this.maxTemperatureCelsius = maxTemperatureCelsius;
    }
}