package com.cloudbox.agent.docker;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.cloudbox.agent.client.AgentClientProperties;
import com.cloudbox.agent.client.RestartPolicySpec;
import com.github.dockerjava.api.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Opt-in: creates only UUID-labelled Nginx test containers, without published ports or volumes. */
@EnabledIfEnvironmentVariable(named = "CLOUDBOX_TEST_DOCKER_HOST", matches = ".+")
class ConfiguredContainerDockerTest {
    @Test
    void shouldApplyConfigurationAndReuseOnlyMatchingInstancesOnRealDocker() throws Exception {
        DockerClientProperties dockerProperties = new DockerClientProperties();
        dockerProperties.setHost(System.getenv("CLOUDBOX_TEST_DOCKER_HOST"));
        try (var docker = new DockerClientFactory().dockerClient(dockerProperties)) {
            var service = new ContainerExecutionService(docker, new AgentClientProperties());
            for (int variant = 0; variant < 4; variant++) {
                String id = UUID.randomUUID().toString();
                String name = "cloudbox-config-test-" + id;
                List<String> command = switch (variant) {
                    case 1 -> List.of("nginx", "-g", "daemon off;");
                    case 3 -> List.of("nginx");
                    default -> null;
                };
                List<String> args = switch (variant) {
                    case 2 -> List.of("nginx", "-g", "daemon off;");
                    case 3 -> List.of("-g", "daemon off;");
                    default -> null;
                };
                var options = new ExecutionOptions(Map.of("CLOUDBOX_CHECK", "literal=$VALUE with spaces"), command, args,
                        new RestartPolicySpec("ON_FAILURE", 3));
                try {
                    var result = service.runContainer("nginx:alpine", name, id, 1, 64, List.of(), options);
                    var inspect = docker.inspectContainerCmd(result.dockerContainerId()).exec();
                    assertThat(inspect.getConfig().getEnv()).contains("CLOUDBOX_CHECK=literal=$VALUE with spaces");
                    assertThat(inspect.getHostConfig().getRestartPolicy().getName()).isEqualTo("on-failure");
                    assertThat(inspect.getHostConfig().getRestartPolicy().getMaximumRetryCount()).isEqualTo(3);
                    if (command != null) assertThat(inspect.getConfig().getEntrypoint()).containsExactlyElementsOf(command);
                    else assertThat(inspect.getConfig().getEntrypoint()).containsExactly("/docker-entrypoint.sh");
                    if (args != null) assertThat(inspect.getConfig().getCmd()).containsExactlyElementsOf(args);
                    else if (command != null) assertThat(inspect.getConfig().getCmd()).isNullOrEmpty();
                    else assertThat(inspect.getConfig().getCmd()).containsExactly("nginx", "-g", "daemon off;");
                    assertThat(result.endpoints()).isEmpty();
                    var reused = service.runContainer("nginx:alpine", name, id, 1, 64, List.of(), options);
                    assertThat(reused.dockerContainerId()).isEqualTo(result.dockerContainerId());
                    assertThatThrownBy(() -> service.runContainer("nginx:alpine", name, id, 1, 64, List.of(),
                            new ExecutionOptions(Map.of("CLOUDBOX_CHECK", "changed"), command, args, options.restartPolicy())))
                            .isInstanceOf(IllegalStateException.class).hasMessageContaining("especificacao");
                } finally {
                    try {
                        var owned = docker.inspectContainerCmd(name).exec();
                        assertThat(owned.getConfig().getLabels()).containsEntry(ContainerExecutionService.MANAGED_LABEL, "true")
                                .containsEntry(ContainerExecutionService.CONTAINER_ID_LABEL, id);
                        docker.removeContainerCmd(owned.getId()).withForce(true).exec();
                    } catch (NotFoundException ignored) {
                        // Failure before creation left no owned container to remove.
                    }
                }
            }
        }
    }
}
