package com.iam.service;

import com.iam.config.AppProperties;
import com.iam.dto.request.ChangePasswordRequest;
import com.iam.dto.request.ResetPasswordRequest;
import com.iam.entity.PasswordResetToken;
import com.iam.entity.User;
import com.iam.exception.AccountLockedException;
import com.iam.exception.BadRequestException;
import com.iam.exception.InvalidPasswordException;
import com.iam.exception.InvalidTokenException;
import com.iam.exception.ResourceNotFoundException;
import com.iam.exception.TokenExpiredException;
import com.iam.repository.PasswordResetTokenRepository;
import com.iam.repository.RefreshTokenRepository;
import com.iam.repository.UserRepository;
import com.iam.repository.UserSessionRepository;
import com.iam.util.TokenUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditService auditService;
    private final AppProperties appProperties;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserSessionRepository userSessionRepository;

    @Transactional
    public void forgotPassword(String email, String ipAddress, String userAgent) {
        userRepository.findByEmail(email).ifPresentOrElse(user -> {
            // Delete any existing reset tokens
            passwordResetTokenRepository.deleteByUserId(user.getId());

            String rawToken = TokenUtils.generateSecureToken();
            String tokenHash = TokenUtils.hashToken(rawToken);

            LocalDateTime expiresAt = LocalDateTime.now().plusHours(
                    appProperties.getSecurity().getPasswordResetTokenExpiryHours()
            );

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .tokenHash(tokenHash)
                    .user(user)
                    .expiresAt(expiresAt)
                    .used(false)
                    .build();

            passwordResetTokenRepository.save(resetToken);

            emailService.sendPasswordResetEmail(user.getEmail(), user.getFirstName(), rawToken);
            auditService.logSuccess(user.getId(), user.getEmail(), "PASSWORD_RESET_REQUEST", "USER",
                    user.getId().toString(), null, null, ipAddress, userAgent);
            
            log.info("Password reset token generated: {} (User ID: {})", rawToken, user.getId());
        }, () -> {
            log.info("Password reset requested for non-existent email: {}", email);
            // We return success/noop silently to prevent user enumeration
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request, String ipAddress, String userAgent) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("New password and confirm password do not match");
        }

        String tokenHash = TokenUtils.hashToken(request.getToken());
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("Invalid password reset token"));

        if (resetToken.isUsed()) {
            throw new InvalidTokenException("Password reset token has already been used");
        }

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new TokenExpiredException("Password reset token has expired");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        
        // Account lock features check
        user.setAccountLocked(false);
        user.setFailedLoginAttempts(0);
        user.setLockTime(null);
        userRepository.save(user);

        // Mark token as used
        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        // Revoke all existing sessions and refresh tokens for security
        refreshTokenRepository.revokeAllByUserId(user.getId());
        userSessionRepository.deactivateAllByUserId(user.getId());

        emailService.sendPasswordChangedEmail(user.getEmail(), user.getFirstName());
        auditService.logSuccess(user.getId(), user.getEmail(), "PASSWORD_RESET_COMPLETE", "USER",
                user.getId().toString(), null, null, ipAddress, userAgent);

        log.info("Password reset completed successfully for user ID: {}", user.getId());
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request, String ipAddress, String userAgent) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new InvalidPasswordException("Current password is incorrect");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("New password and confirm password do not match");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Revoke other refresh tokens and sessions (for security)
        refreshTokenRepository.revokeAllByUserId(userId);
        userSessionRepository.deactivateAllByUserId(userId);

        emailService.sendPasswordChangedEmail(user.getEmail(), user.getFirstName());
        auditService.logSuccess(user.getId(), user.getEmail(), "PASSWORD_CHANGE", "USER",
                user.getId().toString(), null, null, ipAddress, userAgent);

        log.info("Password changed successfully for user ID: {}", userId);
    }

    @Transactional
    public void handleFailedLogin(User user) {
        int failedAttempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(failedAttempts);

        if (failedAttempts >= appProperties.getSecurity().getMaxFailedAttempts()) {
            user.setAccountLocked(true);
            user.setLockTime(LocalDateTime.now());
            
            // Notify user of lockout
            emailService.sendAccountLockedEmail(
                    user.getEmail(),
                    user.getFirstName(),
                    LocalDateTime.now().plusMinutes(appProperties.getSecurity().getLockDurationMinutes())
            );

            log.warn("Account temporarily locked for user ID: {} due to too many failed login attempts", user.getId());
        }

        userRepository.save(user);
    }

    @Transactional
    public void resetFailedAttempts(User user) {
        if (user.getFailedLoginAttempts() > 0) {
            user.setFailedLoginAttempts(0);
            user.setLockTime(null);
            userRepository.save(user);
        }
    }

    public boolean isAccountLocked(User user) {
        if (!user.isAccountLocked()) {
            return false;
        }

        LocalDateTime lockTime = user.getLockTime();
        if (lockTime == null) {
            return false;
        }

        LocalDateTime unlockTime = lockTime.plusMinutes(appProperties.getSecurity().getLockDurationMinutes());
        if (LocalDateTime.now().isAfter(unlockTime)) {
            // Auto unlock account after lock period expired
            log.info("Auto-unlocking account for user ID: {} as lock duration expired", user.getId());
            
            // Note: Since we are in a read-only context (usually checked during login), 
            // the actual status update is handled immediately in login process or we can unlock it here.
            // However, modifying state in a pure boolean getter is not recommended without transactional scope,
            // but we can trigger it during authenticating.
            return false;
        }

        return true;
    }
}
