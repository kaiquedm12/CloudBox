package com.cloudbox.master.node;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NodeRepository extends JpaRepository<Node, UUID> {
    Optional<Node> findByToken(String token);
    List<Node> findByStatus(NodeStatus status);
    List<Node> findByStatusAndLastHeartbeatBefore(NodeStatus status, Instant threshold);
}