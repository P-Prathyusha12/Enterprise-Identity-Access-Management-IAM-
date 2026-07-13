package com.iam.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iam.config.AppProperties;
import com.iam.dto.request.LoginRequest;
import com.iam.dto.request.RegisterRequest;
import com.iam.dto.response.AuthResponse;
import com.iam.dto.response.UserResponse;
import com.iam.security.JwtAuthenticationFilter;
import com.iam.security.JwtTokenProvider;
import com.iam.service.AuthService;
import com.iam.service.PasswordService;
import com.iam.util.SecurityUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@DisplayName("AuthController Integration Tests")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private PasswordService passwordService;

    @MockBean
    private SecurityUtils securityUtils;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private AppProperties appProperties;

    @Test
    @DisplayName("POST /api/v1/auth/register - should return 201 on valid registration")
    void register_shouldReturn201_whenValidRequestBody() throws Exception {
        // Given
        RegisterRequest request = RegisterRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .username("johndoe")
                .email("john@example.com")
                .password("StrongPass@123")
                .build();

        UserResponse userResponse = UserResponse.builder()
                .id(1L)
                .username("johndoe")
                .email("john@example.com")
                .firstName("John")
                .lastName("Doe")
                .build();

        when(authService.register(any(RegisterRequest.class), anyString(), anyString())).thenReturn(userResponse);

        // When/Then
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("john@example.com"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - should return 400 on invalid email")
    void register_shouldReturn400_whenInvalidEmail() throws Exception {
        // Given
        RegisterRequest request = RegisterRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .username("johndoe")
                .email("not-an-email")
                .password("StrongPass@123")
                .build();

        // When/Then
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - should return 200 on valid credentials")
    void login_shouldReturn200_whenValidCredentials() throws Exception {
        // Given
        LoginRequest request = LoginRequest.builder()
                .emailOrUsername("john@example.com")
                .password("StrongPass@123")
                .build();

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken("eyJhbGciOiJIUzI1NiJ9.test")
                .refreshToken("refresh-token-value")
                .tokenType("Bearer")
                .expiresIn(900L)
                .mfaRequired(false)
                .build();

        when(authService.login(any(LoginRequest.class), anyString(), anyString())).thenReturn(authResponse);

        // When/Then
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }
}
