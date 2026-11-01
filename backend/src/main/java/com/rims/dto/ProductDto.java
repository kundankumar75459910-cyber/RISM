package com.rims.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class ProductDto {
    private ProductDto() {}

    /**
     * quantity is the OPENING stock and is only used when a product is created.
     * Afterwards stock changes only through purchases, sales and stock adjustments,
     * so that every change is recorded in the inventory transaction history.
     */
    public record Request(
            @NotBlank(message = "Product name is required") @Size(max = 150) String name,
            @NotBlank(message = "SKU is required") @Size(max = 50) String sku,
            @NotNull(message = "Category is required") Long categoryId,
            Long supplierId,
            @NotNull(message = "Price is required")
            @DecimalMin(value = "0.00", message = "Price cannot be negative")
            @DecimalMax(value = "9999999.99", message = "Price is too large")
            @Digits(integer = 7, fraction = 2, message = "Price may have at most 2 decimal places") BigDecimal price,
            @Min(value = 0, message = "Quantity cannot be negative") Integer quantity,
            @NotNull(message = "Reorder level is required")
            @Min(value = 0, message = "Reorder level cannot be negative") Integer reorderLevel,
            @Size(max = 64, message = "Barcode must be at most 64 characters") String barcode,
            @Size(max = 500, message = "Image link must be at most 500 characters")
            @Pattern(regexp = "^(https?://\\S+)?$", message = "Image link must be an http(s) URL") String imageUrl) {}

    public record Response(Long id, String name, String sku, Long categoryId, String categoryName,
                           Long supplierId, String supplierName, BigDecimal price, int quantity,
                           int reorderLevel, boolean lowStock, BigDecimal stockValue,
                           String barcode, String imageUrl) {}
}
