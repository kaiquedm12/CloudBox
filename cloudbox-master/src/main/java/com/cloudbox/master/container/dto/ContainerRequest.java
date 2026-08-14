package com.cloudbox.master.container.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ContainerRequest(
        @NotBlank String imageName,
        @NotNull @Min(1) Integer cpuCores,
        @NotNull @Min(1) Integer memoryMb,
        @NotNull @Min(1) Integer diskMb) {
}
