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
import com.cloudbox.master.scheduler.SchedulerService;

@Service
public class ContainerService {

    private static final Set<ContainerStatus> AGENT_REPORTABLE_STATUSES =
            Set.of(ContainerStatus.RUNNING, ContainerStatus.ERROR, ContainerStatus.STOPPED);

    private final ContainerRepository containerRepository;
    private final SchedulerService schedulerService;

    public ContainerService(ContainerRepository containerRepository, SchedulerService schedulerService) {
        this.containerRepository = containerRepository;
        this.schedulerService = schedulerService;
    }

    @Transactional
    public Optional<ContainerResponse> create(ContainerRequest request) {
        Optional<Node> node = schedulerService.schedule(
                BigDecimal.valueOf(request.cpuCores()), request.memoryMb());
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
        return containerRepository.findByNodeIdAndStatus(nodeId, ContainerStatus.PENDING)
                .stream()
                .map(this::toPendingCommandResponse)
                .toList();
    }

    @Transactional
    public ContainerResponse updateStatus(UUID id, ContainerStatusUpdateRequest request) {
        ContainerInstance container = containerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Container não encontrado: " + id));

        if (!AGENT_REPORTABLE_STATUSES.contains(request.status())) {
            throw new IllegalArgumentException(
                    "Status inválido: " + request.status() + ". O agente só pode reportar RUNNING, ERROR ou STOPPED.");
        }

        container.setStatus(request.status());
        if (request.dockerContainerId() != null && !request.dockerContainerId().isBlank()) {
            container.setDockerContainerId(request.dockerContainerId());
        }
        if (request.errorMessage() != null && !request.errorMessage().isBlank()) {
            container.setErrorMessage(request.errorMessage());
        }
        container.setUpdatedAt(Instant.now());
        return toResponse(container);
    }

    private PendingCommandResponse toPendingCommandResponse(ContainerInstance container) {
        return new PendingCommandResponse(
                container.getId(),
                container.getImageName(),
                container.getCpuCores(),
                container.getMemoryMb(),
                container.getDiskMb());
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