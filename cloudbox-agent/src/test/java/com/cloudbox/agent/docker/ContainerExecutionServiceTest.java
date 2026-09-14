package com.cloudbox.agent.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.cloudbox.agent.client.AgentClientProperties;
import com.cloudbox.agent.client.ContainerEndpoint;
import com.cloudbox.agent.client.PortExposure;
import com.cloudbox.agent.client.PortProtocol;
import com.cloudbox.agent.client.PortSpec;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.ContainerConfig;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.NetworkSettings;
import com.github.dockerjava.api.model.Ports;

class ContainerExecutionServiceTest {

    @Test
    void shouldTranslatePortsAndReportAllObservedBindings() {
        DockerClient dockerClient = mock(DockerClient.class);
        AgentClientProperties properties = properties("node-a.example.test", "0.0.0.0");
        ContainerExecutionService service = spy(new ContainerExecutionService(dockerClient, properties));
        doNothing().when(service).pullImage("nginx:alpine");

        String name = "cloudbox-command-1";
        InspectContainerCmd missing = mock(InspectContainerCmd.class);
        when(dockerClient.inspectContainerCmd(name)).thenReturn(missing);
        when(missing.exec()).thenThrow(new NotFoundException("missing"));

        CreateContainerCmd create = mock(CreateContainerCmd.class, Mockito.RETURNS_SELF);
        CreateContainerResponse created = new CreateContainerResponse();
        created.setId("docker-1");
        when(dockerClient.createContainerCmd("nginx:alpine")).thenReturn(create);
        when(create.exec()).thenReturn(created);
        StartContainerCmd start = mock(StartContainerCmd.class);
        when(dockerClient.startContainerCmd("docker-1")).thenReturn(start);

        ExposedPort http = ExposedPort.tcp(80);
        ExposedPort tcp = ExposedPort.tcp(5432);
        ExposedPort udp = ExposedPort.udp(53);
        Ports observedPorts = mock(Ports.class);
        when(observedPorts.getBindings()).thenReturn(Map.of(
                http, new Ports.Binding[] {
                        Ports.Binding.bindIpAndPort("0.0.0.0", 32768),
                        Ports.Binding.bindIpAndPort("::", 32768) },
                tcp, new Ports.Binding[] { Ports.Binding.bindIpAndPort("127.0.0.1", 15432) },
                udp, new Ports.Binding[] { Ports.Binding.bindIpAndPort("0.0.0.0", 10053) }));
        InspectContainerResponse inspected = inspectedContainer("docker-1", null, null, true, observedPorts);
        InspectContainerCmd inspectCreated = mock(InspectContainerCmd.class);
        when(dockerClient.inspectContainerCmd("docker-1")).thenReturn(inspectCreated);
        when(inspectCreated.exec()).thenReturn(inspected);

        List<PortSpec> requestedPorts = List.of(
                new PortSpec(80, null, PortProtocol.TCP, PortExposure.HTTP, null),
                new PortSpec(5432, 15432, PortProtocol.TCP, PortExposure.TCP, "127.0.0.1"),
                new PortSpec(53, 10053, PortProtocol.UDP, PortExposure.UDP, null),
                new PortSpec(9000, null, PortProtocol.UDP, PortExposure.INTERNAL, null));

        ContainerLaunchResult result = service.runContainer(
                "nginx:alpine", name, "command-1", 2, 256, requestedPorts);

        assertThat(result).isEqualTo(new ContainerLaunchResult("docker-1", List.of(
                new ContainerEndpoint(80, 32768, PortProtocol.TCP, "0.0.0.0"),
                new ContainerEndpoint(80, 32768, PortProtocol.TCP, "::"),
                new ContainerEndpoint(5432, 15432, PortProtocol.TCP, "127.0.0.1"),
                new ContainerEndpoint(53, 10053, PortProtocol.UDP, "0.0.0.0"))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ExposedPort>> exposedCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<HostConfig> hostConfigCaptor = ArgumentCaptor.forClass(HostConfig.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> labelsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(create).withExposedPorts(exposedCaptor.capture());
        verify(create).withHostConfig(hostConfigCaptor.capture());
        verify(create).withLabels(labelsCaptor.capture());
        assertThat(exposedCaptor.getValue()).containsExactly(http, tcp, udp, ExposedPort.udp(9000));
        assertThat(labelsCaptor.getValue())
                .containsEntry(ContainerExecutionService.MANAGED_LABEL, "true")
                .containsEntry(ContainerExecutionService.CONTAINER_ID_LABEL, "command-1")
                .containsKey(ContainerExecutionService.SPEC_LABEL);

        Map<ExposedPort, Ports.Binding[]> configuredBindings =
                hostConfigCaptor.getValue().getPortBindings().getBindings();
        assertThat(configuredBindings).containsOnlyKeys(http, tcp, udp);
        assertThat(configuredBindings.get(http)[0].getHostIp()).isEqualTo("0.0.0.0");
        assertThat(configuredBindings.get(http)[0].getHostPortSpec()).isNull();
        assertThat(configuredBindings.get(tcp)[0].getHostPortSpec()).isEqualTo("15432");
        assertThat(configuredBindings.get(udp)[0].getHostPortSpec()).isEqualTo("10053");
        assertThat(hostConfigCaptor.getValue().getNanoCPUs()).isEqualTo(2_000_000_000L);
        assertThat(hostConfigCaptor.getValue().getMemory()).isEqualTo(256L * 1024L * 1024L);
    }

    @Test
    void shouldReuseOwnedContainerAfterAgentRestart() {
        DockerClient dockerClient = mock(DockerClient.class);
        ContainerExecutionService service = spy(new ContainerExecutionService(
                dockerClient, properties(null, "0.0.0.0")));
        ContainerConfig config = mock(ContainerConfig.class);
        when(config.getImage()).thenReturn("nginx:alpine");
        when(config.getLabels()).thenReturn(Map.of(
                ContainerExecutionService.MANAGED_LABEL, "true",
                ContainerExecutionService.CONTAINER_ID_LABEL, "command-1",
                ContainerExecutionService.SPEC_LABEL, "nginx:alpine|1|64"));
        InspectContainerResponse existing = inspectedContainer("docker-1", config, null, true, null);
        InspectContainerCmd inspect = mock(InspectContainerCmd.class);
        when(dockerClient.inspectContainerCmd("cloudbox-command-1")).thenReturn(inspect);
        when(dockerClient.inspectContainerCmd("docker-1")).thenReturn(inspect);
        when(inspect.exec()).thenReturn(existing);

        ContainerLaunchResult result = service.runContainer(
                "nginx:alpine", "cloudbox-command-1", "command-1", 1, 64, List.of());

        assertThat(result).isEqualTo(new ContainerLaunchResult("docker-1", List.of()));
        verify(dockerClient, never()).createContainerCmd(anyString());
        verify(dockerClient, never()).startContainerCmd(anyString());
        verify(service, never()).pullImage(anyString());
    }

    @Test
    void shouldNotReuseContainerWithoutMatchingOwnershipLabels() {
        DockerClient dockerClient = mock(DockerClient.class);
        ContainerExecutionService service = new ContainerExecutionService(
                dockerClient, properties(null, "0.0.0.0"));
        ContainerConfig config = mock(ContainerConfig.class);
        when(config.getLabels()).thenReturn(Map.of());
        InspectContainerCmd inspect = mock(InspectContainerCmd.class);
        when(dockerClient.inspectContainerCmd("cloudbox-command-1")).thenReturn(inspect);
        InspectContainerResponse foreign = inspectedContainer("foreign", config, null, true, null);
        when(inspect.exec()).thenReturn(foreign);

        assertThatThrownBy(() -> service.runContainer(
                "nginx:alpine", "cloudbox-command-1", "command-1", 1, 64, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("propriedade CloudBox nao foi comprovada");

        verify(dockerClient, never()).createContainerCmd(anyString());
    }

    @Test
    void shouldFailClearlyWhenCreatedContainerDoesNotRemainRunning() {
        DockerClient dockerClient = mock(DockerClient.class);
        ContainerExecutionService service = spy(new ContainerExecutionService(
                dockerClient, properties(null, "0.0.0.0")));
        doNothing().when(service).pullImage("failing:latest");
        InspectContainerCmd missing = mock(InspectContainerCmd.class);
        when(dockerClient.inspectContainerCmd("cloudbox-command-1")).thenReturn(missing);
        when(missing.exec()).thenThrow(new NotFoundException("missing"));

        CreateContainerCmd create = mock(CreateContainerCmd.class, Mockito.RETURNS_SELF);
        CreateContainerResponse created = new CreateContainerResponse();
        created.setId("docker-1");
        when(dockerClient.createContainerCmd("failing:latest")).thenReturn(create);
        when(create.exec()).thenReturn(created);
        when(dockerClient.startContainerCmd("docker-1")).thenReturn(mock(StartContainerCmd.class));

        InspectContainerResponse.ContainerState exited = mock(InspectContainerResponse.ContainerState.class);
        when(exited.getRunning()).thenReturn(false);
        when(exited.getStatus()).thenReturn("exited");
        when(exited.getExitCodeLong()).thenReturn(2L);
        InspectContainerCmd inspectCreated = mock(InspectContainerCmd.class);
        when(dockerClient.inspectContainerCmd("docker-1")).thenReturn(inspectCreated);
        InspectContainerResponse stopped = inspectedContainer("docker-1", null, exited, false, null);
        when(inspectCreated.exec()).thenReturn(stopped);

        assertThatThrownBy(() -> service.runContainer(
                "failing:latest", "cloudbox-command-1", "command-1", 1, 64, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nao permaneceu em execucao")
                .hasMessageContaining("status=exited")
                .hasMessageContaining("exitCode=2");
    }

    @Test
    void shouldRejectInvalidOrUnsafePortSpecifications() {
        DockerClient dockerClient = mock(DockerClient.class);
        ContainerExecutionService withoutAdvertiseAddress = new ContainerExecutionService(
                dockerClient, properties(null, "0.0.0.0"));
        PortSpec published = new PortSpec(80, null, PortProtocol.TCP, PortExposure.HTTP, null);

        assertThatThrownBy(() -> withoutAdvertiseAddress.runContainer(
                "nginx:alpine", "name", "command-1", 1, 64, List.of(published)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AGENT_ADVERTISE_ADDRESS");

        ContainerExecutionService service = new ContainerExecutionService(
                dockerClient, properties("node-a.example.test", "0.0.0.0"));
        assertThatThrownBy(() -> service.runContainer(
                "nginx:alpine", "name", "command-1", 1, 64, List.of(published, published)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Porta duplicada");
        assertThatThrownBy(() -> service.runContainer(
                "nginx:alpine", "name", "command-1", 1, 64,
                List.of(new PortSpec(53, null, PortProtocol.UDP, PortExposure.HTTP, null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exige protocolo TCP");
        assertThatThrownBy(() -> service.runContainer(
                "nginx:alpine", "name", "command-1", 1, 64,
                List.of(new PortSpec(80, 8080, PortProtocol.TCP, PortExposure.INTERNAL, null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INTERNAL");
        assertThatThrownBy(() -> service.runContainer(
                "nginx:alpine", "name", "command-1", 1, 64,
                java.util.Arrays.asList((PortSpec) null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("itens nulos");
    }

    private static AgentClientProperties properties(String advertiseAddress, String bindAddress) {
        AgentClientProperties properties = new AgentClientProperties();
        properties.setAdvertiseAddress(advertiseAddress);
        properties.setAutoDetectAdvertiseAddress(advertiseAddress != null);
        properties.setPortBindAddress(bindAddress);
        return properties;
    }

    private static InspectContainerResponse inspectedContainer(
            String id,
            ContainerConfig config,
            InspectContainerResponse.ContainerState state,
            boolean running,
            Ports ports) {
        InspectContainerResponse inspected = mock(InspectContainerResponse.class);
        when(inspected.getId()).thenReturn(id);
        when(inspected.getConfig()).thenReturn(config);
        InspectContainerResponse.ContainerState effectiveState = state;
        if (effectiveState == null) {
            effectiveState = mock(InspectContainerResponse.ContainerState.class);
            when(effectiveState.getRunning()).thenReturn(running);
        }
        when(inspected.getState()).thenReturn(effectiveState);
        if (ports != null) {
            NetworkSettings networkSettings = mock(NetworkSettings.class);
            when(networkSettings.getPorts()).thenReturn(ports);
            when(inspected.getNetworkSettings()).thenReturn(networkSettings);
        }
        return inspected;
    }
}
