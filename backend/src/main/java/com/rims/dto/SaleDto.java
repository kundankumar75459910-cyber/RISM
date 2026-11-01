package com.rims.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class SaleDto {
    private SaleDto() {}

    /** Prices are never accepted from the client; the server uses the current product price. */
    public record ItemRequest(
            @NotNull(message = "Product is required for every line") Long productId,
            @NotNull(message = "Quantity is required") @Min(value = 1, message = "Quantity must be at least 1")
            Integer quantity) {}

    public record Request(@Size(max = 100) String customerName,
                          @NotEmpty(message = "Add at least one item") List<@Valid ItemRequest> items,
                          Long locationId) {}

    public record ItemResponse(Long productId, String productName, String sku, int quantity,
                               BigDecimal unitPrice, BigDecimal lineTotal) {}

    public record Response(Long id, String saleNumber, LocalDateTime saleDate, String customerName,
                           BigDecimal totalAmount, String createdBy, List<ItemResponse> items,
                           Long locationId, String locationName) {}
}
