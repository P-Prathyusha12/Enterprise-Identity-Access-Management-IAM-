package com.iam.service;

import com.iam.dto.request.AssignPermissionRequest;
import com.iam.dto.request.CreateRoleRequest;
import com.iam.dto.response.RoleResponse;
import com.iam.entity.Permission;
import com.iam.entity.Role;
import com.iam.exception.BadRequestException;
import com.iam.exception.DuplicateResourceException;
import com.iam.exception.ResourceNotFoundException;
import com.iam.repository.PermissionRepository;
import com.iam.repository.RoleRepository;
import com.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;

    @Transactional
    public RoleResponse createRole(CreateRoleRequest request) {
        String roleName = request.getName().toUpperCase();
        if (roleName.startsWith("ROLE_")) {
            roleName = roleName.substring(5);
        }

        if (roleRepository.existsByName(roleName)) {
            throw new DuplicateResourceException("Role name already exists: " + roleName);
        }

        Role role = Role.builder()
                .name(roleName)
                .description(request.getDescription())
                .permissions(new HashSet<>())
                .build();

        Role savedRole = roleRepository.save(role);
        log.info("Created new role: {}", savedRole.getName());
        return toResponse(savedRole);
    }

    public List<RoleResponse> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public RoleResponse getRoleById(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", id));
        return toResponse(role);
    }

    @Transactional
    public RoleResponse updateRole(Long id, CreateRoleRequest request) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", id));

        String targetName = request.getName().toUpperCase();
        if (targetName.startsWith("ROLE_")) {
            targetName = targetName.substring(5);
        }

        if (!role.getName().equals(targetName) && roleRepository.existsByName(targetName)) {
            throw new DuplicateResourceException("Role name already exists: " + targetName);
        }

        // Prevent modification of system default SUPER_ADMIN role name
        if ("SUPER_ADMIN".equals(role.getName()) && !role.getName().equals(targetName)) {
            throw new BadRequestException("Cannot rename the default SUPER_ADMIN role");
        }

        role.setName(targetName);
        role.setDescription(request.getDescription());

        Role updatedRole = roleRepository.save(role);
        log.info("Updated role: {}", updatedRole.getName());
        return toResponse(updatedRole);
    }

    @Transactional
    public void deleteRole(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", id));

        if ("SUPER_ADMIN".equals(role.getName()) || "USER".equals(role.getName())) {
            throw new BadRequestException("Cannot delete default system roles: SUPER_ADMIN or USER");
        }

        // Check if users are assigned to this role
        // In user_roles table, we can verify if any users are assigned
        // For simplicity, we query role's users if mapped, or count from userRepository
        // Let's assume we fetch role's users or count users having this role.
        // We can execute a custom check using UserRepository
        boolean hasAssignedUsers = roleRepository.existsAssignedUsers(id);
        if (hasAssignedUsers) {
            throw new BadRequestException("Cannot delete role '" + role.getName() + "' because users are currently assigned to it");
        }

        roleRepository.delete(role);
        log.info("Deleted role: {}", role.getName());
    }

    @Transactional
    public RoleResponse assignPermissions(Long roleId, AssignPermissionRequest request) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", roleId));

        if ("SUPER_ADMIN".equals(role.getName())) {
            throw new BadRequestException("Cannot modify permissions of the system SUPER_ADMIN");
        }

        Set<Permission> permissions = new HashSet<>(permissionRepository.findAllById(request.getPermissionIds()));
        if (permissions.size() != request.getPermissionIds().size()) {
            throw new ResourceNotFoundException("Permissions", "ids", request.getPermissionIds());
        }

        role.setPermissions(permissions);
        Role updatedRole = roleRepository.save(role);
        log.info("Assigned {} permissions to role {}", permissions.size(), role.getName());
        return toResponse(updatedRole);
    }

    @Transactional
    public RoleResponse removePermission(Long roleId, Long permissionId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", roleId));

        if ("SUPER_ADMIN".equals(role.getName())) {
            throw new BadRequestException("Cannot modify permissions of the system SUPER_ADMIN");
        }

        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "id", permissionId));

        role.getPermissions().remove(permission);
        Role updatedRole = roleRepository.save(role);
        log.info("Removed permission {} from role {}", permission.getName(), role.getName());
        return toResponse(updatedRole);
    }

    public RoleResponse toResponse(Role role) {
        if (role == null) {
            return null;
        }
        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .createdAt(role.getCreatedAt())
                .permissions(role.getPermissions().stream()
                        .map(permissionService::toResponse)
                        .collect(Collectors.toSet()))
                .build();
    }
}
