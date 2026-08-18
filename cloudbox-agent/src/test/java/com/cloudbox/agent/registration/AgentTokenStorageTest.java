package com.cloudbox.agent.registration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentTokenStorageTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldSaveAndLoadCredentials() {
        AgentTokenStorage storage = new AgentTokenStorage(temporaryDirectory.resolve("credentials.properties"));
        AgentCredentials expected = new AgentCredentials(UUID.randomUUID(), "secret-token");

        storage.save(expected);

        assertThat(storage.load()).contains(expected);
    }
}
