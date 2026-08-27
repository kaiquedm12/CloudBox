package com.cloudbox.master.node;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudbox.master.realtime.ClusterStatusPublisher;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NodeHeartbeatMonitorTest {

    @Mock
    private NodeRepository nodeRepository;

    @Mock
    private ClusterStatusPublisher clusterStatusPublisher;

    @Test
    void publishesWhenStaleOnlineNodeBecomesOffline() {
        UUID nodeId = UUID.randomUUID();
        Node staleNode = new Node();
        staleNode.setId(nodeId);
        staleNode.setStatus(NodeStatus.ONLINE);
        staleNode.setLastHeartbeat(Instant.now().minusSeconds(60));
        when(nodeRepository.findByStatusAndLastHeartbeatBefore(any(), any()))
                .thenReturn(List.of(staleNode));

        HeartbeatProperties properties = new HeartbeatProperties();
        properties.setTimeoutSeconds(30);
        NodeHeartbeatMonitor monitor = new NodeHeartbeatMonitor(
                nodeRepository, properties, clusterStatusPublisher);

        monitor.markStaleNodesOffline();

        verify(nodeRepository).saveAll(List.of(staleNode));
        verify(clusterStatusPublisher).publishNodeStatusChange(
                nodeId, NodeStatus.ONLINE, NodeStatus.OFFLINE);
    }
}
