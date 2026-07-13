package com.iam.service;

import com.iam.dto.request.CreatePermissionRequest;
import com.iam.dto.response.PermissionResponse;
import com.iam.entity.Permission;
import com.iam.exception.DuplicateResourceException;
import com.iam.exception.ResourceNotFoundException;
import com.iam.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionService {

    private final PermissionRepository permissionRepository;

    @Transactional
    public PermissionResponse createPermission(CreatePermissionRequest request) {
        if (permissionRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException("Permission name already exists: " + request.getName());
        }
        if (permissionRepository.existsByResourceAndAction(request.getResource(), request.getAction())) {
            throw new DuplicateResourceException("Permission for resource '" + request.getResource() + 
                    "' and action '" + request.getAction() + "' already exists");
        }

        Permission permission = Permission.builder()
                .name(request.getName())
                .resource(request.getResource())
                .action(request.getAction())
                .description(request.getDescription())
                .build();

        Permission savedPermission = permissionRepository.save(permission);
        log.info("Created new permission: {}", savedPermission.getName());
        return toResponse(savedPermission);
    }

    public List<PermissionResponse> getAllPermissions() {
        return permissionRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public PermissionResponse getPermissionById(Long id) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "id", id));
        return toResponse(permission);
    }

    public List<PermissionResponse> getPermissionsByResource(String resource) {
        return permissionRepository.findByResource(resource).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public PermissionResponse updatePermission(Long id, CreatePermissionRequest request) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "id", id));

        // Check duplicates if name is changing
        if (!permission.getName().equals(request.getName()) && permissionRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException("Permission name already exists: " + request.getName());
        }
        // Check duplicates if resource/action are changing
        if ((!permission.getResource().equals(request.getResource()) || !permission.getAction().equals(request.getAction()))
                && permissionRepository.existsByResourceAndAction(request.getResource(), request.getAction())) {
            throw new DuplicateResourceException("Permission for resource '" + request.getResource() + 
                    "' and action '" + request.getAction() + "' already exists");
        }

        permission.setName(request.getName());
        permission.setResource(request.getResource());
        permission.setAction(request.getAction());
        permission.setDescription(request.getDescription());

        Permission updatedPermission = permissionRepository.save(permission);
        log.info("Updated permission: {}", updatedPermission.getName());
        return toResponse(updatedPermission);
    }

    @Transactional
    public void deletePermission(Long id) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "id", id));
        
        // In real-world, we should check if roles are using this permission.
        // Spring Data JPA's ManyToMany mapping will handle the join table deletion automatically 
        // due to cascading/join table definition.
        permissionRepository.delete(permission);
        log.info("Deleted permission: {}", permission.getName());
    }

    public PermissionResponse toResponse(Permission permission) {
        if (permission == null) {
            return null;
        }
        return PermissionResponse.builder()
                .id(permission.getId())
                .name(permission.getName())
                .resource(permission.getResource())
                .action(permission.getAction())
                .description(permission.getDescription())
                .createdAt(permission.getCreatedAt())
                .build();
    }
}
