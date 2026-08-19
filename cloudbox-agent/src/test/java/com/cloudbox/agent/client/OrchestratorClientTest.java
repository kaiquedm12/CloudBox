package com.cloudbox.agent.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class OrchestratorClientTest {

    private HttpServer server;
    private OrchestratorClient client;
    private final AtomicReference<String> registrationBody = new AtomicReference<>();
    private final AtomicReference<String> heartbeatBody = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> statusBody = new AtomicReference<>();
    private final UUID nodeId = UUID.randomUUID();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/nodes/register", exchange -> {
            registrationBody.set(readBody(exchange));
            respond(exchange, 201, "{\"id\":\"" + nodeId + "\",\"token\":\"agent-token\"}");
        });
        server.createContext("/api/nodes/" + nodeId + "/heartbeat", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            heartbeatBody.set(readBody(exchange));
            respond(exchange, 204, "");
        });
        server.createContext("/api/nodes/" + nodeId + "/pending-commands", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "[{\"containerId\":\"" + nodeId
                    + "\",\"imageName\":\"nginx:alpine\",\"cpuCores\":1,\"memoryMb\":64,\"diskMb\":128}]");
        });
        server.createContext("/api/containers/" + nodeId + "/status", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            statusBody.set(readBody(exchange));
            respond(exchange, 200, "{}");
        });
        server.start();

        AgentClientProperties properties = new AgentClientProperties();
        properties.setMasterUrl("http://localhost:" + server.getAddress().getPort());
        client = new OrchestratorClient(RestClient.builder(), properties);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void shouldUseMasterRegistrationContract() {
        NodeRegisterResponse response = client.register(new NodeRegisterRequest(
                "node-a", java.math.BigDecimal.valueOf(8), 16_384, 200_000));

        assertThat(response).isEqualTo(new NodeRegisterResponse(nodeId, "agent-token"));
        assertThat(registrationBody.get())
                .contains("\"name\":\"node-a\"")
                .contains("\"cpuTotal\":8")
                .contains("\"ramTotalMb\":16384")
                .contains("\"diskTotalMb\":200000");
    }

    @Test
    void shouldUseMasterHeartbeatContractAndBearerToken() {
        client.heartbeat(nodeId, "agent-token", new HeartbeatRequest(
                java.math.BigDecimal.valueOf(6.5), 12_000, 150_000, null));

        assertThat(authorization.get()).isEqualTo("Bearer agent-token");
        assertThat(heartbeatBody.get())
                .contains("\"cpuFree\":6.5")
                .contains("\"ramFreeMb\":12000")
                .contains("\"diskFreeMb\":150000")
                .contains("\"temperatureCelsius\":null");
    }

    @Test
    void shouldReadPendingCommandsWithBearerToken() {
        assertThat(client.pendingCommands(nodeId, "agent-token"))
                .containsExactly(new PendingCommand(nodeId, "nginx:alpine", 1, 64, 128));
        assertThat(authorization.get()).isEqualTo("Bearer agent-token");
    }

    @Test
    void shouldReportContainerStatusWithBearerToken() {
        client.updateContainerStatus(nodeId, "agent-token",
                new ContainerStatusUpdateRequest("RUNNING", "docker-123", null));

        assertThat(authorization.get()).isEqualTo("Bearer agent-token");
        assertThat(statusBody.get())
                .contains("\"status\":\"RUNNING\"")
                .contains("\"dockerContainerId\":\"docker-123\"");
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }
}
