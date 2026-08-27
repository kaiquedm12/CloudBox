package com.cloudbox.master.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudbox.master.config.WebSocketConfig;
import com.cloudbox.master.container.ContainerStatus;
import com.cloudbox.master.node.NodeStatus;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
        classes = ClusterStatusWebSocketTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ClusterStatusWebSocketTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ClusterStatusPublisher publisher;

    @Autowired
    private ObjectMapper objectMapper;

    private WebSocket client;

    @AfterEach
    void disconnect() {
        if (client != null) {
            client.sendClose(WebSocket.NORMAL_CLOSURE, "teste concluído").join();
        }
    }

    @Test
    void simpleWebSocketClientReceivesNodeAndContainerStatusChangesInRealTime() throws Exception {
        MessageListener listener = new MessageListener();
        client = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/cluster-status"), listener)
                .join();

        UUID nodeId = UUID.randomUUID();
        publisher.publishNodeStatusChange(nodeId, NodeStatus.OFFLINE, NodeStatus.ONLINE);

        JsonNode nodeMessage = objectMapper.readTree(listener.messages.poll(5, TimeUnit.SECONDS));
        assertThat(nodeMessage.get("eventType").asText()).isEqualTo("NODE_STATUS_CHANGED");
        assertThat(nodeMessage.get("resourceType").asText()).isEqualTo("NODE");
        assertThat(nodeMessage.get("resourceId").asText()).isEqualTo(nodeId.toString());
        assertThat(nodeMessage.get("previousStatus").asText()).isEqualTo("OFFLINE");
        assertThat(nodeMessage.get("currentStatus").asText()).isEqualTo("ONLINE");
        assertThat(nodeMessage.get("occurredAt").asText()).isNotBlank();

        UUID containerId = UUID.randomUUID();
        publisher.publishContainerStatusChange(
                containerId, ContainerStatus.PENDING, ContainerStatus.RUNNING);

        JsonNode containerMessage = objectMapper.readTree(listener.messages.poll(5, TimeUnit.SECONDS));
        assertThat(containerMessage.get("eventType").asText()).isEqualTo("CONTAINER_STATUS_CHANGED");
        assertThat(containerMessage.get("resourceType").asText()).isEqualTo("CONTAINER");
        assertThat(containerMessage.get("resourceId").asText()).isEqualTo(containerId.toString());
        assertThat(containerMessage.get("previousStatus").asText()).isEqualTo("PENDING");
        assertThat(containerMessage.get("currentStatus").asText()).isEqualTo("RUNNING");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class
    })
    @Import({WebSocketConfig.class, ClusterStatusWebSocketHandler.class, ClusterStatusPublisher.class})
    static class TestApplication {
    }

    private static final class MessageListener implements WebSocket.Listener {

        private final LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final StringBuilder currentMessage = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            currentMessage.append(data);
            if (last) {
                messages.add(currentMessage.toString());
                currentMessage.setLength(0);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }
    }
}
