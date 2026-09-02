package com.cloudbox.master.container;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cloudbox.master.common.ResourceNotFoundException;
import com.cloudbox.master.container.dto.ContainerRequest;
import com.cloudbox.master.container.dto.ContainerResponse;
import com.cloudbox.master.container.dto.ContainerStatusUpdateRequest;
import com.cloudbox.master.container.dto.PendingCommandResponse;
import com.cloudbox.master.node.Node;
import com.cloudbox.master.realtime.ClusterStatusPublisher;
import com.cloudbox.master.scheduler.SchedulerService;

@Service
public class ContainerService {

    private static final Set<ContainerStatus> AGENT_REPORTABLE_STATUSES =
            Set.of(ContainerStatus.RUNNING, ContainerStatus.ERROR, ContainerStatus.STOPPED, ContainerStatus.REMOVED);
    private static final Set<ContainerStatus> COMMAND_STATUSES =
            Set.of(ContainerStatus.PENDING, ContainerStatus.STOPPING, ContainerStatus.REMOVING);

    private final ContainerRepository containerRepository;
    private final SchedulerService schedulerService;
    private final ClusterStatusPublisher clusterStatusPublisher;

    public ContainerService(ContainerRepository containerRepository, SchedulerService schedulerService,
                            ClusterStatusPublisher clusterStatusPublisher) {
        this.containerRepository = containerRepository;
        this.schedulerService = schedulerService;
        this.clusterStatusPublisher = clusterStatusPublisher;
    }

    @Transactional
    public Optional<ContainerResponse> create(ContainerRequest request) {
        Optional<Node> node = schedulerService.schedule(
                BigDecimal.valueOf(request.cpuCores()), request.memoryMb(), request.diskMb());
        if (node.isEmpty()) {
            return Optional.empty();
        }

        ContainerInstance container = new ContainerInstance();
        container.setImageName(request.imageName());
        container.setCpuCores(request.cpuCores());
        container.setMemoryMb(request.memoryMb());
        container.setDiskMb(request.diskMb());
        container.setNodeId(node.get().getId());
        container.setStatus(ContainerStatus.PENDING);

        ContainerInstance saved = containerRepository.save(container);
        return Optional.of(toResponse(saved));
    }

    @Transactional(readOnly = true)
    public List<ContainerResponse> findAll() {
        return containerRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PendingCommandResponse> findPendingCommands(UUID nodeId) {
        return containerRepository.findByNodeIdAndStatusIn(nodeId, COMMAND_STATUSES)
                .stream()
                .map(this::toPendingCommandResponse)
                .toList();
    }

    @Transactional
    public ContainerResponse stop(UUID id) {
        ContainerInstance container = requireContainer(id);
        if (container.getStatus() != ContainerStatus.RUNNING) {
            throw new IllegalArgumentException("Somente containers em execucao podem ser parados");
        }
        return requestAction(container, ContainerStatus.STOPPING);
    }

    @Transactional
    public ContainerResponse remove(UUID id) {
        ContainerInstance container = requireContainer(id);
        if (container.getStatus() != ContainerStatus.RUNNING && container.getStatus() != ContainerStatus.STOPPED
                && container.getStatus() != ContainerStatus.ERROR && container.getStatus() != ContainerStatus.FAILED) {
            throw new IllegalArgumentException("O container nao esta em um estado que permite remocao");
        }
        if (container.getDockerContainerId() == null || container.getDockerContainerId().isBlank()) {
            ContainerStatus previousStatus = container.getStatus();
            container.setStatus(ContainerStatus.REMOVED);
            clusterStatusPublisher.publishContainerStatusChange(
                    container.getId(), previousStatus, ContainerStatus.REMOVED);
            return toResponse(container);
        }
        return requestAction(container, ContainerStatus.REMOVING);
    }

    private ContainerResponse requestAction(ContainerInstance container, ContainerStatus status) {
        ContainerStatus previousStatus = container.getStatus();
        container.setStatus(status);
        clusterStatusPublisher.publishContainerStatusChange(container.getId(), previousStatus, status);
        return toResponse(container);
    }

    private ContainerInstance requireContainer(UUID id) {
        return containerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Container não encontrado: " + id));
    }

    @Transactional
    public ContainerResponse updateStatus(UUID id, ContainerStatusUpdateRequest request) {
        ContainerInstance container = containerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Container não encontrado: " + id));

        if (!AGENT_REPORTABLE_STATUSES.contains(request.status())) {
            throw new IllegalArgumentException(
                    "Status inválido: " + request.status()
                            + ". O agente só pode reportar RUNNING, ERROR, STOPPED ou REMOVED.");
        }

        ContainerStatus previousStatus = container.getStatus();
        container.setStatus(request.status());
        if (request.dockerContainerId() != null && !request.dockerContainerId().isBlank()) {
            container.setDockerContainerId(request.dockerContainerId());
        }
        if (request.errorMessage() != null && !request.errorMessage().isBlank()) {
            container.setErrorMessage(request.errorMessage());
        }
        container.setUpdatedAt(Instant.now());
        if (previousStatus != request.status()) {
            clusterStatusPublisher.publishContainerStatusChange(container.getId(), previousStatus, request.status());
        }
        return toResponse(container);
    }

    private PendingCommandResponse toPendingCommandResponse(ContainerInstance container) {
        return new PendingCommandResponse(
                container.getId(),
                switch (container.getStatus()) {
                    case PENDING -> "START";
                    case STOPPING -> "STOP";
                    case REMOVING -> "REMOVE";
                    default -> throw new IllegalStateException("Status sem comando: " + container.getStatus());
                },
                container.getImageName(),
                container.getCpuCores(),
                container.getMemoryMb(),
                container.getDiskMb(),
                container.getDockerContainerId());
    }

    private ContainerResponse toResponse(ContainerInstance container) {
        return new ContainerResponse(
                container.getId(),
                container.getImageName(),
                container.getCpuCores(),
                container.getMemoryMb(),
                container.getDiskMb(),
                container.getStatus(),
                container.getNodeId(),
                container.getDockerContainerId(),
                container.getErrorMessage(),
                container.getCreatedAt());
    }
}
