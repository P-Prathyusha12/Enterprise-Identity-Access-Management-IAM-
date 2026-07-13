package com.iam.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePermissionRequest {

    @NotBlank(message = "Permission name is required")
    @Pattern(regexp = "^[a-z]+:[a-z]+$", message = "Permission name must follow format 'resource:action' (e.g. user:read)")
    private String name;

    @NotBlank(message = "Resource name is required")
    private String resource;

    @NotBlank(message = "Action is required")
    private String action;

    private String description;
}
