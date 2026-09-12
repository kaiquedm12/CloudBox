package com.cloudbox.agent.docker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.cloudbox.agent.client.AgentClientProperties;
import com.cloudbox.agent.client.ContainerEndpoint;
import com.cloudbox.agent.client.NetworkAddressValidator;
import com.cloudbox.agent.client.PortExposure;
import com.cloudbox.agent.client.PortProtocol;
import com.cloudbox.agent.client.PortSpec;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.ContainerConfig;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.InternetProtocol;
import com.github.dockerjava.api.model.NetworkSettings;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.command.PullImageResultCallback;

@Service
public class ContainerExecutionService {

    static final String MANAGED_LABEL = "com.cloudbox.managed";
    static final String CONTAINER_ID_LABEL = "com.cloudbox.container-id";
    static final String SPEC_LABEL = "com.cloudbox.spec";
    private static final int MAX_PORTS = 32;

    private final DockerClient dockerClient;
    private final AgentClientProperties properties;

    public ContainerExecutionService(DockerClient dockerClient, AgentClientProperties properties) {
        this.dockerClient = dockerClient;
        this.properties = properties;
    }

    public void pullImage(String image) {
        try {
            dockerClient.pullImageCmd(image)
                    .exec(new PullImageResultCallback())
                    .awaitCompletion();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Download da imagem Docker interrompido: " + image, exception);
        }
    }

    public String createContainer(String image, String name) {
        CreateContainerResponse response = dockerClient.createContainerCmd(image)
                .withName(name)
                .exec();
        return response.getId();
    }

    public String runContainer(String image, String name, int cpuCores, int memoryMb) {
        pullImage(image);
        CreateContainerResponse response = dockerClient.createContainerCmd(image)
                .withName(name)
                .withHostConfig(HostConfig.newHostConfig()
                        .withNanoCPUs(cpuCores * 1_000_000_000L)
                        .withMemory(memoryMb * 1024L * 1024L))
                .exec();
        String containerId = response.getId();
        startContainer(containerId);
        return containerId;
    }

    public ContainerLaunchResult runContainer(
            String image,
            String name,
            String logicalContainerId,
            int cpuCores,
            int memoryMb,
            List<PortSpec> ports) {
        List<ResolvedPort> resolvedPorts = validateAndResolvePorts(ports);
        String specIdentity = specIdentity(image, cpuCores, memoryMb, resolvedPorts);

        InspectContainerResponse existing = inspectIfPresent(name);
        if (existing != null) {
            return reuseOwnedContainer(existing, image, logicalContainerId, specIdentity, resolvedPorts);
        }

        pullImage(image);
        try {
            return createAndStartContainer(
                    image, name, logicalContainerId, cpuCores, memoryMb, resolvedPorts, specIdentity);
        } catch (ConflictException exception) {
            InspectContainerResponse racedContainer = inspectIfPresent(name);
            if (racedContainer == null) {
                throw exception;
            }
            return reuseOwnedContainer(
                    racedContainer, image, logicalContainerId, specIdentity, resolvedPorts);
        }
    }

    public void startContainer(String containerId) {
        dockerClient.startContainerCmd(containerId).exec();
    }

    public void stopContainer(String containerId) {
        dockerClient.stopContainerCmd(containerId).exec();
    }

    public ContainerStatus inspectContainer(String containerId) {
        InspectContainerResponse response = dockerClient.inspectContainerCmd(containerId).exec();
        InspectContainerResponse.ContainerState state = response.getState();
        return new ContainerStatus(
                response.getId(),
                state == null ? null : state.getStatus(),
                state != null && Boolean.TRUE.equals(state.getRunning()),
                state == null ? null : state.getExitCodeLong());
    }

    public void removeContainer(String containerId) {
        dockerClient.removeContainerCmd(containerId).withForce(true).exec();
    }

    private ContainerLaunchResult createAndStartContainer(
            String image,
            String name,
            String logicalContainerId,
            int cpuCores,
            int memoryMb,
            List<ResolvedPort> ports,
            String specIdentity) {
        Ports portBindings = new Ports();
        List<ExposedPort> exposedPorts = new ArrayList<>();
        for (ResolvedPort port : ports) {
            ExposedPort exposedPort = exposedPort(port.spec());
            exposedPorts.add(exposedPort);
            if (port.spec().exposure() != PortExposure.INTERNAL) {
                Ports.Binding binding = port.spec().hostPort() == null
                        ? Ports.Binding.bindIp(port.bindAddress())
                        : Ports.Binding.bindIpAndPort(port.bindAddress(), port.spec().hostPort());
                portBindings.bind(exposedPort, binding);
            }
        }

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withNanoCPUs(cpuCores * 1_000_000_000L)
                .withMemory(memoryMb * 1024L * 1024L)
                .withPortBindings(portBindings);
        CreateContainerResponse response = dockerClient.createContainerCmd(image)
                .withName(name)
                .withLabels(Map.of(
                        MANAGED_LABEL, "true",
                        CONTAINER_ID_LABEL, logicalContainerId,
                        SPEC_LABEL, specIdentity))
                .withExposedPorts(exposedPorts)
                .withHostConfig(hostConfig)
                .exec();
        String containerId = response.getId();
        startContainer(containerId);
        return inspectLaunchResult(containerId, ports);
    }

    private ContainerLaunchResult reuseOwnedContainer(
            InspectContainerResponse existing,
            String image,
            String logicalContainerId,
            String specIdentity,
            List<ResolvedPort> ports) {
        ContainerConfig config = existing.getConfig();
        Map<String, String> labels = config == null ? null : config.getLabels();
        boolean owned = labels != null
                && "true".equals(labels.get(MANAGED_LABEL))
                && logicalContainerId.equals(labels.get(CONTAINER_ID_LABEL));
        if (!owned) {
            throw new IllegalStateException(
                    "Ja existe um container com o nome solicitado, mas sua propriedade CloudBox nao foi comprovada");
        }
        if (!Objects.equals(image, config.getImage()) || !Objects.equals(specIdentity, labels.get(SPEC_LABEL))) {
            throw new IllegalStateException(
                    "O container CloudBox existente nao corresponde a especificacao pendente");
        }

        InspectContainerResponse.ContainerState state = existing.getState();
        if (state == null || !Boolean.TRUE.equals(state.getRunning())) {
            startContainer(existing.getId());
        }
        return inspectLaunchResult(existing.getId(), ports);
    }

    private InspectContainerResponse inspectIfPresent(String name) {
        try {
            return dockerClient.inspectContainerCmd(name).exec();
        } catch (NotFoundException exception) {
            return null;
        }
    }

    private ContainerLaunchResult inspectLaunchResult(String containerId, List<ResolvedPort> ports) {
        InspectContainerResponse inspected = dockerClient.inspectContainerCmd(containerId).exec();
        InspectContainerResponse.ContainerState state = inspected.getState();
        if (state == null || !Boolean.TRUE.equals(state.getRunning())) {
            String status = state == null ? "desconhecido" : state.getStatus();
            Long exitCode = state == null ? null : state.getExitCodeLong();
            throw new IllegalStateException("O container Docker foi criado, mas nao permaneceu em execucao"
                    + " (status=" + status + ", exitCode=" + exitCode + ")");
        }
        List<ContainerEndpoint> endpoints = observedEndpoints(inspected, ports);
        return new ContainerLaunchResult(inspected.getId(), endpoints);
    }

    private List<ContainerEndpoint> observedEndpoints(
            InspectContainerResponse inspected,
            List<ResolvedPort> requestedPorts) {
        List<ResolvedPort> publishedPorts = requestedPorts.stream()
                .filter(port -> port.spec().exposure() != PortExposure.INTERNAL)
                .toList();
        if (publishedPorts.isEmpty()) {
            return List.of();
        }

        NetworkSettings networkSettings = inspected.getNetworkSettings();
        Ports observedPorts = networkSettings == null ? null : networkSettings.getPorts();
        Map<ExposedPort, Ports.Binding[]> bindings = observedPorts == null ? null : observedPorts.getBindings();
        List<ContainerEndpoint> endpoints = new ArrayList<>();
        for (ResolvedPort requested : publishedPorts) {
            Ports.Binding[] observedBindings = bindings == null ? null : bindings.get(exposedPort(requested.spec()));
            if (observedBindings == null || observedBindings.length == 0) {
                throw new IllegalStateException("Docker nao reportou o binding da porta "
                        + requested.spec().containerPort() + "/" + requested.spec().protocol());
            }
            for (Ports.Binding binding : observedBindings) {
                endpoints.add(toEndpoint(requested, binding));
            }
        }
        return List.copyOf(endpoints);
    }

    private ContainerEndpoint toEndpoint(ResolvedPort requested, Ports.Binding binding) {
        if (binding == null || binding.getHostPortSpec() == null || binding.getHostPortSpec().isBlank()) {
            throw new IllegalStateException("Docker reportou um binding sem porta do host para "
                    + requested.spec().containerPort() + "/" + requested.spec().protocol());
        }
        int hostPort;
        try {
            hostPort = Integer.parseInt(binding.getHostPortSpec());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "Docker reportou uma porta do host invalida: " + binding.getHostPortSpec(), exception);
        }
        if (hostPort < 1 || hostPort > 65_535) {
            throw new IllegalStateException("Docker reportou uma porta do host fora do intervalo: " + hostPort);
        }
        String address = binding.getHostIp();
        if (address == null || address.isBlank()) {
            address = requested.bindAddress();
        }
        return new ContainerEndpoint(
                requested.spec().containerPort(), hostPort, requested.spec().protocol(), address);
    }

    private List<ResolvedPort> validateAndResolvePorts(List<PortSpec> portSpecs) {
        List<PortSpec> ports = portSpecs == null ? List.of() : new ArrayList<>(portSpecs);
        if (ports.size() > MAX_PORTS) {
            throw new IllegalArgumentException("A especificacao excede o limite de 32 portas");
        }

        Set<PortKey> uniquePorts = new HashSet<>();
        List<ResolvedPort> resolved = new ArrayList<>();
        for (PortSpec port : ports) {
            if (port == null) {
                throw new IllegalArgumentException("A lista de portas nao pode conter itens nulos");
            }
            requirePortNumber(port.containerPort(), "containerPort");
            if (port.hostPort() != null) {
                requirePortNumber(port.hostPort(), "hostPort");
            }
            if (!uniquePorts.add(new PortKey(port.containerPort(), port.protocol()))) {
                throw new IllegalArgumentException("Porta duplicada: "
                        + port.containerPort() + "/" + port.protocol());
            }
            validateExposure(port);

            String bindAddress = null;
            if (port.exposure() != PortExposure.INTERNAL) {
                if (properties.getAdvertiseAddress() == null) {
                    throw new IllegalStateException(
                            "AGENT_ADVERTISE_ADDRESS e obrigatorio para executar workloads com portas publicadas");
                }
                bindAddress = port.bindAddress() == null
                        ? properties.getPortBindAddress()
                        : NetworkAddressValidator.requireIpLiteral(port.bindAddress(), "bindAddress");
            }
            resolved.add(new ResolvedPort(port, bindAddress));
        }
        return List.copyOf(resolved);
    }

    private static void validateExposure(PortSpec port) {
        if (port.exposure() == PortExposure.INTERNAL) {
            if (port.hostPort() != null || port.bindAddress() != null) {
                throw new IllegalArgumentException("Porta INTERNAL nao aceita hostPort nem bindAddress");
            }
            return;
        }
        if ((port.exposure() == PortExposure.HTTP || port.exposure() == PortExposure.TCP)
                && port.protocol() != PortProtocol.TCP) {
            throw new IllegalArgumentException(port.exposure() + " exige protocolo TCP");
        }
        if (port.exposure() == PortExposure.UDP && port.protocol() != PortProtocol.UDP) {
            throw new IllegalArgumentException("Exposicao UDP exige protocolo UDP");
        }
    }

    private static void requirePortNumber(Integer port, String fieldName) {
        if (port == null || port < 1 || port > 65_535) {
            throw new IllegalArgumentException(fieldName + " deve estar entre 1 e 65535");
        }
    }

    private static ExposedPort exposedPort(PortSpec port) {
        InternetProtocol protocol = port.protocol() == PortProtocol.UDP
                ? InternetProtocol.UDP
                : InternetProtocol.TCP;
        return new ExposedPort(port.containerPort(), protocol);
    }

    private static String specIdentity(
            String image,
            int cpuCores,
            int memoryMb,
            List<ResolvedPort> ports) {
        StringBuilder identity = new StringBuilder(image)
                .append('|').append(cpuCores)
                .append('|').append(memoryMb);
        ports.stream()
                .sorted((left, right) -> {
                    int byPort = Integer.compare(left.spec().containerPort(), right.spec().containerPort());
                    return byPort != 0 ? byPort : left.spec().protocol().compareTo(right.spec().protocol());
                })
                .forEach(port -> identity
                        .append('|').append(port.spec().containerPort())
                        .append('/').append(port.spec().protocol())
                        .append('/').append(port.spec().exposure())
                        .append('/').append(port.spec().hostPort())
                        .append('/').append(port.bindAddress()));
        return identity.toString();
    }

    private record ResolvedPort(PortSpec spec, String bindAddress) {
    }

    private record PortKey(int containerPort, PortProtocol protocol) {
    }
}
