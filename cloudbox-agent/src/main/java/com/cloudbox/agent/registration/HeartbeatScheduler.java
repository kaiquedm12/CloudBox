package com.cloudbox.agent.registration;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.cloudbox.agent.client.HeartbeatRequest;
import com.cloudbox.agent.client.OrchestratorClient;
import com.cloudbox.agent.metrics.MetricsCollector;
import com.cloudbox.agent.metrics.SystemMetrics;

@Component
public class HeartbeatScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(HeartbeatScheduler.class);

    private final OrchestratorClient orchestratorClient;
    private final NodeRegistrationService registrationService;
    private final MetricsCollector metricsCollector;

    public HeartbeatScheduler(
            OrchestratorClient orchestratorClient,
            NodeRegistrationService registrationService,
            MetricsCollector metricsCollector) {
        this.orchestratorClient = orchestratorClient;
        this.registrationService = registrationService;
        this.metricsCollector = metricsCollector;
    }

    @Scheduled(
            initialDelayString = "${cloudbox.agent.heartbeat-initial-delay:2000}",
            fixedDelayString = "${cloudbox.agent.heartbeat-interval:10000}")
    public void sendHeartbeat() {
        try {
            AgentCredentials credentials = registrationService.ensureRegistered();
            SystemMetrics metrics = metricsCollector.collect();
            BigDecimal cpuFree = BigDecimal.valueOf(
                    metricsCollector.logicalProcessorCount() * metrics.cpuFreePercent() / 100.0)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal temperature = metrics.temperatureCelsius() == null
                    ? null
                    : BigDecimal.valueOf(metrics.temperatureCelsius()).setScale(2, RoundingMode.HALF_UP);

            orchestratorClient.heartbeat(credentials.nodeId(), credentials.token(), new HeartbeatRequest(
                    cpuFree,
                    NodeRegistrationService.bytesToMb(metrics.ramFreeBytes()),
                    NodeRegistrationService.bytesToMb(metrics.diskFreeBytes()),
                    temperature));
            LOGGER.info("Heartbeat enviado para o nodeId={}", credentials.nodeId());
        } catch (RuntimeException exception) {
            LOGGER.warn("Falha ao enviar heartbeat ao orquestrador", exception);
        }
    }
}
