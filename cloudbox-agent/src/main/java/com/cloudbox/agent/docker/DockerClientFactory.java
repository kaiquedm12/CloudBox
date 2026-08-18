package com.cloudbox.agent.docker;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;

@Configuration
public class DockerClientFactory {

    @Bean(destroyMethod = "close")
    DockerClient dockerClient(DockerClientProperties properties) {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(properties.getHost())
                .build();

        DockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .connectionTimeout(properties.getConnectionTimeout())
                .responseTimeout(properties.getResponseTimeout())
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }
}
