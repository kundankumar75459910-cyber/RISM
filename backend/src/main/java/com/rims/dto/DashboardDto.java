package com.rims.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class DashboardDto {
    private DashboardDto() {}

    public record CategoryStock(String category, long quantity) {}

    public record LocationStock(String location, long quantity) {}
    public record DailySales(LocalDate date, BigDecimal amount, long count) {}

    public record Response(long totalProducts, long totalQuantity, long lowStockCount, long totalSuppliers,
                           long totalSalesCount, BigDecimal totalSalesAmount, BigDecimal inventoryValue,
                           List<CategoryStock> stockByCategory, List<DailySales> salesTrend,
                           List<InventoryDto.TransactionResponse> recentTransactions,
                           List<ProductDto.Response> lowStockProducts,
                           List<LocationStock> stockByLocation) {}
}
