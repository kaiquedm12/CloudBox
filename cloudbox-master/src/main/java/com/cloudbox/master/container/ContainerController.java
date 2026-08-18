package com.cloudbox.master.container;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import com.cloudbox.master.container.dto.ContainerRequest;
import com.cloudbox.master.container.dto.ContainerResponse;
import com.cloudbox.master.container.dto.ContainerStatusUpdateRequest;
import com.cloudbox.master.security.AgentTokenValidator;

@RestController
@RequestMapping("/api/containers")
public class ContainerController {

    private final ContainerService containerService;
    private final AgentTokenValidator agentTokenValidator;

    public ContainerController(ContainerService containerService, AgentTokenValidator agentTokenValidator) {
        this.containerService = containerService;
        this.agentTokenValidator = agentTokenValidator;
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody ContainerRequest request) {
        Optional<ContainerResponse> response = containerService.create(request);
        if (response.isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Nenhum nó disponível com recursos suficientes no momento"));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response.get());
    }

    @GetMapping
    public ResponseEntity<List<ContainerResponse>> findAll() {
        return ResponseEntity.ok(containerService.findAll());
    }

    @Operation(
            summary = "Atualiza o status de um container reportado pelo agente",
            description = "Permite que o agente reporte o resultado da execução de um container: RUNNING "
                    + "(preenchendo dockerContainerId), ERROR (preenchendo errorMessage) ou STOPPED. "
                    + "PENDING não é aceito neste endpoint. Requer o header "
                    + "'Authorization: Bearer {token}' com o token do nó que possui o container.",
            security = @SecurityRequirement(name = "AgentToken"))
    @PostMapping("/{id}/status")
    public ResponseEntity<ContainerResponse> updateStatus(
            @PathVariable UUID id,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ContainerStatusUpdateRequest request) {
        agentTokenValidator.authorizeContainer(id, authorizationHeader);
        return ResponseEntity.ok(containerService.updateStatus(id, request));
    }
}