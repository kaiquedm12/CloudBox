package com.cloudbox.master.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.cloudbox.master.node.Node;
import com.cloudbox.master.node.NodeRepository;
import com.cloudbox.master.node.NodeStatus;

@ExtendWith(MockitoExtension.class)
class SchedulerServiceTest {

    @Mock
    private NodeRepository nodeRepository;

    private SchedulerService schedulerService() {
        return new SchedulerService(nodeRepository, new NodeCandidateFilter(new SchedulerProperties()), new NodeScoringStrategy());
    }

    private Node node(UUID id, NodeStatus status, BigDecimal cpuTotal, BigDecimal cpuFree,
                      Integer ramTotalMb, Integer ramFreeMb, BigDecimal temperature) {
        Node node = new Node();
        node.setId(id);
        node.setStatus(status);
        node.setCpuTotal(cpuTotal);
        node.setCpuFree(cpuFree);
        node.setRamTotalMb(ramTotalMb);
        node.setRamFreeMb(ramFreeMb);
        node.setTemperatureCelsius(temperature);
        return node;
    }

    @Test
    void schedulesOnTheNodeWithMostRemainingResources() {
        Node small = node(UUID.randomUUID(), NodeStatus.ONLINE, new BigDecimal("4.00"), new BigDecimal("4.00"), 8192, 8192, null);
        Node big = node(UUID.randomUUID(), NodeStatus.ONLINE, new BigDecimal("8.00"), new BigDecimal("8.00"), 16384, 16384, null);
        Node tight = node(UUID.randomUUID(), NodeStatus.ONLINE, new BigDecimal("2.00"), new BigDecimal("2.00"), 4096, 2048, null);
        when(nodeRepository.findByStatus(NodeStatus.ONLINE)).thenReturn(List.of(small, big, tight));

        Optional<Node> result = schedulerService().schedule(new BigDecimal("1.00"), 1024);

        assertThat(result).hasValue(big);
    }

    @Test
    void returnsEmptyWhenNoNodeHasEnoughResources() {
        Node lowCpu = node(UUID.randomUUID(), NodeStatus.ONLINE, new BigDecimal("4.00"), new BigDecimal("1.00"), 8192, 8192, null);
        Node lowRam = node(UUID.randomUUID(), NodeStatus.ONLINE, new BigDecimal("8.00"), new BigDecimal("8.00"), 16384, 512, null);
        when(nodeRepository.findByStatus(NodeStatus.ONLINE)).thenReturn(List.of(lowCpu, lowRam));

        Optional<Node> result = schedulerService().schedule(new BigDecimal("2.00"), 2048);

        assertThat(result).isEmpty();
    }

    @Test
    void doesNotScheduleOnOfflineNodeEvenWithResources() {
        Node offline = node(UUID.randomUUID(), NodeStatus.OFFLINE, new BigDecimal("8.00"), new BigDecimal("8.00"), 16384, 16384, null);
        when(nodeRepository.findByStatus(NodeStatus.ONLINE)).thenReturn(List.of(offline));

        Optional<Node> result = schedulerService().schedule(new BigDecimal("1.00"), 1024);

        assertThat(result).isEmpty();
    }

    @Test
    void doesNotScheduleOnOverheatedNodeEvenWithResources() {
        Node hot = node(UUID.randomUUID(), NodeStatus.ONLINE, new BigDecimal("8.00"), new BigDecimal("8.00"), 16384, 16384, new BigDecimal("90.00"));
        when(nodeRepository.findByStatus(NodeStatus.ONLINE)).thenReturn(List.of(hot));

        Optional<Node> result = schedulerService().schedule(new BigDecimal("1.00"), 1024);

        assertThat(result).isEmpty();
    }
}