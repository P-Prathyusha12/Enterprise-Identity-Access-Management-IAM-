package com.iam.util;

import com.iam.exception.ResourceNotFoundException;
import com.iam.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Utility class for Spring Security context operations.
 * Provides helper methods to extract current user info and HTTP request metadata.
 */
@Component
@RequiredArgsConstructor
public class SecurityUtils {

    private final UserRepository userRepository;

    /**
     * Retrieves the ID of the currently authenticated user from the SecurityContext.
     *
     * @return the ID of the current user
     * @throws AuthenticationCredentialsNotFoundException if no authentication is present
     * @throws ResourceNotFoundException                 if the user email is not found
     */
    public Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Not authenticated");
        }
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email))
                .getId();
    }

    /**
     * Retrieves the email (username) of the currently authenticated user.
     *
     * @return email string
     * @throws AuthenticationCredentialsNotFoundException if no authentication is present
     */
    public String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Not authenticated");
        }
        return auth.getName();
    }

    /**
     * Extracts the client IP address from the request, respecting proxy headers.
     *
     * @param request the incoming HTTP request
     * @return the client IP address
     */
    public static String extractIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // Handle multiple IPs in X-Forwarded-For (take first one)
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }

    /**
     * Extracts the User-Agent header from the request.
     *
     * @param request the incoming HTTP request
     * @return the User-Agent string, or "unknown" if not present
     */
    public static String extractUserAgent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        return ua != null ? ua : "unknown";
    }

    /**
     * Extracts the Bearer token from the Authorization header.
     *
     * @param request the incoming HTTP request
     * @return the raw JWT token string, or null if not present
     */
    public static String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
