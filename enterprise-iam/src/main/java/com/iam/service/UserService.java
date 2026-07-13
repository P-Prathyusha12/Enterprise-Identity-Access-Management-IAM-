package com.iam.service;

import com.iam.dto.request.UpdateProfileRequest;
import com.iam.dto.response.PermissionResponse;
import com.iam.dto.response.RoleResponse;
import com.iam.dto.response.UserResponse;
import com.iam.entity.Permission;
import com.iam.entity.Role;
import com.iam.entity.User;
import com.iam.exception.BadRequestException;
import com.iam.exception.ResourceNotFoundException;
import com.iam.repository.RoleRepository;
import com.iam.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public UserResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        return toResponse(user);
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPhone(request.getPhone());

        User updatedUser = userRepository.save(user);
        log.info("Profile updated for user: {}", user.getEmail());
        return toResponse(updatedUser);
    }

    public Page<UserResponse> getAllUsers(String search, Boolean enabled, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }

            if (search != null && !search.isBlank()) {
                String likePattern = "%" + search.toLowerCase() + "%";
                Predicate usernameLike = cb.like(cb.lower(root.get("username")), likePattern);
                Predicate emailLike = cb.like(cb.lower(root.get("email")), likePattern);
                Predicate firstNameLike = cb.like(cb.lower(root.get("firstName")), likePattern);
                Predicate lastNameLike = cb.like(cb.lower(root.get("lastName")), likePattern);
                
                predicates.add(cb.or(usernameLike, emailLike, firstNameLike, lastNameLike));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return userRepository.findAll(spec, pageable).map(this::toResponse);
    }

    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return toResponse(user);
    }

    @Transactional
    public void enableUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        user.setEnabled(true);
        userRepository.save(user);
        log.info("User enabled: {}", user.getEmail());
    }

    @Transactional
    public void disableUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        
        // Prevent disabling yourself if you are an admin (usually checked in controller, but good safeguard here)
        user.setEnabled(false);
        userRepository.save(user);
        log.info("User disabled: {}", user.getEmail());
    }

    @Transactional
    public void unlockUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        user.setAccountLocked(false);
        user.setFailedLoginAttempts(0);
        user.setLockTime(null);
        userRepository.save(user);
        log.info("User unlocked: {}", user.getEmail());
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        userRepository.delete(user);
        log.info("User deleted: {}", user.getEmail());
    }

    @Transactional
    public UserResponse assignRole(Long userId, Long roleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", roleId));

        user.addRole(role);
        User updatedUser = userRepository.save(user);
        log.info("Role {} assigned to user {}", role.getName(), user.getEmail());
        return toResponse(updatedUser);
    }

    @Transactional
    public UserResponse removeRole(Long userId, Long roleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", roleId));

        if (user.getRoles().size() <= 1 && user.getRoles().contains(role)) {
            throw new BadRequestException("User must have at least one role");
        }

        user.removeRole(role);
        User updatedUser = userRepository.save(user);
        log.info("Role {} removed from user {}", role.getName(), user.getEmail());
        return toResponse(updatedUser);
    }

    public UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .enabled(user.isEnabled())
                .emailVerified(user.isEmailVerified())
                .accountLocked(user.isAccountLocked())
                .mfaEnabled(user.isMfaEnabled())
                .profilePictureUrl(user.getProfilePictureUrl())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .roles(user.getRoles().stream()
                        .map(this::toRoleResponse)
                        .collect(Collectors.toSet()))
                .build();
    }

    private RoleResponse toRoleResponse(Role role) {
        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .createdAt(role.getCreatedAt())
                .permissions(role.getPermissions().stream()
                        .map(this::toPermissionResponse)
                        .collect(Collectors.toSet()))
                .build();
    }

    private PermissionResponse toPermissionResponse(Permission permission) {
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
