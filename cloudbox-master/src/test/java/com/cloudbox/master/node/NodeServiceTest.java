package com.cloudbox.master.node;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbox.master.node.dto.HeartbeatRequest;
import com.cloudbox.master.realtime.ClusterStatusPublisher;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NodeServiceTest {

    @Mock
    private NodeRepository nodeRepository;

    @Mock
    private ClusterStatusPublisher clusterStatusPublisher;

    @Test
    void publishesWhenHeartbeatChangesNodeFromOfflineToOnline() {
        UUID nodeId = UUID.randomUUID();
        Node node = node(nodeId, NodeStatus.OFFLINE);
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));

        nodeService().receiveHeartbeat(nodeId, heartbeat());

        verify(clusterStatusPublisher).publishNodeStatusChange(
                nodeId, NodeStatus.OFFLINE, NodeStatus.ONLINE);
    }

    @Test
    void publishesMetricsUpdateWhenHeartbeatKeepsNodeOnline() {
        UUID nodeId = UUID.randomUUID();
        Node node = node(nodeId, NodeStatus.ONLINE);
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));

        nodeService().receiveHeartbeat(nodeId, heartbeat());

        verify(clusterStatusPublisher, never()).publishNodeStatusChange(
                nodeId, NodeStatus.ONLINE, NodeStatus.ONLINE);
        verify(clusterStatusPublisher).publishNodeMetricsUpdated(nodeId, NodeStatus.ONLINE);
    }

    private NodeService nodeService() {
        return new NodeService(nodeRepository, clusterStatusPublisher);
    }

    private Node node(UUID id, NodeStatus status) {
        Node node = new Node();
        node.setId(id);
        node.setStatus(status);
        return node;
    }

    private HeartbeatRequest heartbeat() {
        return new HeartbeatRequest(new BigDecimal("3.5"), 4096, 10240, new BigDecimal("52"));
    }
}
