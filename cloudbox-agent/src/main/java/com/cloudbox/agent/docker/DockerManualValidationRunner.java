package com.cloudbox.agent.docker;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "cloudbox.agent.docker.validation", name = "enabled", havingValue = "true")
public class DockerManualValidationRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerManualValidationRunner.class);

    private final ContainerExecutionService executionService;
    private final String image;

    public DockerManualValidationRunner(
            ContainerExecutionService executionService,
            @Value("${cloudbox.agent.docker.validation.image:nginx:alpine}") String image) {
        this.executionService = executionService;
        this.image = image;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        String name = "cloudbox-validation-" + UUID.randomUUID().toString().substring(0, 8);
        String containerId = null;

        try {
            LOGGER.info("Validacao Docker: baixando imagem {}", image);
            executionService.pullImage(image);
            containerId = executionService.createContainer(image, name);
            LOGGER.info("Validacao Docker: container criado id={} nome={}", containerId, name);

            executionService.startContainer(containerId);
            ContainerStatus running = executionService.inspectContainer(containerId);
            LOGGER.info("Validacao Docker: status apos iniciar={}", running);
            if (!running.running()) {
                throw new IllegalStateException("O container nao permaneceu em execucao: " + running);
            }

            executionService.stopContainer(containerId);
            ContainerStatus stopped = executionService.inspectContainer(containerId);
            LOGGER.info("Validacao Docker: status apos parar={}", stopped);
            if (stopped.running()) {
                throw new IllegalStateException("O container permaneceu em execucao apos a parada: " + stopped);
            }

            LOGGER.info("Validacao Docker concluida com sucesso");
        } finally {
            if (containerId != null) {
                executionService.removeContainer(containerId);
                LOGGER.info("Validacao Docker: container temporario removido id={}", containerId);
            }
        }
    }
}
