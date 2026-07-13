package com.iam.service;

import com.iam.dto.response.AuditLogResponse;
import com.iam.entity.AuditLog;
import com.iam.repository.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Async("taskExecutor")
    public void log(Long userId, String username, String action, String resource,
                    String resourceId, String oldValue, String newValue,
                    String ipAddress, String userAgent, String status, String errorMessage) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .userId(userId)
                    .username(username)
                    .action(action)
                    .resource(resource)
                    .resourceId(resourceId)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .status(status)
                    .errorMessage(errorMessage)
                    .build();
            auditLogRepository.save(auditLog);
            log.debug("Audit log saved: Action={}, Resource={}, Status={}", action, resource, status);
        } catch (Exception e) {
            log.error("Failed to save audit log", e);
        }
    }

    @Async("taskExecutor")
    public void logSuccess(Long userId, String username, String action, String resource,
                           String resourceId, String oldValue, String newValue,
                           String ipAddress, String userAgent) {
        log(userId, username, action, resource, resourceId, oldValue, newValue, ipAddress, userAgent, "SUCCESS", null);
    }

    @Async("taskExecutor")
    public void logFailure(Long userId, String username, String action, String resource,
                           String ipAddress, String userAgent, String errorMessage) {
        log(userId, username, action, resource, null, null, null, ipAddress, userAgent, "FAILURE", errorMessage);
    }

    public Page<AuditLog> getAuditLogs(Long userId, String action, String resource,
                                      LocalDateTime fromDate, LocalDateTime toDate, Pageable pageable) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (resource != null && !resource.isBlank()) {
                predicates.add(cb.equal(root.get("resource"), resource));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return auditLogRepository.findAll(spec, pageable);
    }

    public AuditLogResponse toResponse(AuditLog log) {
        if (log == null) {
            return null;
        }
        return AuditLogResponse.builder()
                .id(log.getId())
                .userId(log.getUserId())
                .username(log.getUsername())
                .action(log.getAction())
                .resource(log.getResource())
                .resourceId(log.getResourceId())
                .oldValue(log.getOldValue())
                .newValue(log.getNewValue())
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
                .status(log.getStatus())
                .errorMessage(log.getErrorMessage())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
