package com.cloudbox.agent.command;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.cloudbox.agent.client.ContainerStatusUpdateRequest;
import com.cloudbox.agent.client.OrchestratorClient;

@Component
public class ContainerStatusReporter {

    private final OrchestratorClient orchestratorClient;

    public ContainerStatusReporter(OrchestratorClient orchestratorClient) {
        this.orchestratorClient = orchestratorClient;
    }

    public void running(UUID containerId, String token, String dockerContainerId) {
        orchestratorClient.updateContainerStatus(
                containerId, token, new ContainerStatusUpdateRequest("RUNNING", dockerContainerId, null));
    }

    public void error(UUID containerId, String token, RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            message = failure.getClass().getSimpleName();
        }
        orchestratorClient.updateContainerStatus(
                containerId, token, new ContainerStatusUpdateRequest("ERROR", null, message));
    }
}
