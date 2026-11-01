package com.rims.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class SupplierDto {
    private SupplierDto() {}

    public record Request(
            @NotBlank(message = "Supplier name is required") @Size(max = 150) String name,
            @Pattern(regexp = "^$|^[0-9+()\\-\\s]{5,30}$", message = "Contact number may contain digits, spaces, + ( ) - only (5-30 chars)")
            String contactNumber,
            @Email(message = "Email address is not valid") @Size(max = 120) String email,
            @Size(max = 255) String address) {}

    public record Response(Long id, String name, String contactNumber, String email, String address) {}
}
