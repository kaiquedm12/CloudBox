package com.cloudbox.master.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.cloudbox.master.node.Node;
import com.cloudbox.master.node.NodeStatus;

class NodeCandidateFilterTest {

    private NodeCandidateFilter filter;

    @BeforeEach
    void setUp() {
        filter = new NodeCandidateFilter(new SchedulerProperties());
    }

    private Node node(UUID id, NodeStatus status, BigDecimal cpuFree, Integer ramFreeMb, BigDecimal temperature) {
        Node node = new Node();
        node.setId(id);
        node.setStatus(status);
        node.setCpuTotal(new BigDecimal("8.00"));
        node.setCpuFree(cpuFree);
        node.setRamTotalMb(16384);
        node.setRamFreeMb(ramFreeMb);
        node.setTemperatureCelsius(temperature);
        return node;
    }

    @Test
    void keepsOnlyOnlineNodesWithEnoughResources() {
        Node apt = node(UUID.randomUUID(), NodeStatus.ONLINE, new BigDecimal("4.00"), 4096, null);
        Node offline = node(UUID.randomUUID(), NodeStatus.OFFLINE, new BigDecimal("4.00"), 4096, null);
        Node lowCpu = node(UUID.randomUUID(), NodeStatus.ONLINE, new BigDecimal("1.00"), 4096, null);
        Node lowRam = node(UUID.randomUUID(), NodeStatus.ONLINE, new BigDecimal("4.00"), 512, null);

        List<Node> result = filter.filterCandidates(
                List.of(apt, offline, lowCpu, lowRam), new BigDecimal("2.00"), 1024);

        assertThat(result).containsExactly(apt);
    }

    @Test
    void rejectsNodeWhoseTemperatureExceedsLimit() {
        Node hotNode = node(UUID.randomUUID(), NodeStatus.ONLINE, new BigDecimal("8.00"), 16384, new BigDecimal("88.00"));
        Node coolNode = node(UUID.randomUUID(), NodeStatus.ONLINE, new BigDecimal("8.00"), 16384, new BigDecimal("60.00"));

        List<Node> result = filter.filterCandidates(
                List.of(hotNode, coolNode), new BigDecimal("1.00"), 1024);

        assertThat(result).containsExactly(coolNode);
    }

    @Test
    void acceptsNodeWithoutTemperatureSensor() {
        Node noSensor = node(UUID.randomUUID(), NodeStatus.ONLINE, new BigDecimal("8.00"), 16384, null);

        List<Node> result = filter.filterCandidates(List.of(noSensor), new BigDecimal("1.00"), 1024);

        assertThat(result).containsExactly(noSensor);
    }
}