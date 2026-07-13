package com.iam.controller;

import com.iam.dto.response.ApiResponse;
import com.iam.dto.response.PagedResponse;
import com.iam.dto.response.UserResponse;
import com.iam.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin User Operations", description = "Dashboard operations for administrative user accounts lifecycle, lock/unlock, roles assignment")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
@Validated
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Query and search users in system (Paginated)")
    public ResponseEntity<ApiResponse<PagedResponse<UserResponse>>> getAllUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        Sort sort = direction.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<UserResponse> users = userService.getAllUsers(search, enabled, pageable);
        PagedResponse<UserResponse> response = PagedResponse.of(users, users.getContent());
        return ResponseEntity.ok(ApiResponse.success(response, "Users list retrieved successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user details by ID")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long id) {
        UserResponse user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success(user, "User details retrieved successfully"));
    }

    @PutMapping("/{id}/enable")
    @Operation(summary = "Administrative account activation")
    public ResponseEntity<ApiResponse<Void>> enableUser(@PathVariable Long id) {
        userService.enableUser(id);
        return ResponseEntity.ok(ApiResponse.success("User account enabled successfully"));
    }

    @PutMapping("/{id}/disable")
    @Operation(summary = "Administrative account deactivation")
    public ResponseEntity<ApiResponse<Void>> disableUser(@PathVariable Long id) {
        userService.disableUser(id);
        return ResponseEntity.ok(ApiResponse.success("User account disabled successfully"));
    }

    @PutMapping("/{id}/unlock")
    @Operation(summary = "Unlock account locked due to failed authentication trials")
    public ResponseEntity<ApiResponse<Void>> unlockUser(@PathVariable Long id) {
        userService.unlockUser(id);
        return ResponseEntity.ok(ApiResponse.success("User account unlocked successfully"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user from platform")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success("User account deleted successfully"));
    }

    @PostMapping("/{userId}/roles/{roleId}")
    @Operation(summary = "Assign a role to a user")
    public ResponseEntity<ApiResponse<UserResponse>> assignRoleToUser(
            @PathVariable Long userId,
            @PathVariable Long roleId
    ) {
        UserResponse user = userService.assignRole(userId, roleId);
        return ResponseEntity.ok(ApiResponse.success(user, "Role assigned to user successfully"));
    }

    @DeleteMapping("/{userId}/roles/{roleId}")
    @Operation(summary = "Remove a role from a user")
    public ResponseEntity<ApiResponse<UserResponse>> removeRoleFromUser(
            @PathVariable Long userId,
            @PathVariable Long roleId
    ) {
        UserResponse user = userService.removeRole(userId, roleId);
        return ResponseEntity.ok(ApiResponse.success(user, "Role removed from user successfully"));
    }
}
