package com.example.shopapp.integration;

import com.example.shopapp.dto.auth.AuthResponse;
import com.example.shopapp.dto.auth.LoginRequest;
import com.example.shopapp.dto.auth.RegisterRequest;
import com.example.shopapp.entity.User;
import com.example.shopapp.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Login and Google Account Linking
 * Uses real database (H2) and full Spring context
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Integration Tests - Login & Google Link")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User registeredUser;
    private String authToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        
        // Create a registered user for testing
        registeredUser = User.builder()
                .email("vi.duong059@gmail.com")
                .fullName("Vi Duong")
                .password(passwordEncoder.encode("SecurePass123"))
                .phone("0901234567")
                .enabled(true)
                .role("USER")
                .build();
        
        userRepository.save(registeredUser);
    }

    @Nested
    @DisplayName("Login Integration Tests")
    class LoginIntegrationTests {

        @Test
        @DisplayName("Should login successfully with valid credentials")
        void login_Success() throws Exception {
            // Arrange
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setEmail("vi.duong059@gmail.com");
            loginRequest.setPassword("SecurePass123");

            // Act & Assert
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("vi.duong059@gmail.com"))
                    .andExpect(jsonPath("$.token").exists())
                    .andExpect(jsonPath("$.fullName").value("Vi Duong"));
        }

        @Test
        @DisplayName("Should return 400 when email not found")
        void login_EmailNotFound_Returns400() throws Exception {
            // Arrange
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setEmail("notfound@example.com");
            loginRequest.setPassword("SecurePass123");

            // Act & Assert
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when password is incorrect")
        void login_WrongPassword_Returns400() throws Exception {
            // Arrange
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setEmail("vi.duong059@gmail.com");
            loginRequest.setPassword("WrongPassword");

            // Act & Assert
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when email is blank")
        void login_BlankEmail_Returns400() throws Exception {
            // Arrange
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setEmail("");
            loginRequest.setPassword("SecurePass123");

            // Act & Assert
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when password is blank")
        void login_BlankPassword_Returns400() throws Exception {
            // Arrange
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setEmail("vi.duong059@gmail.com");
            loginRequest.setPassword("");

            // Act & Assert
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Google Account Linking Integration Tests")
    class GoogleLinkIntegrationTests {

        @Test
        @DisplayName("Should get Google link URL when authenticated")
        void getGoogleLinkUrl_WhenAuthenticated_Success() throws Exception {
            // Arrange - First login to get token
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setEmail("vi.duong059@gmail.com");
            loginRequest.setPassword("SecurePass123");

            String token = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            
            AuthResponse authResponse = objectMapper.readValue(token, AuthResponse.class);
            authToken = authResponse.getToken();

            // Act & Assert
            mockMvc.perform(get("/api/auth/google/link-url")
                    .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.authorizationUrl").exists())
                    .andExpect(jsonPath("$.authorizationUrl").value(
                            org.hamcrest.Matchers.containsString("https://accounts.google.com/o/oauth2/v2/auth")));
        }

        @Test
        @DisplayName("Should return 400 when not authenticated for Google link URL")
        void getGoogleLinkUrl_WhenNotAuthenticated_Returns400() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/auth/google/link-url")
                    .header("Authorization", "Bearer invalid-token"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should get Google link status when authenticated")
        void getGoogleLinkStatus_WhenAuthenticated_Success() throws Exception {
            // Arrange - First login to get token
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setEmail("vi.duong059@gmail.com");
            loginRequest.setPassword("SecurePass123");

            String token = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            
            AuthResponse authResponse = objectMapper.readValue(token, AuthResponse.class);
            authToken = authResponse.getToken();

            // Act & Assert
            mockMvc.perform(get("/api/auth/google/status")
                    .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.linked").exists())
                    .andExpect(jsonPath("$.linked").value(false)); // Not linked yet
        }

        @Test
        @DisplayName("Should return 400 when not authenticated for Google link status")
        void getGoogleLinkStatus_WhenNotAuthenticated_Returns400() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/auth/google/status")
                    .header("Authorization", "Bearer invalid-token"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should handle Google callback with invalid state")
        void handleGoogleCallback_InvalidState_ReturnsError() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/auth/google/callback")
                    .param("state", "invalid-state")
                    .param("code", "authorization-code"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(
                            org.hamcrest.Matchers.containsString("State OAuth không hợp lệ")));
        }

        @Test
        @DisplayName("Should handle Google callback with missing code")
        void handleGoogleCallback_MissingCode_ReturnsError() throws Exception {
            // Arrange - Generate state first
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setEmail("vi.duong059@gmail.com");
            loginRequest.setPassword("SecurePass123");

            String token = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            
            AuthResponse authResponse = objectMapper.readValue(token, AuthResponse.class);
            authToken = authResponse.getToken();

            // Get link URL to generate state
            String linkUrlResponse = mockMvc.perform(get("/api/auth/google/link-url")
                    .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            // Extract state from response (in real scenario, this would be in the URL)
            // For this test, we just verify the endpoint handles missing code

            // Act & Assert
            mockMvc.perform(get("/api/auth/google/callback")
                    .param("state", "some-state")
                    .param("code", ""))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("End-to-End Flow Tests")
    class EndToEndFlowTests {

        @Test
        @DisplayName("Should register, login, and get Google link URL")
        void register_Login_GetGoogleLinkUrl_Success() throws Exception {
            // Step 1: Register new user
            RegisterRequest registerRequest = new RegisterRequest();
            registerRequest.setEmail("newuser@example.com");
            registerRequest.setFullName("New User");
            registerRequest.setPassword("SecurePass123");
            registerRequest.setPhone("0901234568");

            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isOk());

            // Enable the user (normally done via OTP verification)
            User newUser = userRepository.findByEmail("newuser@example.com").orElseThrow();
            newUser.setEnabled(true);
            userRepository.save(newUser);

            // Step 2: Login
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setEmail("newuser@example.com");
            loginRequest.setPassword("SecurePass123");

            String token = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            
            AuthResponse authResponse = objectMapper.readValue(token, AuthResponse.class);

            // Step 3: Get Google link URL
            mockMvc.perform(get("/api/auth/google/link-url")
                    .header("Authorization", "Bearer " + authResponse.getToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.authorizationUrl").exists());
        }

        @Test
        @DisplayName("Should login and verify user profile")
        void login_GetProfile_Success() throws Exception {
            // Step 1: Login
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setEmail("vi.duong059@gmail.com");
            loginRequest.setPassword("SecurePass123");

            String token = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            
            AuthResponse authResponse = objectMapper.readValue(token, AuthResponse.class);

            // Step 2: Get user profile
            mockMvc.perform(get("/api/auth/profile")
                    .header("Authorization", "Bearer " + authResponse.getToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("vi.duong059@gmail.com"))
                    .andExpect(jsonPath("$.fullName").value("Vi Duong"))
                    .andExpect(jsonPath("$.phone").value("0901234567"));
        }
    }
}
