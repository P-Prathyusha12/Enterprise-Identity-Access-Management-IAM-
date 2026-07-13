package com.iam.controller;

import com.iam.dto.request.CreatePermissionRequest;
import com.iam.dto.response.ApiResponse;
import com.iam.dto.response.PermissionResponse;
import com.iam.service.PermissionService;
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
@RequestMapping("/api/v1/admin/permissions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Permission Operations", description = "Endpoints for permission settings catalog management")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
@Validated
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    @Operation(summary = "List all platform system permissions")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getAllPermissions() {
        List<PermissionResponse> permissions = permissionService.getAllPermissions();
        return ResponseEntity.ok(ApiResponse.success(permissions, "System permissions catalog retrieved successfully"));
    }

    @PostMapping
    @Operation(summary = "Create a new system permission")
    public ResponseEntity<ApiResponse<PermissionResponse>> createPermission(@Valid @RequestBody CreatePermissionRequest request) {
        PermissionResponse permission = permissionService.createPermission(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(permission, "Permission created successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get system permission by ID")
    public ResponseEntity<ApiResponse<PermissionResponse>> getPermissionById(@PathVariable Long id) {
        PermissionResponse permission = permissionService.getPermissionById(id);
        return ResponseEntity.ok(ApiResponse.success(permission, "Permission details retrieved successfully"));
    }

    @GetMapping("/resource/{resource}")
    @Operation(summary = "List permissions filtering by targeted resource type")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getPermissionsByResource(@PathVariable String resource) {
        List<PermissionResponse> permissions = permissionService.getPermissionsByResource(resource);
        return ResponseEntity.ok(ApiResponse.success(permissions, "Permissions list for resource '" + resource + "' retrieved successfully"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update system permission details")
    public ResponseEntity<ApiResponse<PermissionResponse>> updatePermission(
            @PathVariable Long id,
            @Valid @RequestBody CreatePermissionRequest request
    ) {
        PermissionResponse permission = permissionService.updatePermission(id, request);
        return ResponseEntity.ok(ApiResponse.success(permission, "Permission updated successfully"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete system permission from database catalog")
    public ResponseEntity<ApiResponse<Void>> deletePermission(@PathVariable Long id) {
        permissionService.deletePermission(id);
        return ResponseEntity.ok(ApiResponse.success("Permission deleted successfully"));
    }
}
