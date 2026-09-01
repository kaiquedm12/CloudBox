package com.cloudbox.master.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cloudbox.master.auth.AuthController;
import com.cloudbox.master.common.GlobalExceptionHandler;
import com.cloudbox.master.user.User;
import com.cloudbox.master.user.UserRepository;
import com.cloudbox.master.user.UserRole;
import com.jayway.jsonpath.JsonPath;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockCookie;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.filter.DelegatingFilterProxy;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class SecurityIntegrationTest {

    private static final String EMAIL = "admin@cloudbox.local";
    private static final String PASSWORD = "senha-segura";
    private static final String SECRET =
            "Y2xvdWRib3gtdGVzdC1qd3Qtc2VjcmV0LW11c3QtYmUtYXQtbGVhc3QtMzItYnl0ZXM=";

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                context,
                "cloudbox.security.jwt.secret=" + SECRET,
                "cloudbox.security.jwt.expiration-seconds=3600");
        context.register(TestConfiguration.class);
        context.refresh();

        jwtService = context.getBean(JwtService.class);

        PasswordEncoder passwordEncoder = context.getBean(PasswordEncoder.class);
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(EMAIL);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(UserRole.ADMIN);
        context.getBean(TestUserStore.class).user = user;

        DelegatingFilterProxy securityFilter = new DelegatingFilterProxy("springSecurityFilterChain", context);
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilter(securityFilter, "/*")
                .build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void loginReturnsValidJwtThatAuthorizesDashboardEndpoint() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@cloudbox.local","password":"senha-segura"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andReturn();

        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.token");
        assertThat(jwtService.validateToken(token)).isTrue();

        mockMvc.perform(get("/api/nodes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void dashboardEndpointRejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/nodes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Token JWT ausente ou inválido"));
    }

    @Test
    void agentEndpointsRemainAvailableWithoutJwt() throws Exception {
        UUID resourceId = UUID.randomUUID();

        mockMvc.perform(post("/api/nodes/register"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/nodes/" + resourceId + "/heartbeat"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/nodes/" + resourceId + "/pending-commands"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/containers/" + resourceId + "/status"))
                .andExpect(status().isNoContent());
    }

    @Test
    void onlyAgentOperationsBypassJwtAuthentication() throws Exception {
        UUID resourceId = UUID.randomUUID();

        mockMvc.perform(get("/api/nodes/register"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/nodes/" + resourceId + "/heartbeat"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/nodes/" + resourceId + "/pending-commands"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/containers/" + resourceId + "/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRejectsInvalidPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@cloudbox.local","password":"senha-incorreta"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("E-mail ou senha inválidos"));
    }

    @Test
    void dashboardContainerEndpointRejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(post("/api/containers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unrelatedApiEndpointAlsoRejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/rota-nao-liberada"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidJwtDoesNotAuthorizeDashboardEndpoint() throws Exception {
        mockMvc.perform(get("/api/nodes").header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void websocketHandshakeAcceptsJwtFromDashboardHttpOnlyCookie() throws Exception {
        String token = jwtService.generateToken(context.getBean(TestUserStore.class).user);

        mockMvc.perform(get("/ws/cluster-status")
                        .cookie(new MockCookie("cloudbox_access_token", token)))
                .andExpect(status().isOk());
    }

    @Test
    void dashboardCookieIsNotAcceptedByRegularApiEndpoints() throws Exception {
        String token = jwtService.generateToken(context.getBean(TestUserStore.class).user);

        mockMvc.perform(get("/api/nodes")
                        .cookie(new MockCookie("cloudbox_access_token", token)))
                .andExpect(status().isUnauthorized());
    }

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class,
            AuthController.class, GlobalExceptionHandler.class, TestEndpoints.class})
    static class TestConfiguration {

        @Bean
        TestUserStore testUserStore() {
            return new TestUserStore();
        }

        @Bean
        UserRepository userRepository(TestUserStore store) {
            return (UserRepository) Proxy.newProxyInstance(
                    UserRepository.class.getClassLoader(),
                    new Class<?>[] {UserRepository.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("findByEmailIgnoreCase")) {
                            String email = (String) arguments[0];
                            return Optional.ofNullable(store.user)
                                    .filter(user -> user.getEmail().equalsIgnoreCase(email));
                        }
                        if (method.getName().equals("toString")) {
                            return "TestUserRepository";
                        }
                        if (method.getName().equals("hashCode")) {
                            return System.identityHashCode(proxy);
                        }
                        if (method.getName().equals("equals")) {
                            return proxy == arguments[0];
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

    static class TestUserStore {
        private User user;
    }

    @RestController
    static class TestEndpoints {

        @GetMapping("/api/nodes")
        List<String> nodes() {
            return List.of();
        }

        @GetMapping("/ws/cluster-status")
        ResponseEntity<Void> clusterStatusWebSocketHandshake() {
            return ResponseEntity.ok().build();
        }

        @PostMapping("/api/nodes/register")
        ResponseEntity<Void> register() {
            return ResponseEntity.status(201).build();
        }

        @PostMapping("/api/nodes/{id}/heartbeat")
        ResponseEntity<Void> heartbeat(@PathVariable UUID id) {
            return ResponseEntity.noContent().build();
        }

        @GetMapping("/api/nodes/{id}/pending-commands")
        List<String> pendingCommands(@PathVariable UUID id) {
            return List.of();
        }

        @PostMapping("/api/containers/{id}/status")
        ResponseEntity<Void> updateStatus(@PathVariable UUID id) {
            return ResponseEntity.noContent().build();
        }
    }
}
