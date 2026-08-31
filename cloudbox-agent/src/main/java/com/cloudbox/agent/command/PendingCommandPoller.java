package com.cloudbox.agent.command;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.cloudbox.agent.client.OrchestratorClient;
import com.cloudbox.agent.client.PendingCommand;
import com.cloudbox.agent.docker.ContainerExecutionService;
import com.cloudbox.agent.registration.AgentCredentials;
import com.cloudbox.agent.registration.NodeRegistrationService;

@Component
public class PendingCommandPoller {

    private static final Logger LOGGER = LoggerFactory.getLogger(PendingCommandPoller.class);

    private final OrchestratorClient orchestratorClient;
    private final NodeRegistrationService registrationService;
    private final ContainerExecutionService executionService;
    private final ContainerStatusReporter statusReporter;
    private final Map<UUID, String> runningAwaitingReport = new ConcurrentHashMap<>();
    private final Map<UUID, String> actionAwaitingReport = new ConcurrentHashMap<>();

    public PendingCommandPoller(
            OrchestratorClient orchestratorClient,
            NodeRegistrationService registrationService,
            ContainerExecutionService executionService,
            ContainerStatusReporter statusReporter) {
        this.orchestratorClient = orchestratorClient;
        this.registrationService = registrationService;
        this.executionService = executionService;
        this.statusReporter = statusReporter;
    }

    @Scheduled(
            initialDelayString = "${cloudbox.agent.command-poll-initial-delay:3000}",
            fixedDelayString = "${cloudbox.agent.command-poll-interval:5000}")
    public void poll() {
        try {
            AgentCredentials credentials = registrationService.ensureRegistered();
            for (PendingCommand command : orchestratorClient.pendingCommands(
                    credentials.nodeId(), credentials.token())) {
                execute(command, credentials.token());
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Falha ao consultar comandos pendentes no orquestrador", exception);
        }
    }

    private void execute(PendingCommand command, String token) {
        String completedAction = actionAwaitingReport.get(command.containerId());
        if (completedAction != null) {
            reportCompletedAction(command.containerId(), token, completedAction);
            return;
        }
        if ("STOP".equals(command.action())) {
            executionService.stopContainer(requireDockerId(command));
            actionAwaitingReport.put(command.containerId(), "STOP");
            reportCompletedAction(command.containerId(), token, "STOP");
            return;
        }
        if ("REMOVE".equals(command.action())) {
            executionService.removeContainer(requireDockerId(command));
            actionAwaitingReport.put(command.containerId(), "REMOVE");
            reportCompletedAction(command.containerId(), token, "REMOVE");
            return;
        }
        if (!"START".equals(command.action())) {
            throw new IllegalArgumentException("Comando de container desconhecido: " + command.action());
        }

        String alreadyRunning = runningAwaitingReport.get(command.containerId());
        if (alreadyRunning != null) {
            reportRunning(command, token, alreadyRunning);
            return;
        }

        String dockerContainerId;
        try {
            LOGGER.info("Executando container solicitado pelo orquestrador id={} imagem={}",
                    command.containerId(), command.imageName());
            dockerContainerId = executionService.runContainer(
                    command.imageName(), "cloudbox-" + command.containerId(),
                    command.cpuCores(), command.memoryMb());
        } catch (RuntimeException exception) {
            LOGGER.error("Falha ao executar container id={}", command.containerId(), exception);
            try {
                statusReporter.error(command.containerId(), token, exception);
            } catch (RuntimeException reportFailure) {
                exception.addSuppressed(reportFailure);
                LOGGER.error("Falha ao reportar erro do container id={}", command.containerId(), reportFailure);
            }
            return;
        }

        runningAwaitingReport.put(command.containerId(), dockerContainerId);
        reportRunning(command, token, dockerContainerId);
    }

    private void reportCompletedAction(UUID containerId, String token, String action) {
        try {
            if ("STOP".equals(action)) {
                statusReporter.stopped(containerId, token);
            } else {
                statusReporter.removed(containerId, token);
            }
            actionAwaitingReport.remove(containerId, action);
        } catch (RuntimeException exception) {
            LOGGER.warn("Acao {} concluida para container id={}, mas o status ainda nao foi reportado; "
                    + "o agente tentara novamente", action, containerId, exception);
        }
    }

    private String requireDockerId(PendingCommand command) {
        if (command.dockerContainerId() == null || command.dockerContainerId().isBlank()) {
            throw new IllegalStateException("Comando " + command.action() + " sem dockerContainerId");
        }
        return command.dockerContainerId();
    }

    private void reportRunning(PendingCommand command, String token, String dockerContainerId) {
        try {
            statusReporter.running(command.containerId(), token, dockerContainerId);
            runningAwaitingReport.remove(command.containerId(), dockerContainerId);
            LOGGER.info("Container iniciado id={} dockerContainerId={}", command.containerId(), dockerContainerId);
        } catch (RuntimeException exception) {
            LOGGER.warn("Container id={} esta em execucao, mas o status RUNNING ainda nao foi reportado; "
                    + "o agente tentara novamente", command.containerId(), exception);
        }
    }
}
