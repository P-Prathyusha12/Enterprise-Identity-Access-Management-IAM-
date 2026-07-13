package com.iam.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {

    private Long id;
    private Long userId;
    private String username;
    private String action;
    private String resource;
    private String resourceId;
    private String oldValue;
    private String newValue;
    private String ipAddress;
    private String userAgent;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;
}
