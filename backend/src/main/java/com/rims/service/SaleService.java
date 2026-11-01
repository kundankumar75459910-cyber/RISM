package com.rims.service;

import com.rims.dto.PageResponse;
import com.rims.dto.SaleDto;
import com.rims.entity.*;
import com.rims.exception.ApiException;
import com.rims.repository.ProductRepository;
import com.rims.repository.SaleRepository;
import com.rims.security.CurrentUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SaleService {

    private final SaleRepository sales;
    private final ProductRepository products;
    private final InventoryService inventory;
    private final LocationService locations;
    private final CurrentUser currentUser;

    public SaleService(SaleRepository sales, ProductRepository products, InventoryService inventory,
                       LocationService locations, CurrentUser currentUser) {
        this.sales = sales;
        this.products = products;
        this.inventory = inventory;
        this.locations = locations;
        this.currentUser = currentUser;
    }

    public PageResponse<SaleDto.Response> list(LocalDate from, LocalDate to, int page, int size) {
        Specification<Sale> spec = (r, q, cb) -> cb.conjunction();
        LocalDateTime start = Dates.start(from);
        LocalDateTime end = Dates.endExclusive(to);
        if (start != null) spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.<LocalDateTime>get("saleDate"), start));
        if (end != null) spec = spec.and((r, q, cb) -> cb.lessThan(r.<LocalDateTime>get("saleDate"), end));
        return PageResponse.of(sales.findAll(spec, PageRequest.of(Math.max(page, 0), InventoryService.clamp(size),
                Sort.by(Sort.Direction.DESC, "saleDate").and(Sort.by(Sort.Direction.DESC, "id")))), Mappers::sale);
    }

    public SaleDto.Response get(Long id) {
        return Mappers.sale(sales.findById(id).orElseThrow(() -> ApiException.notFound("Sale not found")));
    }

    @Transactional
    public SaleDto.Response create(SaleDto.Request req) {
        return create(req, currentUser.get(), LocalDateTime.now());
    }

    /**
     * One transaction: validate -> lock products -> check stock -> save sale -> reduce stock + log.
     * Any exception (e.g. insufficient stock on the 3rd line) rolls back everything,
     * so a failed sale never leaves partial changes (business rules 3 and 7).
     * The total is computed on the server from current product prices with BigDecimal (rule 10).
     */
    @Transactional
    public SaleDto.Response create(SaleDto.Request req, User actor, LocalDateTime when) {
        // merge repeated products; TreeMap => ascending product id => consistent lock order (no deadlocks)
        Map<Long, Integer> wanted = new TreeMap<>();
        for (SaleDto.ItemRequest line : req.items()) {
            wanted.merge(line.productId(), line.quantity(), Integer::sum);
        }

        Location location = locations.resolve(req.locationId());   // sold from this location

        Sale sale = new Sale();
        sale.setSaleNumber("TMP-" + UUID.randomUUID());
        sale.setSaleDate(when);
        sale.setLocation(location);
        sale.setCustomerName(CategoryService.blankToNull(req.customerName()));
        sale.setCreatedBy(actor);

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> e : wanted.entrySet()) {
            Product product = products.findByIdForUpdate(e.getKey())
                    .orElseThrow(() -> ApiException.badRequest("Product " + e.getKey() + " does not exist"));
            if (!product.isActive()) {
                throw ApiException.badRequest("Product " + product.getName() + " is no longer available for sale");
            }
            int available = inventory.available(product.getId(), location.getId());
            if (available < e.getValue()) {
                throw ApiException.conflict(String.format(
                        "Insufficient stock for %s (SKU %s) at %s: available %d, requested %d",
                        product.getName(), product.getSku(), location.getName(), available, e.getValue()));
            }
            SaleItem item = new SaleItem();
            item.setProduct(product);
            item.setQuantity(e.getValue());
            item.setUnitPrice(product.getPrice());
            item.setLineTotal(product.getPrice().multiply(BigDecimal.valueOf(e.getValue())).setScale(2, RoundingMode.HALF_UP));
            sale.addItem(item);
            total = total.add(item.getLineTotal());
        }
        sale.setTotalAmount(total);
        sales.saveAndFlush(sale);
        sale.setSaleNumber(String.format("SL-%06d", sale.getId()));

        for (SaleItem item : sale.getItems()) {
            inventory.changeStock(item.getProduct(), location, -item.getQuantity(), TransactionType.SALE, "SALE",
                    sale.getId(), "Sale " + sale.getSaleNumber(), actor, when);
        }
        return Mappers.sale(sale);
    }
}
