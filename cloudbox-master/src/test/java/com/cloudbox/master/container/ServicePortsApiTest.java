package com.cloudbox.master.container;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cloudbox.master.common.GlobalExceptionHandler;
import com.cloudbox.master.node.Node;
import com.cloudbox.master.node.NodeCommandController;
import com.cloudbox.master.node.NodeController;
import com.cloudbox.master.node.NodeRepository;
import com.cloudbox.master.node.NodeService;
import com.cloudbox.master.node.NodeStatus;
import com.cloudbox.master.realtime.ClusterStatusPublisher;
import com.cloudbox.master.scheduler.NodeCandidateFilter;
import com.cloudbox.master.scheduler.NodeScoringStrategy;
import com.cloudbox.master.scheduler.SchedulerProperties;
import com.cloudbox.master.scheduler.SchedulerService;
import com.cloudbox.master.security.AgentTokenValidator;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Real controllers, validation, scheduling, authorization and JSON; repositories are isolated test doubles. */
class ServicePortsApiTest {
    private final NodeRepository nodes = mock(NodeRepository.class);
    private final ContainerRepository containers = mock(ContainerRepository.class);
    private final ClusterStatusPublisher publisher = mock(ClusterStatusPublisher.class);
    private final Map<UUID, ContainerInstance> stored = new HashMap<>();
    private MockMvc api;
    private Node eligible;

    @BeforeEach
    void setUp() {
        eligible = node("192.0.2.50", "owner-token", 4);
        Node legacy = node(null, "other-token", 16);
        when(nodes.findByStatus(NodeStatus.ONLINE)).thenReturn(List.of(legacy, eligible));
        when(nodes.findById(eligible.getId())).thenReturn(Optional.of(eligible));
        when(nodes.findById(legacy.getId())).thenReturn(Optional.of(legacy));
        when(containers.save(any(ContainerInstance.class))).thenAnswer(invocation -> {
            ContainerInstance container = invocation.getArgument(0);
            container.setId(UUID.randomUUID());
            container.onCreate();
            stored.put(container.getId(), container);
            return container;
        });
        when(containers.findById(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get(invocation.getArgument(0))));
        when(containers.findAll()).thenAnswer(invocation -> List.copyOf(stored.values()));
        when(containers.findByNodeIdAndStatusIn(any(UUID.class), anySet()))
                .thenAnswer(invocation -> {
                    Set<ContainerStatus> statuses = invocation.getArgument(1);
                    return stored.values().stream()
                        .filter(container -> container.getNodeId().equals(invocation.getArgument(0)))
                        .filter(container -> statuses.contains(container.getStatus()))
                        .toList();
                });

        SchedulerService scheduler = new SchedulerService(nodes,
                new NodeCandidateFilter(new SchedulerProperties()), new NodeScoringStrategy());
        ContainerService service = new ContainerService(containers, scheduler, publisher, new EndpointResolver());
        AgentTokenValidator auth = new AgentTokenValidator(nodes, containers);
        api = MockMvcBuilders.standaloneSetup(new ContainerController(service, auth),
                        new NodeCommandController(service, auth),
                        new NodeController(new NodeService(nodes, publisher), auth))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void publishedWorkloadRoundTripsThroughPendingCommandAndObservedEndpoint() throws Exception {
        UUID id = createHttpContainer();
        api.perform(get("/api/nodes/" + eligible.getId() + "/pending-commands")
                        .header("Authorization", "Bearer owner-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ports[0].containerPort").value(80))
                .andExpect(jsonPath("$[0].ports[0].protocol").value("TCP"))
                .andExpect(jsonPath("$[0].ports[0].exposure").value("HTTP"));

        api.perform(post("/api/containers/" + id + "/status")
                        .header("Authorization", "Bearer owner-token")
                        .contentType(MediaType.APPLICATION_JSON).content(running(32768)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endpoints[0].address").value("192.0.2.50"))
                .andExpect(jsonPath("$.endpoints[0].url").value("http://192.0.2.50:32768"));

        api.perform(get("/api/containers"))
                .andExpect(jsonPath("$[0].endpoints[0].hostPort").value(32768));
        api.perform(get("/api/nodes/" + eligible.getId() + "/pending-commands")
                        .header("Authorization", "Bearer owner-token"))
                .andExpect(jsonPath("$").isEmpty());

        // Status stays RUNNING, but the new binding must reach the dashboard.
        api.perform(post("/api/containers/" + id + "/status")
                        .header("Authorization", "Bearer owner-token")
                        .contentType(MediaType.APPLICATION_JSON).content(running(32769)))
                .andExpect(jsonPath("$.endpoints[0].hostPort").value(32769));
        verify(publisher).publishContainerStatusChange(id, ContainerStatus.RUNNING, ContainerStatus.RUNNING);
    }

    @Test
    void rejectsOtherAgentsAndUnrequestedPortsWithoutChangingState() throws Exception {
        UUID id = createHttpContainer();
        api.perform(post("/api/containers/" + id + "/status")
                        .header("Authorization", "Bearer other-token")
                        .contentType(MediaType.APPLICATION_JSON).content(running(32768)))
                .andExpect(status().isUnauthorized());
        api.perform(post("/api/containers/" + id + "/status")
                        .header("Authorization", "Bearer owner-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(running(32768).replace("\"containerPort\":80", "\"containerPort\":81")))
                .andExpect(status().isBadRequest());
        assertThat(stored.get(id).getStatus()).isEqualTo(ContainerStatus.PENDING);
        assertThat(stored.get(id).getEndpoints()).isEmpty();
    }

    @Test
    void heartbeatRequiresTheTokenOfTheAddressedNode() throws Exception {
        String path = "/api/nodes/" + eligible.getId() + "/heartbeat";
        String body = "{\"cpuFree\":2,\"ramFreeMb\":1024,\"diskFreeMb\":8192}";
        api.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        api.perform(post(path).header("Authorization", "Bearer other-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        api.perform(post(path).header("Authorization", "Bearer owner-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
        assertThat(eligible.getRamFreeMb()).isEqualTo(1024);
    }

    @Test
    void omittedEndpointsPreserveAndExplicitEmptyOrStoppedClearAccess() throws Exception {
        UUID id = createHttpContainer();
        update(id, running(32768));
        update(id, "{\"status\":\"RUNNING\",\"dockerContainerId\":\"docker-test\"}");
        assertThat(stored.get(id).getEndpoints()).hasSize(1);
        update(id, "{\"status\":\"RUNNING\",\"endpoints\":[]}");
        assertThat(stored.get(id).getEndpoints()).isEmpty();
        update(id, running(32768));
        update(id, "{\"status\":\"STOPPED\"}");
        assertThat(stored.get(id).getEndpoints()).isEmpty();
        update(id, running(32768));
        update(id, "{\"status\":\"ERROR\",\"errorMessage\":\"process exited\"}");
        assertThat(stored.get(id).getEndpoints()).isEmpty();
    }

    @Test
    void rejectsInvalidNestedPortsAndDefaultsLegacyRequests() throws Exception {
        String base = "\"imageName\":\"nginx:alpine\",\"cpuCores\":1,\"memoryMb\":256,\"diskMb\":100";
        api.perform(post("/api/containers").contentType(MediaType.APPLICATION_JSON)
                        .content("{" + base + ",\"ports\":[{\"containerPort\":65536}]}"))
                .andExpect(status().isBadRequest());
        api.perform(post("/api/containers").contentType(MediaType.APPLICATION_JSON)
                        .content("{" + base + ",\"ports\":[{\"containerPort\":80,\"hostPort\":8080}]}"))
                .andExpect(status().isBadRequest());
        api.perform(post("/api/containers").contentType(MediaType.APPLICATION_JSON)
                        .content("{" + base + ",\"ports\":null}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ports").isEmpty())
                .andExpect(jsonPath("$.endpoints").isEmpty());
    }

    private UUID createHttpContainer() throws Exception {
        String json = api.perform(post("/api/containers").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"imageName":"nginx:alpine","cpuCores":1,"memoryMb":256,"diskMb":100,
                                 "ports":[{"containerPort":80,"protocol":"TCP","exposure":"HTTP"}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nodeId").value(eligible.getId().toString()))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(json, "$.id"));
    }

    private void update(UUID id, String body) throws Exception {
        api.perform(post("/api/containers/" + id + "/status")
                        .header("Authorization", "Bearer owner-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private String running(int hostPort) {
        return "{\"status\":\"RUNNING\",\"dockerContainerId\":\"docker-test\",\"endpoints\":["
                + "{\"containerPort\":80,\"hostPort\":" + hostPort + ",\"protocol\":\"TCP\",\"address\":\"0.0.0.0\"}]}";
    }

    private Node node(String address, String token, int cpu) {
        Node node = new Node();
        node.setId(UUID.randomUUID());
        node.setName("test-node");
        node.setToken(token);
        node.setAdvertiseAddress(address);
        node.setStatus(NodeStatus.ONLINE);
        node.setCpuTotal(BigDecimal.valueOf(cpu));
        node.setCpuFree(BigDecimal.valueOf(cpu));
        node.setRamTotalMb(8192);
        node.setRamFreeMb(8192);
        node.setDiskTotalMb(100_000);
        node.setDiskFreeMb(50_000);
        return node;
    }
}
