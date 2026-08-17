package com.cloudbox.master.scheduler;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;
import com.cloudbox.master.node.Node;
import com.cloudbox.master.node.NodeStatus;

@Component
public class NodeCandidateFilter {

    private final SchedulerProperties schedulerProperties;

    public NodeCandidateFilter(SchedulerProperties schedulerProperties) {
        this.schedulerProperties = schedulerProperties;
    }

    public List<Node> filterCandidates(List<Node> allNodes, BigDecimal cpuRequested, Integer ramRequestedMb) {
        BigDecimal maxTemperatureCelsius = schedulerProperties.getMaxTemperatureCelsius();

        return allNodes.stream()
                .filter(node -> node.getStatus() == NodeStatus.ONLINE)
                .filter(node -> node.getCpuFree().compareTo(cpuRequested) >= 0)
                .filter(node -> node.getRamFreeMb() >= ramRequestedMb)
                .filter(node -> node.getTemperatureCelsius() == null
                        || node.getTemperatureCelsius().compareTo(maxTemperatureCelsius) < 0)
                .toList();
    }
}