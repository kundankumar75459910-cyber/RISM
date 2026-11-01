package com.rims.service;

import com.rims.dto.DashboardDto;
import com.rims.entity.Sale;
import com.rims.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Every figure comes from a database query; nothing is hard-coded. */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final int TREND_DAYS = 14;

    private final ProductRepository products;
    private final SupplierRepository suppliers;
    private final SaleRepository sales;
    private final InventoryTransactionRepository transactions;
    private final StockLevelRepository stockLevels;
    private final LocationRepository locations;

    public DashboardService(ProductRepository products, SupplierRepository suppliers, SaleRepository sales,
                            InventoryTransactionRepository transactions, StockLevelRepository stockLevels,
                            LocationRepository locations) {
        this.products = products;
        this.suppliers = suppliers;
        this.sales = sales;
        this.transactions = transactions;
        this.stockLevels = stockLevels;
        this.locations = locations;
    }

    public DashboardDto.Response load() {
        List<DashboardDto.CategoryStock> byCategory = products.stockByCategory().stream()
                .map(row -> new DashboardDto.CategoryStock((String) row[0], ((Number) row[1]).longValue()))
                .toList();

        // every open location appears, even with no stock
        Map<String, Long> unitsAt = new java.util.LinkedHashMap<>();
        locations.findAllByOrderByNameAsc().stream().filter(l -> l.isActive()).forEach(l -> unitsAt.put(l.getName(), 0L));
        stockLevels.unitsByLocation().forEach(row -> unitsAt.merge((String) row[0], ((Number) row[1]).longValue(), Long::sum));
        List<DashboardDto.LocationStock> byLocation = unitsAt.entrySet().stream()
                .map(e -> new DashboardDto.LocationStock(e.getKey(), e.getValue())).toList();

        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.minusDays(TREND_DAYS - 1L);
        Map<LocalDate, BigDecimal> amounts = new TreeMap<>();
        Map<LocalDate, Long> counts = new TreeMap<>();
        for (int i = 0; i < TREND_DAYS; i++) {          // zero-fill days without sales
            amounts.put(firstDay.plusDays(i), BigDecimal.ZERO);
            counts.put(firstDay.plusDays(i), 0L);
        }
        for (Sale s : sales.findBySaleDateGreaterThanEqual(firstDay.atStartOfDay())) {
            LocalDate d = s.getSaleDate().toLocalDate();
            if (amounts.containsKey(d)) {
                amounts.merge(d, s.getTotalAmount(), BigDecimal::add);
                counts.merge(d, 1L, Long::sum);
            }
        }
        List<DashboardDto.DailySales> trend = amounts.entrySet().stream()
                .map(e -> new DashboardDto.DailySales(e.getKey(), e.getValue(), counts.get(e.getKey()))).toList();

        return new DashboardDto.Response(
                products.countByActiveTrue(),
                products.sumActiveQuantity(),
                products.countLowStock(),
                suppliers.count(),
                sales.count(),
                sales.sumTotalAmount(),
                products.sumInventoryValue(),
                byCategory,
                trend,
                transactions.findTop10ByOrderByCreatedAtDescIdDesc().stream().map(Mappers::transaction).toList(),
                products.findLowStock().stream().limit(6).map(Mappers::product).toList(),
                byLocation);
    }
}
