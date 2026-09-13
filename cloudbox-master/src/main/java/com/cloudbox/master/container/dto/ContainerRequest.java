package com.cloudbox.master.container.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record ContainerRequest(
        @NotBlank
        @Pattern(regexp = "\\S+", message = "O nome da imagem Docker não pode conter espaços")
        String imageName,
        @NotNull @Min(1) Integer cpuCores,
        @NotNull @Min(1) Integer memoryMb,
        @NotNull @Min(1) Integer diskMb,
        @Size(max = 32) List<@NotNull @Valid PortSpec> ports) {

    public ContainerRequest {
        ports = ports == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(ports));
    }

    public ContainerRequest(String imageName, Integer cpuCores, Integer memoryMb, Integer diskMb) {
        this(imageName, cpuCores, memoryMb, diskMb, List.of());
    }

    @AssertTrue(message = "ports não pode repetir a combinação containerPort/protocol")
    @JsonIgnore
    public boolean isPortSetUnique() {
        Set<String> keys = new HashSet<>();
        for (PortSpec port : ports) {
            if (port != null && !keys.add(port.containerPort() + ":" + port.protocol())) {
                return false;
            }
        }
        return true;
    }
}
