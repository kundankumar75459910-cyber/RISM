package com.rims.dto;

import com.rims.entity.PurchaseStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class PurchaseDto {
    private PurchaseDto() {}

    public record ItemRequest(
            @NotNull(message = "Product is required for every line") Long productId,
            @NotNull(message = "Quantity is required") @Min(value = 1, message = "Quantity must be at least 1")
            Integer quantity,
            @NotNull(message = "Unit cost is required")
            @DecimalMin(value = "0.00", message = "Unit cost cannot be negative")
            @DecimalMax(value = "9999999.99", message = "Unit cost is too large")
            @Digits(integer = 7, fraction = 2, message = "Unit cost may have at most 2 decimal places") BigDecimal unitCost) {}

    public record Request(
            @NotNull(message = "Supplier is required") Long supplierId,
            @Size(max = 255) String notes,
            @NotEmpty(message = "Add at least one item") List<@Valid ItemRequest> items,
            Long locationId) {}

    public record ItemResponse(Long productId, String productName, String sku, int quantity,
                               BigDecimal unitCost, BigDecimal lineTotal) {}

    public record Response(Long id, String poNumber, Long supplierId, String supplierName,
                           PurchaseStatus status, LocalDateTime orderDate, LocalDateTime receivedDate,
                           BigDecimal totalAmount, String notes, String createdBy,
                           List<ItemResponse> items, Long locationId, String locationName) {}
}
