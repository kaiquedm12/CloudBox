package com.cloudbox.agent.docker;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cloudbox.agent.docker")
public class DockerClientProperties {

    private String host = "unix:///var/run/docker.sock";
    private Duration connectionTimeout = Duration.ofSeconds(5);
    private Duration responseTimeout = Duration.ofSeconds(30);

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Duration connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public Duration getResponseTimeout() {
        return responseTimeout;
    }

    public void setResponseTimeout(Duration responseTimeout) {
        this.responseTimeout = responseTimeout;
    }
}
