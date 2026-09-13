package com.cloudbox.master.scheduler;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import com.cloudbox.master.node.Node;

@Component
public class
NodeScoringStrategy {

    public Optional<Node> selectBest(List<Node> candidates, BigDecimal cpuRequested, Integer ramRequestedMb,
                                     Integer diskRequestedMb) {
        return candidates.stream().max((a, b) -> compare(
                a, b, cpuRequested, ramRequestedMb, diskRequestedMb));
    }

    private int compare(Node a, Node b, BigDecimal cpuRequested, Integer ramRequestedMb, Integer diskRequestedMb) {
        int scoreCmp = score(a, cpuRequested, ramRequestedMb, diskRequestedMb)
                .compareTo(score(b, cpuRequested, ramRequestedMb, diskRequestedMb));
        if (scoreCmp != 0) {
            return scoreCmp;
        }
        int ramCmp = Long.compare(
                (long) a.getRamFreeMb() - ramRequestedMb,
                (long) b.getRamFreeMb() - ramRequestedMb);
        if (ramCmp != 0) {
            return ramCmp;
        }
        return a.getCpuFree().subtract(cpuRequested).compareTo(b.getCpuFree().subtract(cpuRequested));
    }

    private BigDecimal score(Node node, BigDecimal cpuRequested, Integer ramRequestedMb, Integer diskRequestedMb) {
        return ramSlack(node, ramRequestedMb)
                .add(cpuSlack(node, cpuRequested))
                .add(diskSlack(node, diskRequestedMb));
    }

    private BigDecimal ramSlack(Node node, Integer ramRequestedMb) {
        long slackValue = (long) node.getRamFreeMb() - ramRequestedMb;
        BigDecimal slack = BigDecimal.valueOf(slackValue);
        if (node.getRamTotalMb() == null || node.getRamTotalMb() == 0) {
            return slack;
        }
        return slack.divide(BigDecimal.valueOf(node.getRamTotalMb()), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal cpuSlack(Node node, BigDecimal cpuRequested) {
        BigDecimal slack = node.getCpuFree().subtract(cpuRequested);
        if (node.getCpuTotal() == null || node.getCpuTotal().signum() == 0) {
            return slack;
        }
        return slack.divide(node.getCpuTotal(), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal diskSlack(Node node, Integer diskRequestedMb) {
        long slackValue = (long) node.getDiskFreeMb() - diskRequestedMb;
        BigDecimal slack = BigDecimal.valueOf(slackValue);
        if (node.getDiskTotalMb() == null || node.getDiskTotalMb() == 0) {
            return slack;
        }
        return slack.divide(BigDecimal.valueOf(node.getDiskTotalMb()), 6, RoundingMode.HALF_UP);
    }
}
