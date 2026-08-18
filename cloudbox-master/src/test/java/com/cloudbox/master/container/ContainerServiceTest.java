package com.cloudbox.master.container;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.cloudbox.master.common.ResourceNotFoundException;
import com.cloudbox.master.container.dto.ContainerStatusUpdateRequest;
import com.cloudbox.master.container.dto.PendingCommandResponse;
import com.cloudbox.master.scheduler.SchedulerService;

@ExtendWith(MockitoExtension.class)
class ContainerServiceTest {

    @Mock
    private ContainerRepository containerRepository;

    @Mock
    private SchedulerService schedulerService;

    private ContainerService containerService() {
        return new ContainerService(containerRepository, schedulerService);
    }

    private ContainerInstance container(UUID id, UUID nodeId, ContainerStatus status) {
        ContainerInstance container = new ContainerInstance();
        container.setId(id);
        container.setNodeId(nodeId);
        container.setStatus(status);
        container.setImageName("nginx:1.27");
        container.setCpuCores(1);
        container.setMemoryMb(512);
        container.setDiskMb(128);
        container.setCreatedAt(Instant.now());
        container.setUpdatedAt(Instant.now());
        return container;
    }

    @Test
    void listsOnlyPendingCommandsOfTheNode() {
        UUID nodeId = UUID.randomUUID();
        ContainerInstance pending = container(UUID.randomUUID(), nodeId, ContainerStatus.PENDING);
        ContainerInstance running = container(UUID.randomUUID(), nodeId, ContainerStatus.RUNNING);
        when(containerRepository.findByNodeIdAndStatus(nodeId, ContainerStatus.PENDING))
                .thenReturn(List.of(pending, running));

        List<PendingCommandResponse> result = containerService().findPendingCommands(nodeId);

        assertThat(result).hasSize(2);
        PendingCommandResponse command = result.get(0);
        assertThat(command.containerId()).isEqualTo(pending.getId());
        assertThat(command.imageName()).isEqualTo("nginx:1.27");
        assertThat(command.cpuCores()).isEqualTo(1);
        assertThat(command.memoryMb()).isEqualTo(512);
        assertThat(command.diskMb()).isEqualTo(128);
    }

    @Test
    void returnsEmptyListWhenNoPendingCommand() {
        UUID nodeId = UUID.randomUUID();
        when(containerRepository.findByNodeIdAndStatus(nodeId, ContainerStatus.PENDING)).thenReturn(List.of());

        List<PendingCommandResponse> result = containerService().findPendingCommands(nodeId);

        assertThat(result).isEmpty();
    }

    @Test
    void updateStatusToRunningPersistsDockerContainerId() {
        UUID id = UUID.randomUUID();
        ContainerInstance container = container(id, UUID.randomUUID(), ContainerStatus.PENDING);
        when(containerRepository.findById(id)).thenReturn(Optional.of(container));

        var request = new ContainerStatusUpdateRequest(ContainerStatus.RUNNING, "abc123", null);
        var result = containerService().updateStatus(id, request);

        assertThat(result.status()).isEqualTo(ContainerStatus.RUNNING);
        assertThat(result.dockerContainerId()).isEqualTo("abc123");
        assertThat(container.getStatus()).isEqualTo(ContainerStatus.RUNNING);
        assertThat(container.getDockerContainerId()).isEqualTo("abc123");
        assertThat(container.getUpdatedAt()).isNotNull();
    }

    @Test
    void updateStatusToErrorPersistsErrorMessage() {
        UUID id = UUID.randomUUID();
        ContainerInstance container = container(id, UUID.randomUUID(), ContainerStatus.PENDING);
        when(containerRepository.findById(id)).thenReturn(Optional.of(container));

        var request = new ContainerStatusUpdateRequest(ContainerStatus.ERROR, null, "falha ao puxar imagem");
        var result = containerService().updateStatus(id, request);

        assertThat(result.status()).isEqualTo(ContainerStatus.ERROR);
        assertThat(result.errorMessage()).isEqualTo("falha ao puxar imagem");
    }

    @Test
    void rejectsStatusThatAgentMustNotReport() {
        UUID id = UUID.randomUUID();
        ContainerInstance container = container(id, UUID.randomUUID(), ContainerStatus.PENDING);
        when(containerRepository.findById(id)).thenReturn(Optional.of(container));

        var request = new ContainerStatusUpdateRequest(ContainerStatus.PENDING, null, null);

        assertThatThrownBy(() -> containerService().updateStatus(id, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RUNNING, ERROR ou STOPPED");
    }

    @Test
    void throwsNotFoundWhenContainerDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(containerRepository.findById(id)).thenReturn(Optional.empty());

        var request = new ContainerStatusUpdateRequest(ContainerStatus.RUNNING, "abc123", null);

        assertThatThrownBy(() -> containerService().updateStatus(id, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}