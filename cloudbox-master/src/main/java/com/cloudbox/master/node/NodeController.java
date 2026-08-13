package com.cloudbox.master.node;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import com.cloudbox.master.node.dto.HeartbeatRequest;
import com.cloudbox.master.node.dto.NodeRegisterRequest;
import com.cloudbox.master.node.dto.NodeRegisterResponse;
import com.cloudbox.master.node.dto.NodeResponse;

@RestController
@RequestMapping("/api/nodes")
public class NodeController {

    private final NodeService nodeService;

    public NodeController(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    @PostMapping("/register")
    public ResponseEntity<NodeRegisterResponse> register(@Valid @RequestBody NodeRegisterRequest request) {
        NodeRegisterResponse response = nodeService.registerNode(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/heartbeat")
    public ResponseEntity<Void> heartbeat(@PathVariable UUID id, @Valid @RequestBody HeartbeatRequest request) {
        nodeService.receiveHeartbeat(id, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<NodeResponse>> list() {
        return ResponseEntity.ok(nodeService.listNodes());
    }
}