package com.cloudbox.master.node;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.cloudbox.master.container.ContainerService;
import com.cloudbox.master.container.dto.PendingCommandResponse;
import com.cloudbox.master.security.AgentTokenValidator;

@RestController
@RequestMapping("/api/nodes")
public class NodeCommandController {

    private final ContainerService containerService;
    private final AgentTokenValidator agentTokenValidator;

    public NodeCommandController(ContainerService containerService,
                                 AgentTokenValidator agentTokenValidator) {
        this.containerService = containerService;
        this.agentTokenValidator = agentTokenValidator;
    }

    @Operation(
            summary = "Lista os comandos pendentes de um nó",
            description = "Retorna todos os containers com status PENDING alocados ao nó informado, "
                    + "para que o agente saiba o que executar. Requer o header "
                    + "'Authorization: Bearer {token}' com o token gerado no registro do nó. "
                    + "Se não houver comandos pendentes, retorna uma lista vazia.",
            security = @SecurityRequirement(name = "AgentToken"))
    @GetMapping("/{id}/pending-commands")
    public ResponseEntity<List<PendingCommandResponse>> pendingCommands(
            @PathVariable UUID id,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        agentTokenValidator.authorizeNode(id, authorizationHeader);
        return ResponseEntity.ok(containerService.findPendingCommands(id));
    }
}