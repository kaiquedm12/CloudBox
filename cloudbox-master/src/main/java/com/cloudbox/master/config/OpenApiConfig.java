package com.cloudbox.master.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI cloudBoxOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CloudBox Master API")
                        .description("API do orquestrador CloudBox: registro de nós, heartbeat, agendamento, "
                                + "comandos pendentes e atualização de status de containers.")
                        .version("0.1.0"))
                .components(new Components().addSecuritySchemes("AgentToken",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .description("Token do nó gerado no registro. Enviar como Authorization: Bearer {token}")));
    }
}