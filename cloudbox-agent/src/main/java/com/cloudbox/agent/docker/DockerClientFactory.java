package com.cloudbox.agent.docker;

import java.net.URI;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;

@Configuration
public class DockerClientFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerClientFactory.class);
    private static final String WINDOWS_DOCKER_HOST = "npipe:////./pipe/docker_engine";
    private static final String UNIX_DOCKER_HOST = "unix:///var/run/docker.sock";

    @Bean(destroyMethod = "close")
    DockerClient dockerClient(DockerClientProperties properties) {
        URI dockerHost = resolveDockerHost(properties.getHost(), System.getProperty("os.name"));
        LOGGER.info("Conectando ao Docker em {}", dockerHost);

        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost.toString())
                .build();

        DockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .connectionTimeout(properties.getConnectionTimeout())
                .responseTimeout(properties.getResponseTimeout())
                .build();

        DockerClient client = DockerClientImpl.getInstance(config, httpClient);
        try {
            verifyConnection(client);
            return client;
        } catch (RuntimeException exception) {
            try {
                client.close();
            } catch (Exception closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    static void verifyConnection(DockerClient client) {
        try {
            client.pingCmd().exec();
            LOGGER.info("Docker acessivel; agente pronto para registrar o no e enviar heartbeats");
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Nao foi possivel acessar o Docker. O agente nao sera iniciado. "
                            + "Ao executar a imagem por docker run ou Docker Desktop, monte o socket: "
                            + "-v /var/run/docker.sock:/var/run/docker.sock "
                            + "(Desktop: Optional settings > Volumes). "
                            + "Defina DOCKER_HOST=unix:///var/run/docker.sock dentro do container. "
                            + "Confira se o daemon esta ligado, as permissoes do socket e a causa abaixo. "
                            + "Para outro endpoint, confira DOCKER_HOST e as configuracoes TLS.",
                    exception);
        }
    }

    static URI resolveDockerHost(String configuredHost, String osName) {
        if (configuredHost != null && !configuredHost.isBlank()) {
            return URI.create(configuredHost.trim());
        }

        boolean windows = osName != null
                && osName.toLowerCase(Locale.ROOT).contains("win");
        return URI.create(windows ? WINDOWS_DOCKER_HOST : UNIX_DOCKER_HOST);
    }
}
