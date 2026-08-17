package com.cloudbox.master.container;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cloudbox.master.container.dto.ContainerRequest;
import com.cloudbox.master.container.dto.ContainerResponse;
import com.cloudbox.master.node.Node;
import com.cloudbox.master.scheduler.SchedulerService;

@Service
public class ContainerService {

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

    private ContainerResponse toResponse(ContainerInstance container) {
        return new ContainerResponse(
                container.getId(),
                container.getImageName(),
                container.getCpuCores(),
                container.getMemoryMb(),
                container.getDiskMb(),
                container.getStatus(),
                container.getNodeId(),
                container.getCreatedAt());
    }
}