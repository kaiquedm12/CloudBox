package com.cloudbox.agent.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    private final AtomicReference<String> pendingExtraFields = new AtomicReference<>("");
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
                    + "\",\"action\":\"START\",\"imageName\":\"nginx:alpine\",\"cpuCores\":1,"
                    + "\"memoryMb\":64,\"diskMb\":128,\"dockerContainerId\":null,"
                    + "\"ports\":[{\"containerPort\":80,\"hostPort\":null,\"protocol\":\"TCP\","
                    + "\"exposure\":\"HTTP\",\"bindAddress\":null}]" + pendingExtraFields.get() + "}]");
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
                "node-a", java.math.BigDecimal.valueOf(8), 16_384, 200_000, "node-a.example.test"));

        assertThat(response).isEqualTo(new NodeRegisterResponse(nodeId, "agent-token"));
        assertThat(registrationBody.get())
                .contains("\"name\":\"node-a\"")
                .contains("\"cpuTotal\":8")
                .contains("\"ramTotalMb\":16384")
                .contains("\"diskTotalMb\":200000")
                .contains("\"advertiseAddress\":\"node-a.example.test\"");
        assertThat(registrationBody.get()).contains("\"contractVersion\":2", "ENVIRONMENT", "COMMAND_ARGS",
                "RESTART_POLICY", "\"ephemeralDiskQuota\":false", "\"volumeQuota\":false");
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
        assertThat(heartbeatBody.get()).contains("\"capabilities\":", "\"contractVersion\":2");
    }

    @Test
    void shouldReadPendingCommandsWithBearerToken() {
        assertThat(client.pendingCommands(nodeId, "agent-token"))
                .containsExactly(new PendingCommand(nodeId, "START", "nginx:alpine", 1, 64, 128,
                        java.util.List.of(new PortSpec(
                                80, null, PortProtocol.TCP, PortExposure.HTTP, null)), null));
        assertThat(authorization.get()).isEqualTo("Bearer agent-token");
    }

    @Test
    void shouldReadConfiguredCommandWithoutExpandingArguments() {
        pendingExtraFields.set(",\"environment\":{\"GREETING\":\"hello=world\"},\"command\":[\"/bin/echo\"],"
                + "\"args\":[\"$GREETING\",\"hello world\"],\"restartPolicy\":{\"name\":\"ON_FAILURE\",\"maximumRetryCount\":3},"
                + "\"requiredCapabilities\":[\"ENVIRONMENT\",\"COMMAND_ARGS\",\"RESTART_POLICY\"],"
                + "\"ephemeralDiskMb\":128,\"diskMode\":\"REQUEST_ONLY\",\"secretRefs\":[],\"volumes\":[],\"healthCheck\":null");
        PendingCommand command = client.pendingCommands(nodeId, "agent-token").getFirst();
        var options = command.executionOptions();
        assertThat(options.environment()).containsEntry("GREETING", "hello=world");
        assertThat(options.command()).containsExactly("/bin/echo");
        assertThat(options.args()).containsExactly("$GREETING", "hello world");
        assertThat(options.restartPolicy()).isEqualTo(new RestartPolicySpec("ON_FAILURE", 3));
        assertThat(command.additionalOptions()).containsKey("healthCheck");
    }

    @Test
    void shouldCaptureAndRejectUnknownOrUnsupportedConfiguration() {
        for (String unsupported : java.util.List.of(
                ",\"volumes\":[{\"volumeId\":\"example\"}]",
                ",\"healthCheck\":{\"type\":\"EXEC\"}",
                ",\"secretRefs\":[{}]", ",\"network\":{}",
                ",\"diskMode\":\"REQUIRED\"", ",\"ephemeralDiskMb\":129",
                ",\"unexpectedField\":\"must-not-be-ignored\"")) {
            pendingExtraFields.set(unsupported);
            PendingCommand command = client.pendingCommands(nodeId, "agent-token").getFirst();
            assertThatThrownBy(command::executionOptions).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("UNSUPPORTED_SERVICE_OPTION");
        }
    }

    @Test
    void shouldNotCoerceConfigurationValuesOrIgnoreRestartTypos() {
        for (String invalid : java.util.List.of(",\"environment\":{\"X\":123}", ",\"command\":[123]", ",\"args\":[true]")) {
            pendingExtraFields.set(invalid);
            assertThatThrownBy(() -> client.pendingCommands(nodeId, "agent-token")).isInstanceOf(RuntimeException.class);
        }
        pendingExtraFields.set(",\"restartPolicy\":{\"name\":\"ON_FAILURE\",\"maxRetries\":3}");
        PendingCommand command = client.pendingCommands(nodeId, "agent-token").getFirst();
        assertThatThrownBy(command::executionOptions).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("restartPolicy");
    }

    @Test
    void shouldReportContainerStatusWithBearerToken() {
        client.updateContainerStatus(nodeId, "agent-token",
                new ContainerStatusUpdateRequest("RUNNING", "docker-123", null, java.util.List.of(
                        new ContainerEndpoint(80, 32768, PortProtocol.TCP, "0.0.0.0"))));

        assertThat(authorization.get()).isEqualTo("Bearer agent-token");
        assertThat(statusBody.get())
                .contains("\"status\":\"RUNNING\"")
                .contains("\"dockerContainerId\":\"docker-123\"")
                .contains("\"endpoints\":[{\"containerPort\":80,\"hostPort\":32768,"
                        + "\"protocol\":\"TCP\",\"address\":\"0.0.0.0\"}]");
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
