package com.iam.controller;

import com.iam.dto.request.ForgotPasswordRequest;
import com.iam.dto.request.LoginRequest;
import com.iam.dto.request.MfaVerificationRequest;
import com.iam.dto.request.RefreshTokenRequest;
import com.iam.dto.request.RegisterRequest;
import com.iam.dto.request.ResetPasswordRequest;
import com.iam.dto.response.ApiResponse;
import com.iam.dto.response.AuthResponse;
import com.iam.dto.response.UserResponse;
import com.iam.service.AuthService;
import com.iam.service.PasswordService;
import com.iam.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Endpoints for registration, login, token refresh, and password recovery")
@Validated
public class AuthController {

    private final AuthService authService;
    private final PasswordService passwordService;
    private final SecurityUtils securityUtils;
    private final HttpServletRequest servletRequest;

    @PostMapping("/register")
    @Operation(summary = "Register a new user account")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest registerRequest) {
        String ip = SecurityUtils.extractIpAddress(servletRequest);
        String ua = SecurityUtils.extractUserAgent(servletRequest);
        UserResponse response = authService.register(registerRequest, ip, ua);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Registration successful. Please check your email to verify your account."));
    }

    @GetMapping("/verify-email")
    @Operation(summary = "Verify user email address")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.success("Email verified successfully. You can now login."));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and issue JWT tokens")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest loginRequest) {
        String ip = SecurityUtils.extractIpAddress(servletRequest);
        String ua = SecurityUtils.extractUserAgent(servletRequest);
        AuthResponse response = authService.login(loginRequest, ip, ua);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    @PostMapping("/login/mfa")
    @Operation(summary = "Submit MFA OTP code to complete login")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyMfa(@Valid @RequestBody MfaVerificationRequest mfaRequest) {
        String ip = SecurityUtils.extractIpAddress(servletRequest);
        String ua = SecurityUtils.extractUserAgent(servletRequest);
        AuthResponse response = authService.verifyMfaLogin(mfaRequest, ip, ua);
        return ResponseEntity.ok(ApiResponse.success(response, "MFA code verified successfully"));
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Rotate refresh token and issue new access token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest refreshRequest) {
        String ip = SecurityUtils.extractIpAddress(servletRequest);
        String ua = SecurityUtils.extractUserAgent(servletRequest);
        AuthResponse response = authService.refreshToken(refreshRequest, ip, ua);
        return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed successfully"));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout the current session")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<Void>> logout() {
        String token = SecurityUtils.extractBearerToken(servletRequest);
        Long userId = securityUtils.getCurrentUserId();
        authService.logout(token, userId);
        return ResponseEntity.ok(ApiResponse.success("Logout successful"));
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Logout all active user sessions")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<Void>> logoutAll() {
        String token = SecurityUtils.extractBearerToken(servletRequest);
        Long userId = securityUtils.getCurrentUserId();
        authService.logoutAll(token, userId);
        return ResponseEntity.ok(ApiResponse.success("Terminated all active user sessions successfully"));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Submit password recovery request")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest forgotRequest) {
        String ip = SecurityUtils.extractIpAddress(servletRequest);
        String ua = SecurityUtils.extractUserAgent(servletRequest);
        passwordService.forgotPassword(forgotRequest.getEmail(), ip, ua);
        return ResponseEntity.ok(ApiResponse.success("If the email address exists, a password reset link has been sent."));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Submit password reset with token")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest resetRequest) {
        String ip = SecurityUtils.extractIpAddress(servletRequest);
        String ua = SecurityUtils.extractUserAgent(servletRequest);
        passwordService.resetPassword(resetRequest, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully. You can now login with your new password."));
    }
}
