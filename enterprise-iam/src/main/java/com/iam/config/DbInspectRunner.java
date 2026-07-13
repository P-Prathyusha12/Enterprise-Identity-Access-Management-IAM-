package com.iam.config;

import com.iam.repository.RoleRepository;
import com.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DbInspectRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        log.info("======== DATABASE INSPECTION ON STARTUP ========");
        
        if (!userRepository.existsByUsername("superadmin")) {
            log.info("Superadmin not found in database! Auto-seeding default administrative users and roles...");
            try {
                // 1. Roles
                jdbcTemplate.execute("INSERT INTO roles (id, name, description, created_at, updated_at) VALUES " +
                        "(1, 'SUPER_ADMIN', 'Super administrator with all permissions', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP), " +
                        "(2, 'ADMIN', 'System administrator with user management rights', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP), " +
                        "(3, 'MANAGER', 'Manager with read access to audit logs and user queries', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP), " +
                        "(4, 'USER', 'Standard platform user', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                        "ON DUPLICATE KEY UPDATE name=name");

                // 2. Permissions
                jdbcTemplate.execute("INSERT INTO permissions (id, name, resource, action, description, created_at) VALUES " +
                        "(1, 'user:read', 'user', 'read', 'Read user profiles and lists', CURRENT_TIMESTAMP), " +
                        "(2, 'user:write', 'user', 'write', 'Create and update users', CURRENT_TIMESTAMP), " +
                        "(3, 'user:delete', 'user', 'delete', 'Delete users from system', CURRENT_TIMESTAMP), " +
                        "(4, 'role:read', 'role', 'read', 'Read system roles', CURRENT_TIMESTAMP), " +
                        "(5, 'role:manage', 'role', 'manage', 'Create, update and delete roles', CURRENT_TIMESTAMP), " +
                        "(6, 'permission:read', 'permission', 'read', 'Read system permissions', CURRENT_TIMESTAMP), " +
                        "(7, 'permission:manage', 'permission', 'manage', 'Manage role-permission mappings', CURRENT_TIMESTAMP), " +
                        "(8, 'audit:read', 'audit', 'read', 'Read audit log trails', CURRENT_TIMESTAMP), " +
                        "(9, 'session:manage', 'session', 'manage', 'View and revoke active user sessions', CURRENT_TIMESTAMP) " +
                        "ON DUPLICATE KEY UPDATE name=name");

                // 3. Role Permissions mapping
                jdbcTemplate.execute("INSERT INTO role_permissions (role_id, permission_id) VALUES " +
                        "(1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6), (1, 7), (1, 8), (1, 9) " +
                        "ON DUPLICATE KEY UPDATE role_id=role_id");

                // 4. Superadmin User — use live PasswordEncoder for correct hash
                String encodedPassword = passwordEncoder.encode("Admin@123");
                jdbcTemplate.update("INSERT INTO users (id, username, email, password_hash, first_name, last_name, phone, is_enabled, is_email_verified, is_account_locked, failed_login_attempts, lock_time, mfa_enabled, created_at, updated_at) VALUES " +
                        "(1, 'superadmin', 'superadmin@enterprise-iam.com', ?, 'Super', 'Admin', '+1234567890', TRUE, TRUE, FALSE, 0, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                        "ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash)", encodedPassword);

                // 5. User Roles mapping
                jdbcTemplate.execute("INSERT INTO user_roles (user_id, role_id) VALUES (1, 1) ON DUPLICATE KEY UPDATE user_id=user_id");

                log.info("Database auto-seeding completed! Superadmin password updated with fresh encoded hash.");
            } catch (Exception e) {
                log.error("Failed to auto-seed database: {}", e.getMessage(), e);
            }
        } else {
            // Superadmin exists — always refresh the password hash to guarantee it matches Admin@123
            try {
                String encodedPassword = passwordEncoder.encode("Admin@123");
                jdbcTemplate.update("UPDATE users SET password_hash = ? WHERE username = 'superadmin'", encodedPassword);
                log.info("Superadmin password hash refreshed on startup.");
            } catch (Exception e) {
                log.error("Failed to refresh superadmin password: {}", e.getMessage(), e);
            }
        }

        log.info("=== ROLES IN DATABASE ===");
        roleRepository.findAll().forEach(role ->
            log.info("Role ID: {}, Name: {}", role.getId(), role.getName())
        );

        log.info("=== USERS IN DATABASE ===");
        userRepository.findAll().forEach(user ->
            log.info("User ID: {}, Username: {}, Email: {}, Enabled: {}, EmailVerified: {}",
                user.getId(), user.getUsername(), user.getEmail(), user.isEnabled(), user.isEmailVerified())
        );

        log.info("================================================");
    }
}
