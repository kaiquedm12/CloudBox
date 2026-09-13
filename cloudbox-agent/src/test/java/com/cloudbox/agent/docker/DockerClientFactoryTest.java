package com.cloudbox.agent.docker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DockerClientFactoryTest {

    @Test
    void shouldUseNamedPipeByDefaultOnWindows() {
        assertThat(DockerClientFactory.resolveDockerHost(null, "Windows 11"))
                .hasToString("npipe:////./pipe/docker_engine");
    }

    @Test
    void shouldUseUnixSocketByDefaultOnLinux() {
        assertThat(DockerClientFactory.resolveDockerHost("", "Linux"))
                .hasToString("unix:///var/run/docker.sock");
    }

    @Test
    void shouldHonorConfiguredDockerHost() {
        assertThat(DockerClientFactory.resolveDockerHost(" tcp://docker.example:2375 ", "Windows 11"))
                .hasToString("tcp://docker.example:2375");
    }
}
