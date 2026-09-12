package com.cloudbox.master.scheduler;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import com.cloudbox.master.node.Node;
import com.cloudbox.master.node.NodeRepository;
import com.cloudbox.master.node.NodeStatus;

@Service
public class SchedulerService {

    private final NodeRepository nodeRepository;
    private final NodeCandidateFilter nodeCandidateFilter;
    private final NodeScoringStrategy nodeScoringStrategy;

    public SchedulerService(NodeRepository nodeRepository,
                            NodeCandidateFilter nodeCandidateFilter,
                            NodeScoringStrategy nodeScoringStrategy) {
        this.nodeRepository = nodeRepository;
        this.nodeCandidateFilter = nodeCandidateFilter;
        this.nodeScoringStrategy = nodeScoringStrategy;
    }

    public Optional<Node> schedule(BigDecimal cpuRequested, Integer ramRequestedMb) {
        return schedule(cpuRequested, ramRequestedMb, false);
    }

    public Optional<Node> schedule(BigDecimal cpuRequested, Integer ramRequestedMb,
                                   boolean requiresAdvertiseAddress) {
        List<Node> onlineNodes = nodeRepository.findByStatus(NodeStatus.ONLINE);
        List<Node> candidates = nodeCandidateFilter.filterCandidates(
                onlineNodes, cpuRequested, ramRequestedMb, requiresAdvertiseAddress);
        return nodeScoringStrategy.selectBest(candidates, cpuRequested, ramRequestedMb);
    }
}
