package com.cloudbox.agent.command;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.cloudbox.agent.client.OrchestratorClient;
import com.cloudbox.agent.client.PendingCommand;
import com.cloudbox.agent.client.ContainerEndpoint;
import com.cloudbox.agent.client.PortProtocol;
import com.cloudbox.agent.docker.ContainerExecutionService;
import com.cloudbox.agent.docker.ContainerLaunchResult;
import com.cloudbox.agent.registration.AgentCredentials;
import com.cloudbox.agent.registration.NodeRegistrationService;

class PendingCommandPollerTest {

    @Test
    void shouldExecutePendingCommandAndReportRunning() {
        OrchestratorClient client = Mockito.mock(OrchestratorClient.class);
        NodeRegistrationService registration = Mockito.mock(NodeRegistrationService.class);
        ContainerExecutionService execution = Mockito.mock(ContainerExecutionService.class);
        ContainerStatusReporter reporter = Mockito.mock(ContainerStatusReporter.class);
        UUID nodeId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        AgentCredentials credentials = new AgentCredentials(nodeId, "token");
        PendingCommand command = new PendingCommand(requestId, "nginx:alpine", 1, 64, 128);
        when(registration.ensureRegistered()).thenReturn(credentials);
        when(client.pendingCommands(nodeId, "token")).thenReturn(List.of(command));
        ContainerLaunchResult result = new ContainerLaunchResult(
                "docker-123", List.of(new ContainerEndpoint(80, 32768, PortProtocol.TCP, "0.0.0.0")));
        when(execution.runContainer(
                "nginx:alpine", "cloudbox-" + requestId, requestId.toString(), 1, 64, List.of()))
                .thenReturn(result);

        new PendingCommandPoller(client, registration, execution, reporter).poll();

        verify(reporter).running(requestId, "token", result);
    }

    @Test
    void shouldReportErrorWhenExecutionFails() {
        OrchestratorClient client = Mockito.mock(OrchestratorClient.class);
        NodeRegistrationService registration = Mockito.mock(NodeRegistrationService.class);
        ContainerExecutionService execution = Mockito.mock(ContainerExecutionService.class);
        ContainerStatusReporter reporter = Mockito.mock(ContainerStatusReporter.class);
        UUID nodeId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        RuntimeException failure = new RuntimeException("image not found");
        AgentCredentials credentials = new AgentCredentials(nodeId, "token");
        PendingCommand command = new PendingCommand(requestId, "missing:latest", 1, 64, 128);
        when(registration.ensureRegistered()).thenReturn(credentials);
        when(client.pendingCommands(nodeId, "token")).thenReturn(List.of(command));
        when(execution.runContainer(
                "missing:latest", "cloudbox-" + requestId, requestId.toString(), 1, 64, List.of()))
                .thenThrow(failure);

        new PendingCommandPoller(client, registration, execution, reporter).poll();

        verify(reporter).error(requestId, "token", failure);
    }

    @Test
    void shouldRetryRunningReportWithoutStartingADuplicateContainer() {
        OrchestratorClient client = Mockito.mock(OrchestratorClient.class);
        NodeRegistrationService registration = Mockito.mock(NodeRegistrationService.class);
        ContainerExecutionService execution = Mockito.mock(ContainerExecutionService.class);
        ContainerStatusReporter reporter = Mockito.mock(ContainerStatusReporter.class);
        UUID nodeId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        PendingCommand command = new PendingCommand(requestId, "nginx:alpine", 1, 64, 128);
        when(registration.ensureRegistered()).thenReturn(new AgentCredentials(nodeId, "token"));
        when(client.pendingCommands(nodeId, "token")).thenReturn(List.of(command));
        ContainerLaunchResult result = new ContainerLaunchResult(
                "docker-123", List.of(new ContainerEndpoint(80, 32768, PortProtocol.TCP, "0.0.0.0")));
        when(execution.runContainer(
                "nginx:alpine", "cloudbox-" + requestId, requestId.toString(), 1, 64, List.of()))
                .thenReturn(result);
        org.mockito.Mockito.doThrow(new RuntimeException("master unavailable"))
                .doNothing()
                .when(reporter).running(requestId, "token", result);
        PendingCommandPoller poller = new PendingCommandPoller(client, registration, execution, reporter);

        poller.poll();
        poller.poll();

        verify(execution, times(1)).runContainer(
                "nginx:alpine", "cloudbox-" + requestId, requestId.toString(), 1, 64, List.of());
        verify(reporter, times(2)).running(requestId, "token", result);
        verify(reporter, never()).error(Mockito.any(), Mockito.any(), Mockito.any());
    }
}
