package com.taskflow.userservice.dto;

import com.taskflow.userservice.model.Role;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * DTO for updating a user's role. Includes validation.
 */
@Data // Lombok for getters/setters/etc.
public class UserRoleUpdateRequest {

    @NotNull(message = "Role cannot be null")
    private Role newRole;
}