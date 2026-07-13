package com.iam.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoleService Unit Tests")
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PermissionService permissionService;

    @InjectMocks
    private RoleService roleService;

    private Role testRole;

    @BeforeEach
    void setUp() {
        testRole = Role.builder()
                .id(1L)
                .name("ROLE_TEST")
                .description("Test role")
                .permissions(new HashSet<>())
                .build();
    }

    @Test
    @DisplayName("CreateRole - should add ROLE_ prefix if missing")
    void createRole_shouldAddPrefix_whenMissing() {
        // Given
        CreateRoleRequest request = CreateRoleRequest.builder()
                .name("MANAGER")
                .description("Manager role")
                .build();

        when(roleRepository.existsByName("ROLE_MANAGER")).thenReturn(false);
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> {
            Role role = inv.getArgument(0);
            role.setId(2L);
            return role;
        });
        when(permissionService.toResponse(any())).thenReturn(null);

        // When
        RoleResponse response = roleService.createRole(request);

        // Then
        assertThat(response.getName()).isEqualTo("ROLE_MANAGER");
    }

    @Test
    @DisplayName("CreateRole - should throw when role name already exists")
    void createRole_shouldThrow_whenRoleNameExists() {
        // Given
        CreateRoleRequest request = CreateRoleRequest.builder()
                .name("ROLE_TEST")
                .description("Test")
                .build();

        when(roleRepository.existsByName("ROLE_TEST")).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> roleService.createRole(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Role name already exists");
    }

    @Test
    @DisplayName("DeleteRole - should throw when role has assigned users")
    void deleteRole_shouldThrow_whenRoleHasAssignedUsers() {
        // Given
        when(roleRepository.findById(1L)).thenReturn(Optional.of(testRole));
        when(roleRepository.existsAssignedUsers(1L)).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> roleService.deleteRole(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("users are currently assigned");
    }

    @Test
    @DisplayName("DeleteRole - should throw when trying to delete system roles")
    void deleteRole_shouldThrow_whenDeletingSystemRoles() {
        // Given
        Role superAdminRole = Role.builder()
                .id(1L)
                .name("ROLE_SUPER_ADMIN")
                .permissions(new HashSet<>())
                .build();

        when(roleRepository.findById(1L)).thenReturn(Optional.of(superAdminRole));

        // When/Then
        assertThatThrownBy(() -> roleService.deleteRole(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot delete default system roles");
    }

    @Test
    @DisplayName("GetRoleById - should throw when role not found")
    void getRoleById_shouldThrow_whenRoleNotFound() {
        // Given
        when(roleRepository.findById(999L)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> roleService.getRoleById(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
