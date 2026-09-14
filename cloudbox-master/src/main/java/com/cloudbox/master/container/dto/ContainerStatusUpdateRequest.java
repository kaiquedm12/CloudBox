package com.cloudbox.master.container.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.cloudbox.master.container.ContainerStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Atualização de status reportada pelo agente para um container. O status só aceita RUNNING, ERROR ou STOPPED.")
public record ContainerStatusUpdateRequest(
        @Schema(description = "Novo status do container. Aceita apenas RUNNING, ERROR ou STOPPED — PENDING não é aceito neste endpoint.",
                allowableValues = {"RUNNING", "ERROR", "STOPPED"},
                example = "RUNNING")
        @NotNull ContainerStatus status,
        @Schema(description = "Identificador do container no Docker, obrigatório quando o status é RUNNING",
                example = "f3b5c6d7e8a901234567890abcdef0123456789abcdef0123456789abcdef01234")
        String dockerContainerId,
        @Schema(description = "Mensagem de erro, preenchida quando o status é ERROR",
                example = "falha ao puxar imagem: manifest unknown")
        String errorMessage,
        @Size(max = 64) List<@NotNull @Valid ObservedEndpointRequest> endpoints) {

    public ContainerStatusUpdateRequest {
        endpoints = endpoints == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(endpoints));
    }

    public ContainerStatusUpdateRequest(ContainerStatus status, String dockerContainerId, String errorMessage) {
        this(status, dockerContainerId, errorMessage, null);
    }
}
