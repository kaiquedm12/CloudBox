package com.cloudbox.master.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.cloudbox.master.node.Node;
import com.cloudbox.master.node.NodeStatus;

class NodeScoringStrategyTest {

    private final NodeScoringStrategy strategy = new NodeScoringStrategy();

    private Node node(UUID id, BigDecimal cpuTotal, BigDecimal cpuFree, Integer ramTotalMb, Integer ramFreeMb) {
        Node node = new Node();
        node.setId(id);
        node.setStatus(NodeStatus.ONLINE);
        node.setCpuTotal(cpuTotal);
        node.setCpuFree(cpuFree);
        node.setRamTotalMb(ramTotalMb);
        node.setRamFreeMb(ramFreeMb);
        return node;
    }

    @Test
    void picksNodeWithMostRemainingSlack() {
        Node smaller = node(UUID.randomUUID(), new BigDecimal("4.00"), new BigDecimal("4.00"), 8192, 8192);
        Node bigger = node(UUID.randomUUID(), new BigDecimal("8.00"), new BigDecimal("8.00"), 16384, 16384);
        Node tight = node(UUID.randomUUID(), new BigDecimal("2.00"), new BigDecimal("2.00"), 4096, 2048);

        Optional<Node> result = strategy.selectBest(List.of(smaller, tight, bigger), new BigDecimal("1.00"), 1024);

        assertThat(result).hasValue(bigger);
    }

    @Test
    void breaksTieByRemainingRam() {
        Node moreRam = node(UUID.randomUUID(), new BigDecimal("8.00"), new BigDecimal("6.00"), 16384, 8192);
        Node lessRam = node(UUID.randomUUID(), new BigDecimal("8.00"), new BigDecimal("6.00"), 16384, 4096);

        Optional<Node> result = strategy.selectBest(List.of(lessRam, moreRam), new BigDecimal("1.00"), 1024);

        assertThat(result).hasValue(moreRam);
    }

    @Test
    void returnsEmptyForEmptyCandidates() {
        Optional<Node> result = strategy.selectBest(List.of(), new BigDecimal("1.00"), 1024);

        assertThat(result).isEmpty();
    }
}