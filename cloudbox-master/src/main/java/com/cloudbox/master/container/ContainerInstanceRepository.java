package com.cloudbox.master.container;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContainerInstanceRepository extends JpaRepository<ContainerInstance, UUID> {
    List<ContainerInstance> findByNodeId(UUID nodeId);
    List<ContainerInstance> findByStatus(ContainerStatus status);
}