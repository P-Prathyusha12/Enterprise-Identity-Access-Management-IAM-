package com.iam.service;

import com.iam.config.AppProperties;
import com.iam.dto.request.ChangePasswordRequest;
import com.iam.entity.PasswordResetToken;
import com.iam.entity.User;
import com.iam.exception.BadRequestException;
import com.iam.exception.InvalidPasswordException;
import com.iam.exception.InvalidTokenException;
import com.iam.exception.TokenExpiredException;
import com.iam.repository.PasswordResetTokenRepository;
import com.iam.repository.RefreshTokenRepository;
import com.iam.repository.UserRepository;
import com.iam.repository.UserSessionRepository;
import com.iam.util.TokenUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordService Unit Tests")
class PasswordServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @Mock
    private AuditService auditService;

    @Mock
    private AppProperties appProperties;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserSessionRepository userSessionRepository;

    @InjectMocks
    private PasswordService passwordService;

    private User testUser;
    private AppProperties.Security securityProps;
    private AppProperties.Jwt jwtProps;

    @BeforeEach
    void setUp() {
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
                .failedLoginAttempts(0)
                .build();

        securityProps = new AppProperties.Security();
        jwtProps = new AppProperties.Jwt();

        when(appProperties.getSecurity()).thenReturn(securityProps);
    }

    @Test
    @DisplayName("ChangePassword - should succeed with correct credentials")
    void changePassword_shouldSucceed_whenCorrectCurrentPasswordProvided() {
        // Given
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("OldPass@123")
                .newPassword("NewPass@123")
                .confirmPassword("NewPass@123")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("OldPass@123", testUser.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode("NewPass@123")).thenReturn("$2a$12$newhashedpassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        doNothing().when(refreshTokenRepository).revokeAllByUserId(anyLong());
        doNothing().when(userSessionRepository).deactivateAllByUserId(anyLong());
        doNothing().when(emailService).sendPasswordChangedEmail(anyString(), anyString());
        doNothing().when(auditService).logSuccess(any(), any(), any(), any(), any(), any(), any(), any(), any());

        // When/Then (no exception = success)
        passwordService.changePassword(1L, request, "127.0.0.1", "TestAgent");

        verify(userRepository).save(any(User.class));
        verify(passwordEncoder).encode("NewPass@123");
    }

    @Test
    @DisplayName("ChangePassword - should throw when current password is wrong")
    void changePassword_shouldThrow_whenCurrentPasswordIsWrong() {
        // Given
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("WrongPass@123")
                .newPassword("NewPass@123")
                .confirmPassword("NewPass@123")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("WrongPass@123", testUser.getPasswordHash())).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> passwordService.changePassword(1L, request, "127.0.0.1", "TestAgent"))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessageContaining("Current password is incorrect");
    }

    @Test
    @DisplayName("ChangePassword - should throw when passwords don't match")
    void changePassword_shouldThrow_whenNewPasswordsDoNotMatch() {
        // Given
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("OldPass@123")
                .newPassword("NewPass@123")
                .confirmPassword("DifferentPass@123")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("OldPass@123", testUser.getPasswordHash())).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> passwordService.changePassword(1L, request, "127.0.0.1", "TestAgent"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("do not match");
    }

    @Test
    @DisplayName("HandleFailedLogin - should lock account after max attempts")
    void handleFailedLogin_shouldLockAccount_whenMaxAttemptsReached() {
        // Given
        testUser.setFailedLoginAttempts(4); // One more will trigger lock
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        doNothing().when(emailService).sendAccountLockedEmail(anyString(), anyString(), any());

        // When
        passwordService.handleFailedLogin(testUser);

        // Then
        verify(userRepository).save(argThat(user -> user.isAccountLocked() && user.getFailedLoginAttempts() == 5));
        verify(emailService).sendAccountLockedEmail(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("IsAccountLocked - should return false after lock duration expires")
    void isAccountLocked_shouldReturnFalse_whenLockDurationExpired() {
        // Given
        testUser.setAccountLocked(true);
        testUser.setLockTime(LocalDateTime.now().minusMinutes(60)); // Locked 60 mins ago, duration is 30

        // When
        boolean isLocked = passwordService.isAccountLocked(testUser);

        // Then
        assert (!isLocked);
    }
}
