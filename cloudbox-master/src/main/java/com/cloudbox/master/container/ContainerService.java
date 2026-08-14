package com.cloudbox.master.container;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cloudbox.master.container.dto.ContainerRequest;
import com.cloudbox.master.container.dto.ContainerResponse;

@Service
public class ContainerService {

    private final ContainerRepository containerRepository;

    public ContainerService(ContainerRepository containerRepository) {
        this.containerRepository = containerRepository;
    }

    @Transactional
    public ContainerResponse create(ContainerRequest request) {
        ContainerInstance container = new ContainerInstance();
        container.setImageName(request.imageName());
        container.setCpuCores(request.cpuCores());
        container.setMemoryMb(request.memoryMb());
        container.setDiskMb(request.diskMb());
        container.setStatus(ContainerStatus.PENDING);

        ContainerInstance saved = containerRepository.save(container);
        return toResponse(saved);
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
                container.getCreatedAt());
    }
}
