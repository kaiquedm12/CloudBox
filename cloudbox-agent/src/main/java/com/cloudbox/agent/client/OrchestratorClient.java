package com.cloudbox.agent.client;

import java.util.List;
import java.util.UUID;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OrchestratorClient {

    private final RestClient restClient;

    public OrchestratorClient(RestClient.Builder builder, AgentClientProperties properties) {
        this.restClient = builder.baseUrl(properties.getMasterUrl()).build();
    }

    public NodeRegisterResponse register(NodeRegisterRequest request) {
        return restClient.post()
                .uri("/api/nodes/register")
                .body(request)
                .retrieve()
                .body(NodeRegisterResponse.class);
    }

    public void heartbeat(UUID nodeId, String token, HeartbeatRequest request) {
        restClient.post()
                .uri("/api/nodes/{id}/heartbeat", nodeId)
                .headers(headers -> headers.setBearerAuth(token))
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public List<PendingCommand> pendingCommands(UUID nodeId, String token) {
        List<PendingCommand> commands = restClient.get()
                .uri("/api/nodes/{id}/pending-commands", nodeId)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return commands == null ? List.of() : commands;
    }

    public void updateContainerStatus(UUID containerId, String token, ContainerStatusUpdateRequest request) {
        restClient.post()
                .uri("/api/containers/{id}/status", containerId)
                .headers(headers -> headers.setBearerAuth(token))
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }
}
