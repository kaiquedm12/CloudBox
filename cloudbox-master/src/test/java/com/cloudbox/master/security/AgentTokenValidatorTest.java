package com.cloudbox.master.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.cloudbox.master.common.ResourceNotFoundException;
import com.cloudbox.master.common.UnauthorizedException;
import com.cloudbox.master.container.ContainerInstance;
import com.cloudbox.master.container.ContainerRepository;
import com.cloudbox.master.node.Node;
import com.cloudbox.master.node.NodeRepository;

@ExtendWith(MockitoExtension.class)
class AgentTokenValidatorTest {

    private static final String NODE_TOKEN = "0f7e4a6b-9c2d-4e8f-a1b3-5d6c7e8f9a0b";

    @Mock
    private NodeRepository nodeRepository;

    @Mock
    private ContainerRepository containerRepository;

    private AgentTokenValidator validator() {
        return new AgentTokenValidator(nodeRepository, containerRepository);
    }

    private Node node(UUID id) {
        Node node = new Node();
        node.setId(id);
        node.setToken(NODE_TOKEN);
        return node;
    }

    @Test
    void acceptsNodeWithCorrectBearerToken() {
        UUID nodeId = UUID.randomUUID();
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node(nodeId)));

        Node result = validator().authorizeNode(nodeId, "Bearer " + NODE_TOKEN);

        assertThat(result.getToken()).isEqualTo(NODE_TOKEN);
    }

    @Test
    void rejectsNodeWithWrongToken() {
        UUID nodeId = UUID.randomUUID();
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node(nodeId)));

        assertThatThrownBy(() -> validator().authorizeNode(nodeId, "Bearer token-incorreto"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsNodeWithoutAuthorizationHeader() {
        UUID nodeId = UUID.randomUUID();
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node(nodeId)));

        assertThatThrownBy(() -> validator().authorizeNode(nodeId, null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsNodeThatDoesNotExist() {
        UUID nodeId = UUID.randomUUID();
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator().authorizeNode(nodeId, "Bearer " + NODE_TOKEN))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void acceptsContainerOwnedByNodeWithCorrectToken() {
        UUID containerId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        ContainerInstance container = new ContainerInstance();
        container.setId(containerId);
        container.setNodeId(nodeId);
        when(containerRepository.findById(containerId)).thenReturn(Optional.of(container));
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node(nodeId)));

        validator().authorizeContainer(containerId, "Bearer " + NODE_TOKEN);
    }

    @Test
    void rejectsContainerWhoseNodeHasDifferentToken() {
        UUID containerId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        ContainerInstance container = new ContainerInstance();
        container.setId(containerId);
        container.setNodeId(nodeId);
        when(containerRepository.findById(containerId)).thenReturn(Optional.of(container));
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node(nodeId)));

        assertThatThrownBy(() -> validator().authorizeContainer(containerId, "Bearer token-incorreto"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsContainerNotLinkedToAnyNode() {
        UUID containerId = UUID.randomUUID();
        ContainerInstance container = new ContainerInstance();
        container.setId(containerId);
        container.setNodeId(null);
        when(containerRepository.findById(containerId)).thenReturn(Optional.of(container));

        assertThatThrownBy(() -> validator().authorizeContainer(containerId, "Bearer " + NODE_TOKEN))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void throwsNotFoundWhenContainerDoesNotExist() {
        UUID containerId = UUID.randomUUID();
        when(containerRepository.findById(containerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator().authorizeContainer(containerId, "Bearer " + NODE_TOKEN))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}