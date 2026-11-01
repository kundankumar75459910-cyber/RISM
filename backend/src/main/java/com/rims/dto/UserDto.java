package com.rims.dto;

import com.rims.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class UserDto {
    private UserDto() {}

    /** password is mandatory on create, optional on update (blank = keep current). */
    public record Request(
            @NotBlank(message = "Username is required")
            @Size(min = 3, max = 50, message = "Username must be 3-50 characters")
            @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "Username may only contain letters, digits, dot, dash and underscore")
            String username,
            @Size(max = 100, message = "Password is too long") String password,
            @NotBlank(message = "Full name is required") @Size(max = 100) String fullName,
            @NotNull(message = "Role is required") Role role,
            Boolean active) {}

    public record Response(Long id, String username, String fullName, Role role, boolean active,
                           LocalDateTime createdAt) {}
}
