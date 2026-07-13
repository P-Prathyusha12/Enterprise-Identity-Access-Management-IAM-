package com.iam.service;

import com.iam.config.AppProperties;
import com.iam.dto.response.SessionResponse;
import com.iam.entity.User;
import com.iam.entity.UserSession;
import com.iam.exception.ResourceNotFoundException;
import com.iam.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final UserSessionRepository sessionRepository;
    private final AppProperties appProperties;

    @Transactional
    public UserSession createSession(User user, String ipAddress, String userAgent) {
        String sessionId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        long expiryMs = appProperties.getJwt().getRefreshTokenExpiration();
        LocalDateTime expiresAt = now.plusSeconds(expiryMs / 1000);

        UserSession session = UserSession.builder()
                .sessionId(sessionId)
                .user(user)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .createdAt(now)
                .lastAccessedAt(now)
                .expiresAt(expiresAt)
                .active(true)
                .build();

        return sessionRepository.save(session);
    }

    public List<SessionResponse> getActiveSessions(Long userId, String currentSessionId) {
        List<UserSession> activeSessions = sessionRepository.findByUserIdAndActiveTrue(userId);
        
        // Remove expired sessions on the fly
        LocalDateTime now = LocalDateTime.now();
        List<UserSession> validSessions = activeSessions.stream()
                .filter(s -> {
                    if (s.getExpiresAt().isBefore(now)) {
                        s.setActive(false);
                        sessionRepository.save(s);
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());

        return validSessions.stream()
                .map(s -> toResponse(s, currentSessionId))
                .collect(Collectors.toList());
    }

    @Transactional
    public void revokeSession(String sessionId, Long userId) {
        UserSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("UserSession", "sessionId", sessionId));

        if (!session.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Session does not belong to the authenticated user");
        }

        session.setActive(false);
        sessionRepository.save(session);
        log.info("Session revoked: {}", sessionId);
    }

    @Transactional
    public void revokeAllSessions(Long userId) {
        sessionRepository.deactivateAllByUserId(userId);
        log.info("All active sessions revoked for user ID: {}", userId);
    }

    @Transactional
    public void updateLastAccessed(String sessionId) {
        try {
            sessionRepository.findBySessionId(sessionId).ifPresent(session -> {
                if (session.isActive() && session.getExpiresAt().isAfter(LocalDateTime.now())) {
                    session.setLastAccessedAt(LocalDateTime.now());
                    sessionRepository.save(session);
                }
            });
        } catch (Exception e) {
            log.error("Failed to update last accessed time for session: {}", sessionId, e);
        }
    }

    public SessionResponse toResponse(UserSession session, String currentSessionId) {
        if (session == null) {
            return null;
        }
        return SessionResponse.builder()
                .id(session.getId())
                .sessionId(session.getSessionId())
                .ipAddress(session.getIpAddress())
                .userAgent(session.getUserAgent())
                .createdAt(session.getCreatedAt())
                .lastAccessedAt(session.getLastAccessedAt())
                .expiresAt(session.getExpiresAt())
                .active(session.isActive())
                .currentSession(session.getSessionId().equals(currentSessionId))
                .build();
    }
}
