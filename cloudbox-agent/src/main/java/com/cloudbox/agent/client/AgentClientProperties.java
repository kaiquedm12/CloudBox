package com.cloudbox.agent.client;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cloudbox.agent")
public class AgentClientProperties {

    private String masterUrl = "http://localhost:8080";
    private String name = defaultHostName();
    private Path tokenFile = Path.of(System.getProperty("user.home"), ".cloudbox", "agent-credentials.properties");

    public String getMasterUrl() {
        return masterUrl;
    }

    public void setMasterUrl(String masterUrl) {
        this.masterUrl = masterUrl;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Path getTokenFile() {
        return tokenFile;
    }

    public void setTokenFile(Path tokenFile) {
        this.tokenFile = tokenFile;
    }

    private static String defaultHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException exception) {
            return "cloudbox-agent";
        }
    }
}
