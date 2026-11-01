package com.rims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class CategoryDto {
    private CategoryDto() {}

    public record Request(@NotBlank(message = "Category name is required")
                          @Size(max = 100, message = "Name must be at most 100 characters") String name,
                          @Size(max = 255, message = "Description must be at most 255 characters") String description) {}

    public record Response(Long id, String name, String description, long productCount) {}
}
