package com.cloudbox.agent.registration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.cloudbox.agent.client.AgentClientProperties;

@Component
public class AgentTokenStorage {

    private static final String NODE_ID = "nodeId";
    private static final String TOKEN = "token";

    private final Path tokenFile;

    @Autowired
    public AgentTokenStorage(AgentClientProperties properties) {
        this(properties.getTokenFile());
    }

    AgentTokenStorage(Path tokenFile) {
        this.tokenFile = tokenFile;
    }

    public void save(AgentCredentials credentials) {
        Properties values = new Properties();
        values.setProperty(NODE_ID, credentials.nodeId().toString());
        values.setProperty(TOKEN, credentials.token());

        try {
            Path parent = tokenFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(tokenFile)) {
                values.store(output, "CloudBox agent credentials");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Nao foi possivel salvar o token do agente em " + tokenFile, exception);
        }
    }

    public Optional<AgentCredentials> load() {
        if (Files.notExists(tokenFile)) {
            return Optional.empty();
        }

        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(tokenFile)) {
            values.load(input);
            return Optional.of(new AgentCredentials(
                    UUID.fromString(values.getProperty(NODE_ID)),
                    values.getProperty(TOKEN)));
        } catch (IOException | IllegalArgumentException | NullPointerException exception) {
            throw new IllegalStateException("Credenciais do agente invalidas em " + tokenFile, exception);
        }
    }
}
