package com.cloudbox.agent.registration;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.cloudbox.agent.client.AgentClientProperties;
import com.cloudbox.agent.client.NodeRegisterRequest;
import com.cloudbox.agent.client.NodeRegisterResponse;
import com.cloudbox.agent.client.OrchestratorClient;
import com.cloudbox.agent.metrics.MetricsCollector;
import com.cloudbox.agent.metrics.SystemMetrics;

@Service
public class NodeRegistrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NodeRegistrationService.class);
    private static final long BYTES_PER_MB = 1024L * 1024L;

    private final OrchestratorClient orchestratorClient;
    private final AgentTokenStorage tokenStorage;
    private final MetricsCollector metricsCollector;
    private final AgentClientProperties properties;
    private volatile AgentCredentials credentials;

    public NodeRegistrationService(
            OrchestratorClient orchestratorClient,
            AgentTokenStorage tokenStorage,
            MetricsCollector metricsCollector,
            AgentClientProperties properties) {
        this.orchestratorClient = orchestratorClient;
        this.tokenStorage = tokenStorage;
        this.metricsCollector = metricsCollector;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerOnStartup() {
        try {
            register();
        } catch (RuntimeException exception) {
            LOGGER.warn("Nao foi possivel registrar o agente; uma nova tentativa sera feita no proximo heartbeat", exception);
        }
    }

    public synchronized AgentCredentials ensureRegistered() {
        return credentials != null ? credentials : register();
    }

    private AgentCredentials register() {
        SystemMetrics metrics = metricsCollector.collect();
        NodeRegisterResponse response = orchestratorClient.register(new NodeRegisterRequest(
                properties.getName(),
                BigDecimal.valueOf(metricsCollector.logicalProcessorCount()),
                bytesToMb(metrics.ramTotalBytes()),
                bytesToMb(metrics.diskTotalBytes())));
        if (response == null || response.id() == null || response.token() == null || response.token().isBlank()) {
            throw new IllegalStateException("O orquestrador retornou um registro incompleto");
        }

        AgentCredentials registered = new AgentCredentials(response.id(), response.token());
        tokenStorage.save(registered);
        credentials = registered;
        LOGGER.info("Agente registrado no orquestrador com nodeId={}", registered.nodeId());
        return registered;
    }

    static int bytesToMb(long bytes) {
        return Math.toIntExact(bytes / BYTES_PER_MB);
    }
}
