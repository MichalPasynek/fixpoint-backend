package pl.fixpoint.fixpointbackend.security.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional // Wycofanie zmian w bazie po każdym teście
public class AuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper; // Do konwersji obiektów Java na JSON

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
        // Zapewniamy, że role istnieją (zgodnie z V2__add_security_tables.sql)
        if (roleRepository.findByName(CUSTOMERS_ROLE).isEmpty()) {
            roleRepository.save(Role.builder().name(CUSTOMERS_ROLE).build());
        }
        if (roleRepository.findByName(ADMIN_ROLE).isEmpty()) {
            roleRepository.save(Role.builder().name(ADMIN_ROLE).build());
        }

        // 1. ZAREJESTRUJ KLIENTA (dla testu uwierzytelniania)
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

        // 2. LOGOWANIE KLIENTA (zdobycie tokena)
        AuthenticationRequest authCustomerRequest = AuthenticationRequest.builder()
                .email("test.customer@test.com")
                .password("password123")
                .build();

        MvcResult customerResult = mockMvc.perform(post("/api/v1/auth/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authCustomerRequest)))
                .andExpect(status().isOk())
                .andReturn();

        // Zapisanie tokena klienta
        customerToken = objectMapper.readValue(customerResult.getResponse().getContentAsString(), AuthenticationResponse.class).getToken();

        // 3. RĘCZNE UTWORZENIE ADMINA (do testu autoryzacji)
        User adminUser = User.builder()
                .firstName("Admin")
                .lastName("User")
                .email("test.admin@test.com")
                .password("$2a$10$XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX")
                .roles(List.of(roleRepository.findByName(ADMIN_ROLE).get()))
                .build();
        userRepository.save(adminUser);

        // 4. LOGOWANIE ADMINA (zdobycie tokena)
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

    // --- TESTY UWIEŻYTELNIENIA (AUTHENTICATION) ---

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

        // Spring Security zwraca 403 Forbidden dla nieudanego uwierzytelnienia w REST API
        mockMvc.perform(post("/api/v1/auth/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // --- TESTY AUTORYZACJI (AUTHORIZATION) ---

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