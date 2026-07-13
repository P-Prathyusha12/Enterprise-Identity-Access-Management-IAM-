package com.iam.controller;

import com.iam.dto.request.ChangePasswordRequest;
import com.iam.dto.request.UpdateProfileRequest;
import com.iam.dto.response.ApiResponse;
import com.iam.dto.response.MfaSetupResponse;
import com.iam.dto.response.SessionResponse;
import com.iam.dto.response.UserResponse;
import com.iam.service.MfaService;
import com.iam.service.PasswordService;
import com.iam.service.SessionService;
import com.iam.service.UserService;
import com.iam.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Management", description = "Endpoints for profile management, password updates, MFA setup, and session control")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class UserController {

    private final UserService userService;
    private final PasswordService passwordService;
    private final MfaService mfaService;
    private final SessionService sessionService;
    private final SecurityUtils securityUtils;
    private final HttpServletRequest servletRequest;

    @GetMapping("/me")
    @Operation(summary = "Retrieve current user profile")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile() {
        Long userId = securityUtils.getCurrentUserId();
        UserResponse profile = userService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.success(profile, "Profile retrieved successfully"));
    }

    @PutMapping("/me")
    @Operation(summary = "Update current user profile info")
    public ResponseEntity<ApiResponse<UserResponse>> updateMyProfile(@Valid @RequestBody UpdateProfileRequest updateRequest) {
        Long userId = securityUtils.getCurrentUserId();
        UserResponse updatedProfile = userService.updateProfile(userId, updateRequest);
        return ResponseEntity.ok(ApiResponse.success(updatedProfile, "Profile updated successfully"));
    }

    @PutMapping("/me/password")
    @Operation(summary = "Change password for authenticated user")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest changeRequest) {
        Long userId = securityUtils.getCurrentUserId();
        String ip = SecurityUtils.extractIpAddress(servletRequest);
        String ua = SecurityUtils.extractUserAgent(servletRequest);
        passwordService.changePassword(userId, changeRequest, ip, ua);
        return ResponseEntity.ok(ApiResponse.success("Password updated successfully. Other active sessions revoked."));
    }

    @PostMapping("/me/mfa/setup")
    @Operation(summary = "Initiate TOTP Multi-Factor Authentication setup")
    public ResponseEntity<ApiResponse<MfaSetupResponse>> initiateMfaSetup() {
        Long userId = securityUtils.getCurrentUserId();
        MfaSetupResponse response = mfaService.setupMfa(userId);
        return ResponseEntity.ok(ApiResponse.success(response, "MFA setup initiated. Verify using the QR code."));
    }

    @PostMapping("/me/mfa/verify")
    @Operation(summary = "Confirm and enable TOTP Multi-Factor Authentication")
    public ResponseEntity<ApiResponse<Void>> enableMfa(@RequestBody Map<String, String> request) {
        String code = request.get("code");
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("MFA validation code is required");
        }
        Long userId = securityUtils.getCurrentUserId();
        mfaService.verifyAndEnableMfa(userId, code);
        return ResponseEntity.ok(ApiResponse.success("MFA successfully enabled on your account"));
    }

    @DeleteMapping("/me/mfa")
    @Operation(summary = "Disable Multi-Factor Authentication")
    public ResponseEntity<ApiResponse<Void>> disableMfa() {
        Long userId = securityUtils.getCurrentUserId();
        mfaService.disableMfa(userId);
        return ResponseEntity.ok(ApiResponse.success("MFA successfully disabled on your account"));
    }

    @GetMapping("/me/sessions")
    @Operation(summary = "Retrieve active user sessions")
    public ResponseEntity<ApiResponse<List<SessionResponse>>> getMyActiveSessions() {
        Long userId = securityUtils.getCurrentUserId();
        String currentToken = SecurityUtils.extractBearerToken(servletRequest);
        
        // Find session ID from token - we can query the token hash or just compare headers
        // For simplicity, we just retrieve sessions list. We will match session by IP/agent.
        // Let's pass null or match in services
        List<SessionResponse> sessions = sessionService.getActiveSessions(userId, currentToken);
        return ResponseEntity.ok(ApiResponse.success(sessions, "Active sessions retrieved successfully"));
    }

    @DeleteMapping("/me/sessions/{sessionId}")
    @Operation(summary = "Revoke/terminate a specific session")
    public ResponseEntity<ApiResponse<Void>> revokeSession(@PathVariable String sessionId) {
        Long userId = securityUtils.getCurrentUserId();
        sessionService.revokeSession(sessionId, userId);
        return ResponseEntity.ok(ApiResponse.success("Session successfully terminated"));
    }
}
