package com.cloudbox.agent.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PingCmd;

import org.junit.jupiter.api.Test;

class DockerClientFactoryTest {

    @Test
    void shouldAllowStartupWhenDockerResponds() {
        DockerClient client = mock(DockerClient.class);
        when(client.pingCmd()).thenReturn(mock(PingCmd.class));

        assertThatCode(() -> DockerClientFactory.verifyConnection(client)).doesNotThrowAnyException();
    }

    @Test
    void shouldBlockStartupWithSetupInstructionsWhenDockerIsUnavailable() {
        DockerClient client = mock(DockerClient.class);
        PingCmd ping = mock(PingCmd.class);
        when(client.pingCmd()).thenReturn(ping);
        RuntimeException failure = new RuntimeException("SocketException: Permission denied");
        when(ping.exec()).thenThrow(failure);

        assertThatThrownBy(() -> DockerClientFactory.verifyConnection(client))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("/var/run/docker.sock:/var/run/docker.sock")
                .hasMessageContaining("Docker Desktop")
                .hasMessageContaining("DOCKER_HOST")
                .hasCause(failure);
    }

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
