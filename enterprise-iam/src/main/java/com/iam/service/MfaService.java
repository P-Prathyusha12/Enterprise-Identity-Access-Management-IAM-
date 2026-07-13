package com.iam.service;

import com.iam.config.AppProperties;
import com.iam.dto.response.MfaSetupResponse;
import com.iam.entity.User;
import com.iam.exception.BadRequestException;
import com.iam.exception.ResourceNotFoundException;
import com.iam.repository.UserRepository;
import com.iam.util.TokenUtils;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrDataFactory;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MfaService {

    private final SecretGenerator secretGenerator;
    private final QrDataFactory qrDataFactory;
    private final QrGenerator qrGenerator;
    private final CodeVerifier codeVerifier;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final EmailService emailService;
    private final AuditService auditService;
    private final AppProperties appProperties;

    private static final String MFA_SETUP_KEY_PREFIX = "mfa:setup:";
    private static final String OTP_KEY_PREFIX = "otp:";

    @Transactional
    public MfaSetupResponse setupMfa(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (user.isMfaEnabled()) {
            throw new BadRequestException("MFA is already enabled on this account");
        }

        String secret = secretGenerator.generate();
        
        // Cache the secret in Redis for 10 minutes while waiting for user confirmation
        redisTemplate.opsForValue().set(
                MFA_SETUP_KEY_PREFIX + userId,
                secret,
                10,
                TimeUnit.MINUTES
        );

        QrData qrData = qrDataFactory.newBuilder()
                .label(user.getEmail())
                .secret(secret)
                .issuer("Enterprise IAM")
                .build();

        String qrCodeUri = qrData.getUri();
        String qrImageBase64 = "";
        try {
            byte[] qrBytes = qrGenerator.generate(qrData);
            qrImageBase64 = Base64.getEncoder().encodeToString(qrBytes);
        } catch (Exception e) {
            log.error("Failed to generate QR Code image", e);
        }

        return MfaSetupResponse.builder()
                .secret(secret)
                .qrCodeUri(qrCodeUri)
                .qrCodeImageBase64(qrImageBase64)
                .build();
    }

    @Transactional
    public void verifyAndEnableMfa(Long userId, String code) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        String cachedSecret = redisTemplate.opsForValue().get(MFA_SETUP_KEY_PREFIX + userId);
        if (cachedSecret == null) {
            throw new BadRequestException("MFA setup request expired or was not initiated. Please start setup again.");
        }

        if (!codeVerifier.isValidCode(cachedSecret, code)) {
            throw new BadRequestException("Invalid verification code. Please check your authenticator app.");
        }

        user.setMfaSecret(cachedSecret);
        user.setMfaEnabled(true);
        userRepository.save(user);

        redisTemplate.delete(MFA_SETUP_KEY_PREFIX + userId);

        auditService.logSuccess(user.getId(), user.getEmail(), "MFA_ENABLE", "USER",
                user.getId().toString(), null, null, "unknown", "unknown");

        log.info("MFA successfully enabled for user: {}", user.getEmail());
    }

    public boolean verifyTotpCode(User user, String code) {
        if (!user.isMfaEnabled() || user.getMfaSecret() == null) {
            return false;
        }
        return codeVerifier.isValidCode(user.getMfaSecret(), code);
    }

    @Transactional
    public void disableMfa(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (!user.isMfaEnabled()) {
            throw new BadRequestException("MFA is not enabled on this account");
        }

        user.setMfaSecret(null);
        user.setMfaEnabled(false);
        userRepository.save(user);

        auditService.logSuccess(user.getId(), user.getEmail(), "MFA_DISABLE", "USER",
                user.getId().toString(), null, null, "unknown", "unknown");

        log.info("MFA successfully disabled for user: {}", user.getEmail());
    }

    // Email OTP methods
    public void sendEmailOtp(Long userId, String email, String firstName) {
        String otp = TokenUtils.generateOtp();
        
        // Cache OTP in Redis for 5 minutes
        redisTemplate.opsForValue().set(
                OTP_KEY_PREFIX + userId,
                otp,
                5,
                TimeUnit.MINUTES
        );

        emailService.sendOtpEmail(email, firstName, otp);
        log.info("OTP sent to email for user ID: {}", userId);
    }

    public boolean verifyEmailOtp(Long userId, String otp) {
        String cachedOtp = redisTemplate.opsForValue().get(OTP_KEY_PREFIX + userId);
        if (cachedOtp == null) {
            return false;
        }
        
        boolean isValid = cachedOtp.equals(otp);
        if (isValid) {
            redisTemplate.delete(OTP_KEY_PREFIX + userId);
        }
        return isValid;
    }
}
