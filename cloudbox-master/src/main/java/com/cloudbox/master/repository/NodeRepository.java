package com.cloudbox.master.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cloudbox.master.domain.Node;
import com.cloudbox.master.domain.NodeStatus;

public interface NodeRepository extends JpaRepository<Node, UUID> {
    Optional<Node> findByToken(String token);
    List<Node> findByStatus(NodeStatus status);
}