-- Seed default roles
INSERT INTO roles (id, name, description, created_at, updated_at) VALUES
(1, 'SUPER_ADMIN', 'Super administrator with all permissions', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 'ADMIN', 'System administrator with user management rights', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 'MANAGER', 'Manager with read access to audit logs and user queries', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4, 'USER', 'Standard platform user', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE name=name;

-- Seed permissions
INSERT INTO permissions (id, name, resource, action, description, created_at) VALUES
(1, 'user:read', 'user', 'read', 'Read user profiles and lists', CURRENT_TIMESTAMP),
(2, 'user:write', 'user', 'write', 'Create and update users', CURRENT_TIMESTAMP),
(3, 'user:delete', 'user', 'delete', 'Delete users from system', CURRENT_TIMESTAMP),
(4, 'role:read', 'role', 'read', 'Read system roles', CURRENT_TIMESTAMP),
(5, 'role:manage', 'role', 'manage', 'Create, update and delete roles', CURRENT_TIMESTAMP),
(6, 'permission:read', 'permission', 'read', 'Read system permissions', CURRENT_TIMESTAMP),
(7, 'permission:manage', 'permission', 'manage', 'Manage role-permission mappings', CURRENT_TIMESTAMP),
(8, 'audit:read', 'audit', 'read', 'Read audit log trails', CURRENT_TIMESTAMP),
(9, 'session:manage', 'session', 'manage', 'View and revoke active user sessions', CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE name=name;

-- Assign permissions to SUPER_ADMIN (Role ID 1 - gets all permissions)
INSERT INTO role_permissions (role_id, permission_id) VALUES
(1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6), (1, 7), (1, 8), (1, 9)
ON DUPLICATE KEY UPDATE role_id=role_id;

-- Assign permissions to ADMIN (Role ID 2)
INSERT INTO role_permissions (role_id, permission_id) VALUES
(2, 1), (2, 2), (2, 4), (2, 6), (2, 8), (2, 9)
ON DUPLICATE KEY UPDATE role_id=role_id;

-- Assign permissions to MANAGER (Role ID 3)
INSERT INTO role_permissions (role_id, permission_id) VALUES
(3, 1), (3, 4), (3, 8)
ON DUPLICATE KEY UPDATE role_id=role_id;

-- Assign permissions to USER (Role ID 4)
INSERT INTO role_permissions (role_id, permission_id) VALUES
(4, 1)
ON DUPLICATE KEY UPDATE role_id=role_id;

-- Create default superadmin user
-- Password is 'Admin@123'
-- Hash is BCrypt(12) of 'Admin@123' -> $2a$12$LBHyRkJLRBV3OBcLo6JWBuRj3GhkFf4.VJO7MCv4pHZYYSQH9u3yO
INSERT INTO users (id, username, email, password_hash, first_name, last_name, phone, is_enabled, is_email_verified, is_account_locked, failed_login_attempts, lock_time, mfa_enabled, created_at, updated_at) VALUES
(1, 'superadmin', 'superadmin@enterprise-iam.com', '$2a$12$LBHyRkJLRBV3OBcLo6JWBuRj3GhkFf4.VJO7MCv4pHZYYSQH9u3yO', 'Super', 'Admin', '+1234567890', TRUE, TRUE, FALSE, 0, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE username=username;

-- Assign SUPER_ADMIN role to superadmin user
INSERT INTO user_roles (user_id, role_id) VALUES
(1, 1)
ON DUPLICATE KEY UPDATE user_id=user_id;
