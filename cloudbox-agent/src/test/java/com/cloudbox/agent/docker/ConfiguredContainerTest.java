package com.cloudbox.agent.docker;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import com.cloudbox.agent.client.AgentClientProperties;
import com.cloudbox.agent.client.RestartPolicySpec;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.ContainerConfig;
import com.github.dockerjava.api.model.HostConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

class ConfiguredContainerTest {
    private final DockerClient docker = mock(DockerClient.class);
    private final ContainerExecutionService service = spy(new ContainerExecutionService(docker, new AgentClientProperties()));
    private final CreateContainerCmd create = mock(CreateContainerCmd.class, RETURNS_SELF);

    private void prepareCreation() {
        doNothing().when(service).pullImage("nginx:alpine");
        InspectContainerCmd missing = mock(InspectContainerCmd.class);
        when(docker.inspectContainerCmd("cloudbox-test")).thenReturn(missing);
        when(missing.exec()).thenThrow(new NotFoundException("missing"));
        when(docker.createContainerCmd("nginx:alpine")).thenReturn(create);
        CreateContainerResponse response = new CreateContainerResponse();
        response.setId("docker-test");
        when(create.exec()).thenReturn(response);
        when(docker.startContainerCmd("docker-test")).thenReturn(mock(StartContainerCmd.class));
        InspectContainerCmd inspected = mock(InspectContainerCmd.class);
        InspectContainerResponse container = mock(InspectContainerResponse.class);
        var state = mock(InspectContainerResponse.ContainerState.class);
        when(container.getId()).thenReturn("docker-test");
        when(container.getState()).thenReturn(state);
        when(state.getRunning()).thenReturn(true);
        when(docker.inspectContainerCmd("docker-test")).thenReturn(inspected);
        when(inspected.exec()).thenReturn(container);
    }

    @ParameterizedTest
    @CsvSource({"NONE,no,0", "ALWAYS,always,0", "UNLESS_STOPPED,unless-stopped,0", "ON_FAILURE,on-failure,3"})
    void shouldTranslateEnvironmentArgvAndRestart(String policy, String dockerPolicy, int retries) {
        prepareCreation();
        var options = new ExecutionOptions(Map.of("GREETING", "hello=world"), List.of("/bin/echo"),
                List.of("$GREETING", "hello world"), new RestartPolicySpec(policy, retries));
        service.runContainer("nginx:alpine", "cloudbox-test", "test", 1, 64, List.of(), options);
        verify(create).withEnv(List.of("GREETING=hello=world"));
        verify(create).withEntrypoint(List.of("/bin/echo"));
        verify(create).withCmd(List.of("$GREETING", "hello world"));
        var host = ArgumentCaptor.forClass(HostConfig.class);
        verify(create).withHostConfig(host.capture());
        if ("NONE".equals(policy)) {
            // docker-java encodes the Engine default (no restart) as an empty name.
            assertThat(host.getValue().getRestartPolicy().getName()).isIn("", "no");
        } else {
            assertThat(host.getValue().getRestartPolicy().getName()).isEqualTo(dockerPolicy);
        }
        assertThat(host.getValue().getRestartPolicy().getMaximumRetryCount()).isEqualTo(retries);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> labels = ArgumentCaptor.forClass(Map.class);
        verify(create).withLabels(labels.capture());
        assertThat(labels.getValue().get(ContainerExecutionService.SPEC_LABEL))
                .contains("config-sha256=").doesNotContain("GREETING", "hello=world", "$GREETING");
    }

    @Test
    void shouldClearImageCmdOnlyWhenEntrypointWasReplaced() {
        prepareCreation();
        service.runContainer("nginx:alpine", "cloudbox-test", "test", 1, 64, List.of(),
                new ExecutionOptions(null, List.of("nginx"), null, null));
        verify(create).withCmd(List.of());
        clearInvocations(create);
        service.runContainer("nginx:alpine", "cloudbox-test", "test", 1, 64, List.of(),
                new ExecutionOptions(null, null, List.of("nginx", "-g", "daemon off;"), null));
        verify(create, never()).withEntrypoint(org.mockito.ArgumentMatchers.<List<String>>any());
        verify(create).withCmd(List.of("nginx", "-g", "daemon off;"));
    }

    @Test
    void shouldReuseSameConfigurationAndRejectChangedEnvironmentBeforeDockerWrites() {
        var options = new ExecutionOptions(Map.of("MODE", "one"), null, null, null);
        var config = mock(ContainerConfig.class);
        when(config.getImage()).thenReturn("nginx:alpine");
        when(config.getLabels()).thenReturn(Map.of(ContainerExecutionService.MANAGED_LABEL, "true",
                ContainerExecutionService.CONTAINER_ID_LABEL, "test",
                ContainerExecutionService.SPEC_LABEL, "nginx:alpine|1|64|config-sha256=" + options.fingerprint()));
        var container = mock(InspectContainerResponse.class);
        var state = mock(InspectContainerResponse.ContainerState.class);
        when(container.getConfig()).thenReturn(config);
        when(container.getId()).thenReturn("docker-test");
        when(container.getState()).thenReturn(state);
        when(state.getRunning()).thenReturn(true);
        var inspect = mock(InspectContainerCmd.class);
        when(inspect.exec()).thenReturn(container);
        when(docker.inspectContainerCmd(anyString())).thenReturn(inspect);
        assertThat(service.runContainer("nginx:alpine", "cloudbox-test", "test", 1, 64, List.of(), options).dockerContainerId())
                .isEqualTo("docker-test");
        assertThatThrownBy(() -> service.runContainer("nginx:alpine", "cloudbox-test", "test", 1, 64, List.of(),
                new ExecutionOptions(Map.of("MODE", "two"), null, null, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("especificacao");
        verify(docker, never()).createContainerCmd(anyString());
        verify(docker, never()).startContainerCmd(anyString());
    }
}
