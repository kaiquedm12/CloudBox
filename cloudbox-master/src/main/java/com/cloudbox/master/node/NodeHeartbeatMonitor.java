package com.cloudbox.master.node;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NodeHeartbeatMonitor {

    private static final Logger log = LoggerFactory.getLogger(NodeHeartbeatMonitor.class);

    private final NodeRepository nodeRepository;
    private final HeartbeatProperties heartbeatProperties;

    public NodeHeartbeatMonitor(NodeRepository nodeRepository, HeartbeatProperties heartbeatProperties) {
        this.nodeRepository = nodeRepository;
        this.heartbeatProperties = heartbeatProperties;
    }

    @Scheduled(fixedRateString = "#{${cloudbox.heartbeat.check-interval-seconds} * 1000}")
    @Transactional
    public void markStaleNodesOffline() {
        Instant threshold = Instant.now().minusSeconds(heartbeatProperties.getTimeoutSeconds());
        List<Node> staleNodes = nodeRepository.findByStatusAndLastHeartbeatBefore(NodeStatus.ONLINE, threshold);

        if (staleNodes.isEmpty()) {
            log.debug("Nenhum nó ONLINE com heartbeat anterior a {} foi encontrado para marcar como OFFLINE", threshold);
            return;
        }

        for (Node node : staleNodes) {
            node.setStatus(NodeStatus.OFFLINE);
        }
        nodeRepository.saveAll(staleNodes);

        log.info("Marcados {} nó(s) como OFFLINE por timeout de heartbeat", staleNodes.size());
    }
}