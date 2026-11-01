package com.rims.service;

import com.rims.dto.InventoryDto;
import com.rims.dto.PageResponse;
import com.rims.dto.ProductDto;
import com.rims.entity.*;
import com.rims.exception.ApiException;
import com.rims.repository.InventoryTransactionRepository;
import com.rims.repository.ProductRepository;
import com.rims.repository.StockLevelRepository;
import com.rims.security.CurrentUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The ONLY place that changes stock (Product.quantity and StockLevel.quantity). Every change:
 *   - is rejected if it would make stock negative at that location (business rules 2 and 3),
 *   - keeps Product.quantity equal to the sum of its per-location stock,
 *   - writes an inventory transaction row (rule 6),
 *   - runs inside the caller's transaction, so a later failure rolls everything back (rule 7).
 */
@Service
@Transactional(readOnly = true)
public class InventoryService {

    private final ProductRepository products;
    private final InventoryTransactionRepository transactions;
    private final StockLevelRepository stockLevels;
    private final LocationService locations;
    private final CurrentUser currentUser;

    public InventoryService(ProductRepository products, InventoryTransactionRepository transactions,
                            StockLevelRepository stockLevels, LocationService locations, CurrentUser currentUser) {
        this.products = products;
        this.transactions = transactions;
        this.stockLevels = stockLevels;
        this.locations = locations;
        this.currentUser = currentUser;
    }

    /**
     * Caller must already hold a row lock on the product (findByIdForUpdate) or have just created it.
     * That lock also serialises the creation of the first stock row for a product at a location.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryTransaction changeStock(Product product, Location location, int delta, TransactionType type,
                                            String referenceType, Long referenceId, String note, User actor,
                                            LocalDateTime when) {
        if (delta == 0) {
            throw ApiException.badRequest("Quantity change must not be zero");
        }
        StockLevel level = stockLevels.findForUpdate(product.getId(), location.getId()).orElse(null);
        int atLocation = level == null ? 0 : level.getQuantity();
        int newAtLocation = atLocation + delta;
        if (newAtLocation < 0) {
            throw ApiException.conflict(String.format(
                    "Insufficient stock for %s (SKU %s) at %s: available %d, requested %d",
                    product.getName(), product.getSku(), location.getName(), atLocation, -delta));
        }
        int newTotal = product.getQuantity() + delta;
        if (newTotal < 0) {
            throw ApiException.conflict(String.format(
                    "Insufficient stock for %s (SKU %s): available %d, requested %d",
                    product.getName(), product.getSku(), product.getQuantity(), -delta));
        }
        if (level == null) {
            level = new StockLevel();
            level.setProduct(product);
            level.setLocation(location);
        }
        level.setQuantity(newAtLocation);
        stockLevels.save(level);
        product.setQuantity(newTotal);
        products.save(product);

        InventoryTransaction t = new InventoryTransaction();
        t.setProduct(product);
        t.setLocation(location);
        t.setType(type);
        t.setQuantityChange(delta);
        t.setQuantityAfter(newAtLocation);
        t.setReferenceType(referenceType);
        t.setReferenceId(referenceId);
        t.setNote(note);
        t.setCreatedBy(actor);
        t.setCreatedAt(when);
        return transactions.save(t);
    }

    /** Units of a product currently held at one location (0 when it has none there). */
    public int available(Long productId, Long locationId) {
        return stockLevels.findLevel(productId, locationId).map(StockLevel::getQuantity).orElse(0);
    }

    @Transactional
    public InventoryDto.TransactionResponse adjust(InventoryDto.AdjustmentRequest req) {
        Location location = locations.resolve(req.locationId());
        Product product = products.findByIdForUpdate(req.productId())
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        if (!product.isActive()) {
            throw ApiException.badRequest("Product " + product.getName() + " has been deleted");
        }
        boolean in = req.direction() == InventoryDto.Direction.IN;
        InventoryTransaction t = changeStock(product, location, in ? req.quantity() : -req.quantity(),
                in ? TransactionType.ADJUSTMENT_IN : TransactionType.ADJUSTMENT_OUT,
                "ADJUSTMENT", null, req.reason().trim(), currentUser.get(), LocalDateTime.now());
        return Mappers.transaction(t);
    }

    /**
     * Moves stock from one location to another. The product's total quantity is unchanged;
     * two transactions (out of the source, into the destination) record the movement.
     * If the source does not hold enough, nothing changes.
     */
    @Transactional
    public List<InventoryDto.TransactionResponse> transfer(InventoryDto.TransferRequest req) {
        return transfer(req, currentUser.get(), LocalDateTime.now());
    }

    /** Same as above with an explicit actor and time (used by the sample-data loader). */
    @Transactional
    public List<InventoryDto.TransactionResponse> transfer(InventoryDto.TransferRequest req, User actor,
                                                           LocalDateTime when) {
        if (req.fromLocationId().equals(req.toLocationId())) {
            throw ApiException.badRequest("Source and destination must be different locations");
        }
        Location from = locations.resolve(req.fromLocationId());
        Location to = locations.resolve(req.toLocationId());
        Product product = products.findByIdForUpdate(req.productId())
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        if (!product.isActive()) {
            throw ApiException.badRequest("Product " + product.getName() + " has been deleted");
        }
        String text = req.note() == null || req.note().isBlank()
                ? "Transfer from " + from.getName() + " to " + to.getName()
                : req.note().trim();
        InventoryTransaction out = changeStock(product, from, -req.quantity(), TransactionType.TRANSFER_OUT,
                "TRANSFER", null, text, actor, when);
        InventoryTransaction in = changeStock(product, to, req.quantity(), TransactionType.TRANSFER_IN,
                "TRANSFER", null, text, actor, when);
        return List.of(Mappers.transaction(out), Mappers.transaction(in));
    }

    /** Where a product's stock is held (locations holding none are left out). */
    public List<InventoryDto.StockLevelResponse> stockByLocation(Long productId) {
        products.findById(productId).orElseThrow(() -> ApiException.notFound("Product not found"));
        return stockLevels.findByProductId(productId).stream()
                .filter(s -> s.getQuantity() > 0)
                .map(s -> new InventoryDto.StockLevelResponse(s.getLocation().getId(), s.getLocation().getName(),
                        s.getLocation().isActive(), s.getQuantity()))
                .toList();
    }

    /** Which products a location holds, and how many of each. */
    public List<InventoryDto.ProductStockResponse> stockAtLocation(Long locationId) {
        locations.resolveAny(locationId);
        return stockLevels.findInStockAt(locationId).stream()
                .map(s -> new InventoryDto.ProductStockResponse(s.getProduct().getId(), s.getQuantity()))
                .toList();
    }

    public List<ProductDto.Response> lowStock() {
        return products.findLowStock().stream().map(Mappers::product).toList();
    }

    public PageResponse<InventoryDto.TransactionResponse> transactions(LocalDate from, LocalDate to, Long categoryId,
                                                                       Long productId, Long locationId,
                                                                       TransactionType type, int page, int size) {
        Specification<InventoryTransaction> spec = (r, q, cb) -> cb.conjunction();
        LocalDateTime start = Dates.start(from);
        LocalDateTime end = Dates.endExclusive(to);
        if (start != null) spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.<LocalDateTime>get("createdAt"), start));
        if (end != null) spec = spec.and((r, q, cb) -> cb.lessThan(r.<LocalDateTime>get("createdAt"), end));
        if (categoryId != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("product").get("category").get("id"), categoryId));
        if (productId != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("product").get("id"), productId));
        if (locationId != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("location").get("id"), locationId));
        if (type != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("type"), type));
        Pageable pageable = PageRequest.of(Math.max(page, 0), clamp(size),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.of(transactions.findAll(spec, pageable), Mappers::transaction);
    }

    static int clamp(int size) {
        return Math.min(Math.max(size, 1), 1000);
    }
}
