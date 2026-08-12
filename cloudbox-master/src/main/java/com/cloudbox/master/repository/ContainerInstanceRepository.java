package com.cloudbox.master.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cloudbox.master.domain.ContainerInstance;
import com.cloudbox.master.domain.ContainerStatus;

public interface ContainerInstanceRepository extends JpaRepository<ContainerInstance, UUID> {
    List<ContainerInstance> findByNodeId(UUID nodeId);
    List<ContainerInstance> findByStatus(ContainerStatus status);
}