package com.cloudbox.agent.client;

import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.cloudbox.agent.docker.ExecutionOptions;
import tools.jackson.databind.annotation.JsonDeserialize;

public record PendingCommand(
        UUID containerId,
        String action,
        String imageName,
        Integer cpuCores,
        Integer memoryMb,
        Integer diskMb,
        List<PortSpec> ports,
        String dockerContainerId,
        @JsonDeserialize(contentUsing = CommandStringDeserializer.class) Map<String, String> environment,
        @JsonDeserialize(contentUsing = CommandStringDeserializer.class) List<String> command,
        @JsonDeserialize(contentUsing = CommandStringDeserializer.class) List<String> args,
        RestartPolicySpec restartPolicy,
        List<String> requiredCapabilities,
        @JsonAnySetter Map<String, Object> additionalOptions) {

    public PendingCommand {
        ports = ports == null ? List.of() : List.copyOf(ports);
        environment = environment == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(environment));
        command = command == null ? null : Collections.unmodifiableList(new java.util.ArrayList<>(command));
        args = args == null ? null : Collections.unmodifiableList(new java.util.ArrayList<>(args));
        requiredCapabilities = requiredCapabilities == null ? List.of() : List.copyOf(requiredCapabilities);
        additionalOptions = additionalOptions == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(additionalOptions));
    }

    public PendingCommand(UUID containerId, String action, String imageName, Integer cpuCores,
                          Integer memoryMb, Integer diskMb, List<PortSpec> ports, String dockerContainerId) {
        this(containerId, action, imageName, cpuCores, memoryMb, diskMb, ports, dockerContainerId,
                null, null, null, null, null, null);
    }

    public ExecutionOptions executionOptions() {
        if (!AgentCapabilities.CURRENT.features().containsAll(requiredCapabilities)) {
            throw new IllegalArgumentException("UNSUPPORTED_CAPABILITY: o comando exige recurso nao implementado");
        }
        for (var option : additionalOptions.entrySet()) {
            Object value = option.getValue();
            boolean compatible = switch (option.getKey()) {
                case "diskMode" -> "REQUEST_ONLY".equals(value);
                case "ephemeralDiskMb" -> value instanceof Integer size && size > 0 && size.equals(diskMb);
                case "secretRefs", "volumes", "resolvedVolumes" -> value == null || value instanceof List<?> list && list.isEmpty();
                case "healthCheck" -> value == null;
                default -> false;
            };
            if (!compatible) throw new IllegalArgumentException("UNSUPPORTED_SERVICE_OPTION: configuracao nao implementada ou conflitante");
        }
        return new ExecutionOptions(environment, command, args, restartPolicy);
    }

    @Override
    public String toString() {
        return "PendingCommand[containerId=" + containerId + ", action=" + action + "]";
    }

    public PendingCommand(
            UUID containerId,
            String imageName,
            Integer cpuCores,
            Integer memoryMb,
            Integer diskMb) {
        this(containerId, "START", imageName, cpuCores, memoryMb, diskMb, List.of(), null);
    }

    public PendingCommand(
            UUID containerId,
            String imageName,
            Integer cpuCores,
            Integer memoryMb,
            Integer diskMb,
            List<PortSpec> ports) {
        this(containerId, "START", imageName, cpuCores, memoryMb, diskMb, ports, null);
    }

    public PendingCommand(
            UUID containerId,
            String action,
            String imageName,
            Integer cpuCores,
            Integer memoryMb,
            Integer diskMb,
            String dockerContainerId) {
        this(containerId, action, imageName, cpuCores, memoryMb, diskMb, List.of(), dockerContainerId);
    }
}
