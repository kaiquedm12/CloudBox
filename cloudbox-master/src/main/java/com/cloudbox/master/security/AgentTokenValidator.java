package com.cloudbox.master.security;

import java.util.UUID;
import org.springframework.stereotype.Component;
import com.cloudbox.master.common.ResourceNotFoundException;
import com.cloudbox.master.common.UnauthorizedException;
import com.cloudbox.master.container.ContainerInstance;
import com.cloudbox.master.container.ContainerRepository;
import com.cloudbox.master.node.Node;
import com.cloudbox.master.node.NodeRepository;

/**
 * Validacao pontual do token do no (Fase 6). Ainda nao e a autenticacao JWT
 * completa (Fase 8): apenas confere o header {@code Authorization: Bearer {token}}
 * contra o token gerado no registro do no.
 */
@Component
public class AgentTokenValidator {

    private static final String BEARER_PREFIX = "Bearer ";

    private final NodeRepository nodeRepository;
    private final ContainerRepository containerRepository;

    public AgentTokenValidator(NodeRepository nodeRepository, ContainerRepository containerRepository) {
        this.nodeRepository = nodeRepository;
        this.containerRepository = containerRepository;
    }

    public Node authorizeNode(UUID nodeId, String authorizationHeader) {
        Node node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Nó não encontrado: " + nodeId));
        assertTokenMatches(node, authorizationHeader);
        return node;
    }

    public void authorizeContainer(UUID containerId, String authorizationHeader) {
        ContainerInstance container = containerRepository.findById(containerId)
                .orElseThrow(() -> new ResourceNotFoundException("Container não encontrado: " + containerId));
        if (container.getNodeId() == null) {
            throw new UnauthorizedException("Container não vinculado a nenhum nó");
        }
        Node node = nodeRepository.findById(container.getNodeId())
                .orElseThrow(() -> new UnauthorizedException("Nó do container não encontrado: " + container.getNodeId()));
        assertTokenMatches(node, authorizationHeader);
    }

    private void assertTokenMatches(Node node, String authorizationHeader) {
        String provided = bearerToken(authorizationHeader);
        if (provided == null || !provided.equals(node.getToken())) {
            throw new UnauthorizedException(
                    "Token inválido ou ausente. Envie Authorization: Bearer {token-do-no}");
        }
    }

    private String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}