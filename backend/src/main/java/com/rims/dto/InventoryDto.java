package com.rims.dto;

import com.rims.entity.TransactionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class InventoryDto {
    private InventoryDto() {}

    public enum Direction { IN, OUT }

    public record AdjustmentRequest(
            @NotNull(message = "Product is required") Long productId,
            @NotNull(message = "Direction is required") Direction direction,
            @NotNull(message = "Quantity is required")
            @Min(value = 1, message = "Quantity must be at least 1")
            @Max(value = 1000000, message = "Quantity is too large") Integer quantity,
            @NotBlank(message = "A reason is required") @Size(max = 200) String reason,
            Long locationId) {}

    /** Moves stock between two locations. Total stock of the product does not change. */
    public record TransferRequest(
            @NotNull(message = "Product is required") Long productId,
            @NotNull(message = "Source location is required") Long fromLocationId,
            @NotNull(message = "Destination location is required") Long toLocationId,
            @NotNull(message = "Quantity is required")
            @Min(value = 1, message = "Quantity must be at least 1")
            @Max(value = 1000000, message = "Quantity is too large") Integer quantity,
            @Size(max = 200) String note) {}

    /** Units of one product held at a given location. */
    public record ProductStockResponse(Long productId, int quantity) {}

    public record StockLevelResponse(Long locationId, String locationName, boolean locationActive, int quantity) {}

    public record TransactionResponse(Long id, Long productId, String productName, String sku,
                                      String categoryName, TransactionType type, int quantityChange,
                                      int quantityAfter, String referenceType, Long referenceId,
                                      String note, String createdBy, LocalDateTime createdAt,
                                      Long locationId, String locationName) {}
}
