package com.cloudbox.master.container;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.cloudbox.master.common.ResourceNotFoundException;
import com.cloudbox.master.container.dto.ContainerStatusUpdateRequest;
import com.cloudbox.master.container.dto.PendingCommandResponse;
import com.cloudbox.master.scheduler.SchedulerService;
import com.cloudbox.master.realtime.ClusterStatusPublisher;

@ExtendWith(MockitoExtension.class)
class ContainerServiceTest {

    @Mock
    private ContainerRepository containerRepository;

    @Mock
    private SchedulerService schedulerService;

    @Mock
    private ClusterStatusPublisher clusterStatusPublisher;

    private ContainerService containerService() {
        return new ContainerService(containerRepository, schedulerService, clusterStatusPublisher);
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
        ContainerInstance stopping = container(UUID.randomUUID(), nodeId, ContainerStatus.STOPPING);
        when(containerRepository.findByNodeIdAndStatusIn(nodeId,
                Set.of(ContainerStatus.PENDING, ContainerStatus.STOPPING, ContainerStatus.REMOVING)))
                .thenReturn(List.of(pending, stopping));

        List<PendingCommandResponse> result = containerService().findPendingCommands(nodeId);

        assertThat(result).hasSize(2);
        PendingCommandResponse command = result.get(0);
        assertThat(command.containerId()).isEqualTo(pending.getId());
        assertThat(command.action()).isEqualTo("START");
        assertThat(command.imageName()).isEqualTo("nginx:1.27");
        assertThat(command.cpuCores()).isEqualTo(1);
        assertThat(command.memoryMb()).isEqualTo(512);
        assertThat(command.diskMb()).isEqualTo(128);
        assertThat(result.get(1).action()).isEqualTo("STOP");
    }

    @Test
    void returnsEmptyListWhenNoPendingCommand() {
        UUID nodeId = UUID.randomUUID();
        when(containerRepository.findByNodeIdAndStatusIn(nodeId,
                Set.of(ContainerStatus.PENDING, ContainerStatus.STOPPING, ContainerStatus.REMOVING)))
                .thenReturn(List.of());

        List<PendingCommandResponse> result = containerService().findPendingCommands(nodeId);

        assertThat(result).isEmpty();
    }

    @Test
    void requestsStopForRunningContainer() {
        UUID id = UUID.randomUUID();
        ContainerInstance container = container(id, UUID.randomUUID(), ContainerStatus.RUNNING);
        container.setDockerContainerId("docker-123");
        when(containerRepository.findById(id)).thenReturn(Optional.of(container));

        var result = containerService().stop(id);

        assertThat(result.status()).isEqualTo(ContainerStatus.STOPPING);
        verify(clusterStatusPublisher).publishContainerStatusChange(
                id, ContainerStatus.RUNNING, ContainerStatus.STOPPING);
    }

    @Test
    void requestsRemovalForStoppedContainer() {
        UUID id = UUID.randomUUID();
        ContainerInstance container = container(id, UUID.randomUUID(), ContainerStatus.STOPPED);
        container.setDockerContainerId("docker-123");
        when(containerRepository.findById(id)).thenReturn(Optional.of(container));

        var result = containerService().remove(id);

        assertThat(result.status()).isEqualTo(ContainerStatus.REMOVING);
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
        verify(clusterStatusPublisher).publishContainerStatusChange(
                id, ContainerStatus.PENDING, ContainerStatus.RUNNING);
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
                .hasMessageContaining("RUNNING, ERROR, STOPPED ou REMOVED");
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
