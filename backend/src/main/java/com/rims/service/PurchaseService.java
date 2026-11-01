package com.rims.service;

import com.rims.dto.PageResponse;
import com.rims.dto.PurchaseDto;
import com.rims.entity.*;
import com.rims.exception.ApiException;
import com.rims.repository.ProductRepository;
import com.rims.repository.PurchaseOrderRepository;
import com.rims.repository.SupplierRepository;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PurchaseService {

    private final PurchaseOrderRepository purchases;
    private final SupplierRepository suppliers;
    private final ProductRepository products;
    private final InventoryService inventory;
    private final LocationService locations;
    private final CurrentUser currentUser;

    public PurchaseService(PurchaseOrderRepository purchases, SupplierRepository suppliers, ProductRepository products,
                           InventoryService inventory, LocationService locations, CurrentUser currentUser) {
        this.purchases = purchases;
        this.suppliers = suppliers;
        this.products = products;
        this.inventory = inventory;
        this.locations = locations;
        this.currentUser = currentUser;
    }

    public PageResponse<PurchaseDto.Response> list(PurchaseStatus status, Long supplierId, LocalDate from, LocalDate to,
                                                   int page, int size) {
        Specification<PurchaseOrder> spec = (r, q, cb) -> cb.conjunction();
        LocalDateTime start = Dates.start(from);
        LocalDateTime end = Dates.endExclusive(to);
        if (status != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        if (supplierId != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("supplier").get("id"), supplierId));
        if (start != null) spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.<LocalDateTime>get("orderDate"), start));
        if (end != null) spec = spec.and((r, q, cb) -> cb.lessThan(r.<LocalDateTime>get("orderDate"), end));
        return PageResponse.of(purchases.findAll(spec, PageRequest.of(Math.max(page, 0), InventoryService.clamp(size),
                Sort.by(Sort.Direction.DESC, "orderDate").and(Sort.by(Sort.Direction.DESC, "id")))), Mappers::purchase);
    }

    public PurchaseDto.Response get(Long id) {
        return Mappers.purchase(purchases.findById(id).orElseThrow(() -> ApiException.notFound("Purchase order not found")));
    }

    @Transactional
    public PurchaseDto.Response create(PurchaseDto.Request req) {
        return create(req, currentUser.get(), LocalDateTime.now());
    }

    /**
     * Creating a purchase order records the intent to buy. It does NOT touch stock
     * (stock only moves when the order is received).
     */
    @Transactional
    public PurchaseDto.Response create(PurchaseDto.Request req, User actor, LocalDateTime orderDate) {
        Supplier supplier = suppliers.findById(req.supplierId())
                .orElseThrow(() -> ApiException.badRequest("Selected supplier does not exist"));

        PurchaseOrder po = new PurchaseOrder();
        po.setPoNumber("TMP-" + UUID.randomUUID());
        po.setSupplier(supplier);
        po.setLocation(locations.resolve(req.locationId()));   // where the goods will be delivered
        po.setStatus(PurchaseStatus.PENDING);
        po.setOrderDate(orderDate);
        po.setNotes(CategoryService.blankToNull(req.notes()));
        po.setCreatedBy(actor);

        Set<Long> seen = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseDto.ItemRequest line : req.items()) {
            if (!seen.add(line.productId())) {
                throw ApiException.badRequest("The same product appears on more than one line. Combine the quantities");
            }
            Product product = products.findById(line.productId())
                    .orElseThrow(() -> ApiException.badRequest("Product " + line.productId() + " does not exist"));
            if (!product.isActive()) {
                throw ApiException.badRequest("Product " + product.getName() + " has been deleted and cannot be ordered");
            }
            BigDecimal unitCost = line.unitCost().setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = unitCost.multiply(BigDecimal.valueOf(line.quantity())).setScale(2, RoundingMode.HALF_UP);
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setProduct(product);
            item.setQuantity(line.quantity());
            item.setUnitCost(unitCost);
            item.setLineTotal(lineTotal);
            po.addItem(item);
            total = total.add(lineTotal);
        }
        po.setTotalAmount(total);
        purchases.saveAndFlush(po);                              // generates the id
        po.setPoNumber(String.format("PO-%06d", po.getId()));   // human readable number from the id
        return Mappers.purchase(po);
    }

    @Transactional
    public PurchaseDto.Response receive(Long id) {
        return receive(id, currentUser.get(), LocalDateTime.now());
    }

    /**
     * Business rule 4: stock increases exactly once. The PO row is locked and its status is
     * checked inside the same transaction, so a second (or simultaneous) "receive" is rejected.
     */
    @Transactional
    public PurchaseDto.Response receive(Long id, User actor, LocalDateTime when) {
        PurchaseOrder po = purchases.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Purchase order not found"));
        if (po.getStatus() != PurchaseStatus.PENDING) {
            throw ApiException.conflict("Purchase order " + po.getPoNumber() + " is already "
                    + po.getStatus().name().toLowerCase() + " and cannot be received");
        }
        if (!po.getLocation().isActive()) {
            throw ApiException.conflict("Location '" + po.getLocation().getName()
                    + "' is closed. Cancel this order or reopen the location first");
        }
        // lock products in id order so concurrent operations cannot deadlock
        for (PurchaseOrderItem item : po.getItems().stream()
                .sorted(Comparator.comparing(i -> i.getProduct().getId())).toList()) {
            Product product = products.findByIdForUpdate(item.getProduct().getId())
                    .orElseThrow(() -> ApiException.notFound("Product not found"));
            inventory.changeStock(product, po.getLocation(), item.getQuantity(), TransactionType.PURCHASE_RECEIVED,
                    "PURCHASE_ORDER", po.getId(), "Received " + po.getPoNumber(), actor, when);
        }
        po.setStatus(PurchaseStatus.RECEIVED);
        po.setReceivedDate(when);
        return Mappers.purchase(po);
    }

    /** Business rule 5: only a pending order can be cancelled, and cancelling never touches stock. */
    @Transactional
    public PurchaseDto.Response cancel(Long id) {
        PurchaseOrder po = purchases.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Purchase order not found"));
        if (po.getStatus() != PurchaseStatus.PENDING) {
            throw ApiException.conflict("Only pending purchase orders can be cancelled. "
                    + po.getPoNumber() + " is " + po.getStatus().name().toLowerCase());
        }
        po.setStatus(PurchaseStatus.CANCELLED);
        return Mappers.purchase(po);
    }
}
