package com.cloudbox.master.container;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContainerRepository extends JpaRepository<ContainerInstance, UUID> {
}
