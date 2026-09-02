package com.cloudbox.master.container.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ContainerRequest(
        @NotBlank
        @Pattern(regexp = "\\S+", message = "O nome da imagem Docker não pode conter espaços")
        String imageName,
        @NotNull @Min(1) Integer cpuCores,
        @NotNull @Min(1) Integer memoryMb,
        @NotNull @Min(1) Integer diskMb) {
}
