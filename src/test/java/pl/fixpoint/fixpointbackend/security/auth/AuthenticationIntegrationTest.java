package pl.fixpoint.fixpointbackend.security.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import pl.fixpoint.fixpointbackend.security.user.Role;
import pl.fixpoint.fixpointbackend.security.user.RoleRepository;
import pl.fixpoint.fixpointbackend.security.user.User;
import pl.fixpoint.fixpointbackend.security.user.UserRepository;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;


@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Testcontainers
public class AuthenticationIntegrationTest {

    @Container
    public static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("fixpointdb")
            .withUsername("fixpoint_test_user")
            .withPassword("fixpoint_test_pass")
            .withInitScript("sql/create_service_schema.sql");

    @DynamicPropertySource
    static void setTestProperties(DynamicPropertyRegistry registry) {
        // 1. Parametry połączenia (z Testcontainers)
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);

        // 2. KONFIGURACJA FLYWAY (WŁĄCZONA!)
        registry.add("spring.flyway.enabled", () -> "true"); // Upewnij się, że Flyway jest włączony

        // Upewnij się, że Flyway wie, gdzie szukać migracji (domyślna ścieżka to 'db/migration')
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");

        // Flyway musi wiedzieć, że ma pracować na schemacie "service"
        registry.add("spring.flyway.schemas", () -> "service");

        // 3. Kontrola DDL Hibernate (możemy to zostawić w bezpiecznym trybie)
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none"); // Flyway się tym zajmie

        // Wreszcie, upewnienie się, że Hibernate też wie, że ma pracować na schemacie "service"
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "service");
    }

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private static final String CUSTOMERS_ROLE = "CUSTOMERS";
    private static final String ADMIN_ROLE = "ADMIN";
    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setup() throws Exception {

        if (roleRepository.findByName(CUSTOMERS_ROLE).isEmpty()) {
            roleRepository.save(Role.builder().name(CUSTOMERS_ROLE).build());
        }
        if (roleRepository.findByName(ADMIN_ROLE).isEmpty()) {
            roleRepository.save(Role.builder().name(ADMIN_ROLE).build());
        }

        RegisterRequest registerCustomerRequest = RegisterRequest.builder()
                .firstName("Test")
                .lastName("Customer")
                .email("test.customer@test.com")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerCustomerRequest)))
                .andExpect(status().isOk());

        AuthenticationRequest authCustomerRequest = AuthenticationRequest.builder()
                .email("test.customer@test.com")
                .password("password123")
                .build();

        MvcResult customerResult = mockMvc.perform(post("/api/v1/auth/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authCustomerRequest)))
                .andExpect(status().isOk())
                .andReturn();

        customerToken = objectMapper.readValue(customerResult.getResponse().getContentAsString(), AuthenticationResponse.class).getToken();

        User adminUser = User.builder()
                .firstName("Admin")
                .lastName("User")
                .email("test.admin@test.com")
                .password(passwordEncoder.encode("password123"))
                .roles(List.of(roleRepository.findByName(ADMIN_ROLE).get()))
                .build();
        userRepository.save(adminUser);

        AuthenticationRequest authAdminRequest = AuthenticationRequest.builder()
                .email("test.admin@test.com")
                .password("password123")
                .build();

        MvcResult adminResult = mockMvc.perform(post("/api/v1/auth/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authAdminRequest)))
                .andExpect(status().isOk())
                .andReturn();

        adminToken = objectMapper.readValue(adminResult.getResponse().getContentAsString(), AuthenticationResponse.class).getToken();
    }

    @Test
    void shouldReturnTokenOnValidAuthentication() throws Exception {
        AuthenticationRequest request = AuthenticationRequest.builder()
                .email("test.customer@test.com")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/auth/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    void shouldReturn403ForbiddenForInvalidPassword() throws Exception {
        AuthenticationRequest request = AuthenticationRequest.builder()
                .email("test.customer@test.com")
                .password("wrongpassword")
                .build();

        mockMvc.perform(post("/api/v1/auth/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn401UnauthorizedWhenAccessingSecureEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/test/secure"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerShouldAccessSecureEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/test/secure")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk());
    }

    @Test
    void customerShouldBeForbiddenFromAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/test/admin")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden()); // Użytkownik CUSTOMERS nie ma uprawnień ADMIN
    }

    @Test
    void adminShouldAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/test/admin")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }
}