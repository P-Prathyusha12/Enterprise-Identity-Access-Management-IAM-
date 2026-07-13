CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    username VARCHAR(100) NULL,
    action VARCHAR(100) NOT NULL,
    resource VARCHAR(100) NOT NULL,
    resource_id VARCHAR(100),
    old_value JSON,
    new_value JSON,
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    error_message VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_al_user_id (user_id),
    INDEX idx_al_action (action),
    INDEX idx_al_created_at (created_at),
    INDEX idx_al_resource (resource)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
