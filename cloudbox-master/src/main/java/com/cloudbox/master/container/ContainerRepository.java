package com.cloudbox.master.container;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContainerRepository extends JpaRepository<ContainerInstance, UUID> {
    List<ContainerInstance> findByNodeIdAndStatus(UUID nodeId, ContainerStatus status);
}
