package com.cloudbox.master.container.dto;

import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Comando pendente que um nó deve executar. Retornado ao agente em GET /api/nodes/{id}/pending-commands.")
public record PendingCommandResponse(
        @Schema(description = "Identificador do container no orquestrador", example = "0c0f7c3d-7a4e-4f1b-9b1c-3e2d5a6b7c8d")
        UUID containerId,
        @Schema(description = "Nome da imagem Docker a executar", example = "nginx:1.27")
        String imageName,
        @Schema(description = "Número de núcleos de CPU reservados", example = "1")
        Integer cpuCores,
        @Schema(description = "Memória reservada em MB", example = "512")
        Integer memoryMb,
        @Schema(description = "Disco solicitado em MB; não representa quota aplicada", example = "128")
        Integer diskMb,
        List<PortSpec> ports) {

    public PendingCommandResponse {
        ports = ports == null ? List.of() : List.copyOf(ports);
    }

    public PendingCommandResponse(UUID containerId, String imageName, Integer cpuCores,
                                  Integer memoryMb, Integer diskMb) {
        this(containerId, imageName, cpuCores, memoryMb, diskMb, List.of());
    }
}
