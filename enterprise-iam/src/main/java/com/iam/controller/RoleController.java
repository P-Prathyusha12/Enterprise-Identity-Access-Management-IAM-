package com.iam.controller;

import com.iam.dto.request.AssignPermissionRequest;
import com.iam.dto.request.CreateRoleRequest;
import com.iam.dto.response.ApiResponse;
import com.iam.dto.response.RoleResponse;
import com.iam.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Role Operations", description = "Endpoints for creating and updating system roles and managing their permissions")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
@Validated
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @Operation(summary = "List all system roles")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAllRoles() {
        List<RoleResponse> roles = roleService.getAllRoles();
        return ResponseEntity.ok(ApiResponse.success(roles, "System roles retrieved successfully"));
    }

    @PostMapping
    @Operation(summary = "Create a new system role")
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(@Valid @RequestBody CreateRoleRequest request) {
        RoleResponse role = roleService.createRole(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(role, "Role created successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get system role by ID")
    public ResponseEntity<ApiResponse<RoleResponse>> getRoleById(@PathVariable Long id) {
        RoleResponse role = roleService.getRoleById(id);
        return ResponseEntity.ok(ApiResponse.success(role, "Role details retrieved successfully"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update system role details")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody CreateRoleRequest request
    ) {
        RoleResponse role = roleService.updateRole(id, request);
        return ResponseEntity.ok(ApiResponse.success(role, "Role updated successfully"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete system role")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ResponseEntity.ok(ApiResponse.success("Role deleted successfully"));
    }

    @PostMapping("/{id}/permissions")
    @Operation(summary = "Assign permission mapping set to role")
    public ResponseEntity<ApiResponse<RoleResponse>> assignPermissionsToRole(
            @PathVariable Long id,
            @Valid @RequestBody AssignPermissionRequest request
    ) {
        RoleResponse role = roleService.assignPermissions(id, request);
        return ResponseEntity.ok(ApiResponse.success(role, "Permissions assigned to role successfully"));
    }

    @DeleteMapping("/{roleId}/permissions/{permissionId}")
    @Operation(summary = "Revoke specific permission from role")
    public ResponseEntity<ApiResponse<RoleResponse>> removePermissionFromRole(
            @PathVariable Long roleId,
            @PathVariable Long permissionId
    ) {
        RoleResponse role = roleService.removePermission(roleId, permissionId);
        return ResponseEntity.ok(ApiResponse.success(role, "Permission removed from role successfully"));
    }
}
