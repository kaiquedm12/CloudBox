package com.cloudbox.master.container;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
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
            Set.of(ContainerStatus.RUNNING, ContainerStatus.ERROR, ContainerStatus.STOPPED);

    private final ContainerRepository containerRepository;
    private final SchedulerService schedulerService;
    private final ClusterStatusPublisher clusterStatusPublisher;
    private final EndpointResolver endpointResolver;

    @Autowired
    public ContainerService(ContainerRepository containerRepository, SchedulerService schedulerService,
                            ClusterStatusPublisher clusterStatusPublisher, EndpointResolver endpointResolver) {
        this.containerRepository = containerRepository;
        this.schedulerService = schedulerService;
        this.clusterStatusPublisher = clusterStatusPublisher;
        this.endpointResolver = endpointResolver;
    }

    public ContainerService(ContainerRepository containerRepository, SchedulerService schedulerService,
                            ClusterStatusPublisher clusterStatusPublisher) {
        this(containerRepository, schedulerService, clusterStatusPublisher, new EndpointResolver());
    }

    @Transactional
    public Optional<ContainerResponse> create(ContainerRequest request) {
        Optional<Node> node = schedulerService.schedule(
                BigDecimal.valueOf(request.cpuCores()), request.memoryMb(), hasPublishedPorts(request));
        if (node.isEmpty()) {
            return Optional.empty();
        }

        ContainerInstance container = new ContainerInstance();
        container.setImageName(request.imageName());
        container.setCpuCores(request.cpuCores());
        container.setMemoryMb(request.memoryMb());
        container.setDiskMb(request.diskMb());
        container.setPorts(request.ports().stream().map(ContainerPort::from).toList());
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
        return updateStatus(id, request, null);
    }

    @Transactional
    public ContainerResponse updateStatus(UUID id, ContainerStatusUpdateRequest request, Node authorizedNode) {
        ContainerInstance container = containerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Container não encontrado: " + id));

        if (!AGENT_REPORTABLE_STATUSES.contains(request.status())) {
            throw new IllegalArgumentException(
                    "Status inválido: " + request.status() + ". O agente só pode reportar RUNNING, ERROR ou STOPPED.");
        }

        List<ContainerEndpoint> updatedEndpoints = endpointsAfterUpdate(container, request, authorizedNode);
        boolean endpointsChanged = !container.getEndpoints().equals(updatedEndpoints);
        ContainerStatus previousStatus = container.getStatus();
        container.setStatus(request.status());
        if (endpointsChanged) {
            container.setEndpoints(updatedEndpoints);
        }
        if (request.dockerContainerId() != null && !request.dockerContainerId().isBlank()) {
            container.setDockerContainerId(request.dockerContainerId());
        }
        if (request.errorMessage() != null && !request.errorMessage().isBlank()) {
            container.setErrorMessage(request.errorMessage());
        }
        container.setUpdatedAt(Instant.now());
        if (previousStatus != request.status() || endpointsChanged) {
            clusterStatusPublisher.publishContainerStatusChange(container.getId(), previousStatus, request.status());
        }
        return toResponse(container);
    }

    private List<ContainerEndpoint> endpointsAfterUpdate(ContainerInstance container,
                                                         ContainerStatusUpdateRequest request,
                                                         Node authorizedNode) {
        if (request.status() == ContainerStatus.ERROR || request.status() == ContainerStatus.STOPPED) {
            return List.of();
        }
        if (request.endpoints() == null) {
            return List.copyOf(container.getEndpoints());
        }
        return endpointResolver.resolve(container.getPorts(), request.endpoints(), authorizedNode);
    }

    private boolean hasPublishedPorts(ContainerRequest request) {
        return request.ports().stream().anyMatch(port -> port.exposure() != PortExposure.INTERNAL);
    }

    private PendingCommandResponse toPendingCommandResponse(ContainerInstance container) {
        return new PendingCommandResponse(
                container.getId(),
                container.getImageName(),
                container.getCpuCores(),
                container.getMemoryMb(),
                container.getDiskMb(),
                container.getPorts().stream().map(ContainerPort::toSpec).toList());
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
                container.getCreatedAt(),
                container.getPorts().stream().map(ContainerPort::toSpec).toList(),
                container.getEndpoints().stream()
                        .filter(endpoint -> endpoint.getResolvedAddress() != null)
                        .map(ContainerEndpoint::toResponse)
                        .toList());
    }
}
