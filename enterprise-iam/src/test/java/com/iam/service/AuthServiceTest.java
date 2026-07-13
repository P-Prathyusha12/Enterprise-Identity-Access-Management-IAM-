package com.iam.service;

import com.iam.config.AppProperties;
import com.iam.dto.request.LoginRequest;
import com.iam.dto.request.RegisterRequest;
import com.iam.dto.response.AuthResponse;
import com.iam.dto.response.UserResponse;
import com.iam.entity.Role;
import com.iam.entity.User;
import com.iam.exception.AccountLockedException;
import com.iam.exception.DuplicateResourceException;
import com.iam.exception.EmailNotVerifiedException;
import com.iam.repository.EmailVerificationTokenRepository;
import com.iam.repository.RefreshTokenRepository;
import com.iam.repository.RoleRepository;
import com.iam.repository.UserRepository;
import com.iam.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordService passwordService;

    @Mock
    private MfaService mfaService;

    @Mock
    private SessionService sessionService;

    @Mock
    private AuditService auditService;

    @Mock
    private AppProperties appProperties;

    @Mock
    private UserService userService;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private Role userRole;
    private AppProperties.Jwt jwtProps;
    private AppProperties.Security securityProps;

    @BeforeEach
    void setUp() {
        userRole = Role.builder()
                .id(1L)
                .name("ROLE_USER")
                .permissions(new HashSet<>())
                .build();

        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .passwordHash("$2a$12$hashedpassword")
                .firstName("Test")
                .lastName("User")
                .enabled(true)
                .emailVerified(true)
                .accountLocked(false)
                .mfaEnabled(false)
                .roles(new HashSet<>(Set.of(userRole)))
                .build();

        jwtProps = new AppProperties.Jwt();
        securityProps = new AppProperties.Security();

        when(appProperties.getJwt()).thenReturn(jwtProps);
        when(appProperties.getSecurity()).thenReturn(securityProps);
    }

    @Test
    @DisplayName("Register - successful registration")
    void register_shouldSucceed_whenValidRequestProvided() {
        // Given
        RegisterRequest request = RegisterRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .username("johndoe")
                .email("john@example.com")
                .password("StrongPass@123")
                .build();

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(userRole));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashed");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(emailVerificationTokenRepository.save(any())).thenReturn(null);
        doNothing().when(emailService).sendVerificationEmail(anyString(), anyString(), anyString());
        doNothing().when(auditService).logSuccess(any(), any(), any(), any(), any(), any(), any(), any(), any());
        when(userService.toResponse(any(User.class))).thenReturn(UserResponse.builder()
                .id(1L).email("john@example.com").build());

        // When
        UserResponse result = authService.register(request, "127.0.0.1", "TestAgent");

        // Then
        assertThat(result).isNotNull();
        verify(userRepository).existsByEmail("john@example.com");
        verify(userRepository).existsByUsername("johndoe");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Register - should throw when email already exists")
    void register_shouldThrow_whenEmailAlreadyExists() {
        // Given
        RegisterRequest request = RegisterRequest.builder()
                .email("existing@example.com")
                .username("newuser")
                .firstName("New")
                .lastName("User")
                .password("StrongPass@123")
                .build();

        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> authService.register(request, "127.0.0.1", "TestAgent"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Email already registered");
    }

    @Test
    @DisplayName("Register - should throw when username already taken")
    void register_shouldThrow_whenUsernameAlreadyTaken() {
        // Given
        RegisterRequest request = RegisterRequest.builder()
                .email("new@example.com")
                .username("existinguser")
                .firstName("New")
                .lastName("User")
                .password("StrongPass@123")
                .build();

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> authService.register(request, "127.0.0.1", "TestAgent"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Username already taken");
    }

    @Test
    @DisplayName("Login - should throw when account is locked")
    void login_shouldThrow_whenAccountIsLocked() {
        // Given
        LoginRequest request = LoginRequest.builder()
                .emailOrUsername("test@example.com")
                .password("Password@123")
                .build();

        testUser.setAccountLocked(true);
        testUser.setLockTime(LocalDateTime.now().minusMinutes(5)); // Locked 5 minutes ago

        when(userRepository.findByEmailOrUsername(anyString(), anyString())).thenReturn(Optional.of(testUser));
        when(passwordService.isAccountLocked(testUser)).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> authService.login(request, "127.0.0.1", "TestAgent"))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    @DisplayName("Login - should throw when email is not verified")
    void login_shouldThrow_whenEmailNotVerified() {
        // Given
        LoginRequest request = LoginRequest.builder()
                .emailOrUsername("test@example.com")
                .password("Password@123")
                .build();

        testUser.setEmailVerified(false);
        testUser.setEnabled(false);

        when(userRepository.findByEmailOrUsername(anyString(), anyString())).thenReturn(Optional.of(testUser));
        when(passwordService.isAccountLocked(testUser)).thenReturn(false);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> authService.login(request, "127.0.0.1", "TestAgent"))
                .isInstanceOf(EmailNotVerifiedException.class);
    }
}
