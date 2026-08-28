package com.cloudbox.master.realtime;

import com.cloudbox.master.container.ContainerStatus;
import com.cloudbox.master.node.NodeStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class ClusterStatusPublisher {

    private final ClusterStatusWebSocketHandler webSocketHandler;

    public ClusterStatusPublisher(ClusterStatusWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    public void publishNodeStatusChange(UUID nodeId, NodeStatus previousStatus, NodeStatus currentStatus) {
        publish("NODE_STATUS_CHANGED", "NODE", nodeId, previousStatus, currentStatus);
    }

    public void publishNodeMetricsUpdated(UUID nodeId, NodeStatus currentStatus) {
        publish("NODE_METRICS_UPDATED", "NODE", nodeId, currentStatus, currentStatus);
    }

    public void publishContainerStatusChange(
            UUID containerId, ContainerStatus previousStatus, ContainerStatus currentStatus) {
        publish("CONTAINER_STATUS_CHANGED", "CONTAINER", containerId, previousStatus, currentStatus);
    }

    private void publish(String eventType, String resourceType, UUID resourceId, Enum<?> previousStatus,
                         Enum<?> currentStatus) {
        if (Objects.equals(previousStatus, currentStatus) && !"NODE_METRICS_UPDATED".equals(eventType)) {
            return;
        }

        ClusterStatusMessage message = new ClusterStatusMessage(
                eventType,
                resourceType,
                resourceId,
                previousStatus == null ? null : previousStatus.name(),
                currentStatus.name(),
                Instant.now());

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    webSocketHandler.broadcast(message);
                }
            });
        } else {
            webSocketHandler.broadcast(message);
        }
    }
}
