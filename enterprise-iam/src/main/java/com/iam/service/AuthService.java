package com.iam.service;

import com.iam.config.AppProperties;
import com.iam.dto.request.LoginRequest;
import com.iam.dto.request.MfaVerificationRequest;
import com.iam.dto.request.RefreshTokenRequest;
import com.iam.dto.request.RegisterRequest;
import com.iam.dto.response.AuthResponse;
import com.iam.dto.response.UserResponse;
import com.iam.entity.EmailVerificationToken;
import com.iam.entity.RefreshToken;
import com.iam.entity.Role;
import com.iam.entity.User;
import com.iam.entity.UserSession;
import com.iam.exception.AccountLockedException;
import com.iam.exception.DuplicateResourceException;
import com.iam.exception.EmailNotVerifiedException;
import com.iam.exception.InvalidPasswordException;
import com.iam.exception.InvalidTokenException;
import com.iam.exception.MfaRequiredException;
import com.iam.exception.ResourceNotFoundException;
import com.iam.exception.TokenExpiredException;
import com.iam.repository.EmailVerificationTokenRepository;
import com.iam.repository.RefreshTokenRepository;
import com.iam.repository.RoleRepository;
import com.iam.repository.UserRepository;
import com.iam.security.JwtTokenProvider;
import com.iam.util.TokenUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final PasswordService passwordService;
    private final MfaService mfaService;
    private final SessionService sessionService;
    private final AuditService auditService;
    private final AppProperties appProperties;
    private final UserService userService;

    @Transactional
    public UserResponse register(RegisterRequest request, String ipAddress, String userAgent) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already registered: " + request.getEmail());
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username already taken: " + request.getUsername());
        }

        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", "USER"));

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .enabled(false) // requires email verification
                .emailVerified(false)
                .roles(new HashSet<>(Set.of(userRole)))
                .build();

        User savedUser = userRepository.save(user);

        // Generate email verification token
        String rawToken = TokenUtils.generateSecureToken();
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .token(rawToken)
                .user(savedUser)
                .expiresAt(LocalDateTime.now().plusHours(appProperties.getSecurity().getEmailTokenExpiryHours()))
                .used(false)
                .build();

        emailVerificationTokenRepository.save(verificationToken);

        // Send email
        emailService.sendVerificationEmail(savedUser.getEmail(), savedUser.getFirstName(), rawToken);

        auditService.logSuccess(savedUser.getId(), savedUser.getEmail(), "USER_REGISTER", "USER",
                savedUser.getId().toString(), null, null, ipAddress, userAgent);

        log.info("User registered successfully. Verification token created for email: {}", savedUser.getEmail());
        return userService.toResponse(savedUser);
    }

    @Transactional
    public void verifyEmail(String token) {
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid email verification token"));

        if (verificationToken.isUsed()) {
            throw new InvalidTokenException("Email verification token has already been used");
        }

        if (verificationToken.isExpired()) {
            throw new TokenExpiredException("Email verification token has expired");
        }

        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        user.setEnabled(true);
        userRepository.save(user);

        verificationToken.setUsed(true);
        emailVerificationTokenRepository.save(verificationToken);

        auditService.logSuccess(user.getId(), user.getEmail(), "EMAIL_VERIFY", "USER",
                user.getId().toString(), null, null, "unknown", "unknown");

        log.info("Email verified successfully and user account enabled for user ID: {}", user.getId());
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        User user = userRepository.findByEmailOrUsername(request.getEmailOrUsername(), request.getEmailOrUsername())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Invalid email/username or password"));

        // Check lock status
        if (passwordService.isAccountLocked(user)) {
            LocalDateTime unlockTime = user.getLockTime().plusMinutes(appProperties.getSecurity().getLockDurationMinutes());
            throw new AccountLockedException("Account locked due to 5 failed login attempts. Please try again after " + unlockTime);
        }

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            passwordService.handleFailedLogin(user);
            throw new InvalidPasswordException("Invalid email/username or password");
        }

        // Check if email is verified
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException("Your email address is not verified. Please verify your email first.");
        }

        // Clear any failed attempts
        passwordService.resetFailedAttempts(user);

        // Check if MFA enabled
        if (user.isMfaEnabled()) {
            // Generate pending token
            String mfaToken = jwtTokenProvider.generateMfaPendingToken(user.getEmail(), 300000); // 5 mins
            auditService.logSuccess(user.getId(), user.getEmail(), "LOGIN_MFA_CHALLENGE", "USER",
                    user.getId().toString(), null, null, ipAddress, userAgent);
            throw new MfaRequiredException("MFA authentication required", mfaToken);
        }

        // Complete login
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        auditService.logSuccess(user.getId(), user.getEmail(), "USER_LOGIN", "USER",
                user.getId().toString(), null, null, ipAddress, userAgent);

        log.info("Successful login for user ID: {}", user.getId());
        return buildAuthResponse(user, ipAddress, userAgent);
    }

    @Transactional
    public AuthResponse verifyMfaLogin(MfaVerificationRequest request, String ipAddress, String userAgent) {
        String email = jwtTokenProvider.getMfaPendingEmail(request.getMfaToken());
        if (email == null) {
            throw new InvalidTokenException("MFA login session has expired or is invalid");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        if (!mfaService.verifyTotpCode(user, request.getOtpCode())) {
            auditService.logFailure(user.getId(), user.getEmail(), "LOGIN_MFA_FAIL", "USER", ipAddress, userAgent, "Invalid MFA Code");
            throw new InvalidTokenException("Invalid MFA code");
        }

        jwtTokenProvider.deleteMfaPendingToken(request.getMfaToken());

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        auditService.logSuccess(user.getId(), user.getEmail(), "USER_LOGIN_MFA", "USER",
                user.getId().toString(), null, null, ipAddress, userAgent);

        log.info("Successful MFA login for user ID: {}", user.getId());
        return buildAuthResponse(user, ipAddress, userAgent);
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request, String ipAddress, String userAgent) {
        String tokenHash = TokenUtils.hashToken(request.getRefreshToken());
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        if (refreshToken.isRevoked()) {
            // Potential theft! Revoke all tokens for this user for security
            refreshTokenRepository.revokeAllByUserId(refreshToken.getUser().getId());
            auditService.logFailure(refreshToken.getUser().getId(), refreshToken.getUser().getEmail(),
                    "TOKEN_REUSE_DETECTION", "REFRESH_TOKEN", ipAddress, userAgent, "Revoked refresh token reuse attempted");
            throw new InvalidTokenException("This refresh token has been revoked. All sessions terminated for safety.");
        }

        if (refreshToken.isExpired()) {
            throw new TokenExpiredException("Refresh token has expired. Please login again.");
        }

        // Revoke the old token (rotation)
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        User user = refreshToken.getUser();
        auditService.logSuccess(user.getId(), user.getEmail(), "TOKEN_ROTATE", "REFRESH_TOKEN",
                refreshToken.getId().toString(), null, null, ipAddress, userAgent);

        log.info("Token rotated for user ID: {}", user.getId());
        return buildAuthResponse(user, ipAddress, userAgent);
    }

    @Transactional
    public void logout(String accessToken, Long userId) {
        if (accessToken != null) {
            long remainingMs = jwtTokenProvider.getRemainingExpiryMs(accessToken);
            jwtTokenProvider.blacklistToken(accessToken, remainingMs);
        }
        log.info("Logged out user ID: {}", userId);
    }

    @Transactional
    public void logoutAll(String accessToken, Long userId) {
        if (accessToken != null) {
            long remainingMs = jwtTokenProvider.getRemainingExpiryMs(accessToken);
            jwtTokenProvider.blacklistToken(accessToken, remainingMs);
        }
        
        // Revoke all DB tokens and sessions
        refreshTokenRepository.revokeAllByUserId(userId);
        sessionService.revokeAllSessions(userId);
        
        log.info("Terminated all sessions for user ID: {}", userId);
    }

    private AuthResponse buildAuthResponse(User user, String ipAddress, String userAgent) {
        // Load authorities
        org.springframework.security.core.userdetails.User.UserBuilder userBuilder = org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .disabled(!user.isEnabled())
                .accountLocked(user.isAccountLocked());

        Set<String> authorities = new HashSet<>();
        user.getRoles().forEach(role -> {
            authorities.add("ROLE_" + role.getName());
            role.getPermissions().forEach(permission -> authorities.add(permission.getName()));
        });

        userBuilder.authorities(authorities.stream().map(org.springframework.security.core.authority.SimpleGrantedAuthority::new).collect(Collectors.toList()));

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        claims.put("email", user.getEmail());
        claims.put("roles", user.getRoles().stream().map(Role::getName).collect(Collectors.toList()));

        String accessToken = jwtTokenProvider.generateAccessToken(userBuilder.build(), claims);
        String rawRefreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail());
        String tokenHash = TokenUtils.hashToken(rawRefreshToken);

        // Save refresh token to DB
        RefreshToken rt = RefreshToken.builder()
                .tokenHash(tokenHash)
                .user(user)
                .expiresAt(LocalDateTime.now().plusSeconds(appProperties.getJwt().getRefreshTokenExpiration() / 1000))
                .revoked(false)
                .deviceInfo(userAgent)
                .ipAddress(ipAddress)
                .build();
        refreshTokenRepository.save(rt);

        // Register new session
        UserSession session = sessionService.createSession(user, ipAddress, userAgent);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(appProperties.getJwt().getAccessTokenExpiration() / 1000)
                .mfaRequired(false)
                .user(userService.toResponse(user))
                .build();
    }
}
