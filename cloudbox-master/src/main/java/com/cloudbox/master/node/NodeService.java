package com.cloudbox.master.node;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cloudbox.master.common.ResourceNotFoundException;
import com.cloudbox.master.node.dto.HeartbeatRequest;
import com.cloudbox.master.node.dto.NodeRegisterRequest;
import com.cloudbox.master.node.dto.NodeRegisterResponse;
import com.cloudbox.master.node.dto.NodeResponse;

@Service
public class NodeService {

    private final NodeRepository nodeRepository;

    public NodeService(NodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
    }

    @Transactional
    public NodeRegisterResponse registerNode(NodeRegisterRequest request) {
        Node node = new Node();
        node.setName(request.name());
        node.setToken(UUID.randomUUID().toString());
        node.setStatus(NodeStatus.OFFLINE);
        node.setCpuTotal(request.cpuTotal());
        node.setCpuFree(request.cpuTotal());
        node.setRamTotalMb(request.ramTotalMb());
        node.setRamFreeMb(request.ramTotalMb());
        node.setDiskTotalMb(request.diskTotalMb());
        node.setDiskFreeMb(request.diskTotalMb());
        node.setCreatedAt(Instant.now());

        Node saved = nodeRepository.save(node);
        return new NodeRegisterResponse(saved.getId(), saved.getToken());
    }

    @Transactional
    public void receiveHeartbeat(UUID nodeId, HeartbeatRequest request) {
        Node node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Nó não encontrado: " + nodeId));

        node.setCpuFree(request.cpuFree());
        node.setRamFreeMb(request.ramFreeMb());
        node.setDiskFreeMb(request.diskFreeMb());
        node.setTemperatureCelsius(request.temperatureCelsius());
        node.setLastHeartbeat(Instant.now());
        node.setStatus(NodeStatus.ONLINE);
    }

    @Transactional(readOnly = true)
    public List<NodeResponse> listNodes() {
        return nodeRepository.findAll().stream().map(this::toResponse).toList();
    }

    private NodeResponse toResponse(Node node) {
        return new NodeResponse(
                node.getId(),
                node.getName(),
                node.getStatus(),
                node.getCpuTotal(),
                node.getCpuFree(),
                node.getRamTotalMb(),
                node.getRamFreeMb(),
                node.getDiskTotalMb(),
                node.getDiskFreeMb(),
                node.getTemperatureCelsius(),
                node.getLastHeartbeat());
    }
}