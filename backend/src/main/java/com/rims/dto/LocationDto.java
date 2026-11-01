package com.rims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class LocationDto {
    private LocationDto() {}

    /** `active` is optional: a new location is open unless it says otherwise. */
    public record Request(@NotBlank(message = "Location name is required")
                          @Size(max = 100, message = "Name must be at most 100 characters") String name,
                          @Size(max = 255, message = "Address must be at most 255 characters") String address,
                          Boolean active) {}

    public record Response(Long id, String name, String address, boolean active, long totalUnits) {}
}
