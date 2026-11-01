package com.rims.service;

import com.rims.dto.PageResponse;
import com.rims.dto.ProductDto;
import com.rims.entity.*;
import com.rims.exception.ApiException;
import com.rims.repository.CategoryRepository;
import com.rims.repository.ProductRepository;
import com.rims.repository.SupplierRepository;
import com.rims.security.CurrentUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final SupplierRepository suppliers;
    private final InventoryService inventory;
    private final LocationService locations;
    private final CurrentUser currentUser;

    public ProductService(ProductRepository products, CategoryRepository categories, SupplierRepository suppliers,
                          InventoryService inventory, LocationService locations, CurrentUser currentUser) {
        this.products = products;
        this.categories = categories;
        this.suppliers = suppliers;
        this.inventory = inventory;
        this.locations = locations;
        this.currentUser = currentUser;
    }

    /** Deleted (inactive) products are never listed. */
    public PageResponse<ProductDto.Response> list(String search, Long categoryId, boolean lowStockOnly,
                                                  int page, int size) {
        Specification<Product> spec = (r, q, cb) -> cb.isTrue(r.get("active"));
        if (search != null && !search.isBlank()) {
            String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((r, q, cb) -> cb.or(cb.like(cb.lower(r.get("name")), like),
                    cb.like(cb.lower(r.get("sku")), like),
                    cb.like(cb.lower(r.get("barcode")), like)));
        }
        if (categoryId != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("category").get("id"), categoryId));
        }
        if (lowStockOnly) {
            // business rule 9: each product is compared with ITS OWN reorder level
            spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.<Integer>get("quantity"), r.<Integer>get("reorderLevel")));
        }
        return PageResponse.of(products.findAll(spec,
                PageRequest.of(Math.max(page, 0), InventoryService.clamp(size), Sort.by("name").and(Sort.by("id")))),
                Mappers::product);
    }

    public ProductDto.Response get(Long id) {
        return Mappers.product(findActive(id));
    }

    /** What a barcode scanner (a keyboard that types a code and presses Enter) calls: barcode first, then SKU. */
    public ProductDto.Response lookup(String code) {
        String c = code == null ? "" : code.trim();
        return Mappers.product(products.findByBarcodeIgnoreCase(c)
                .or(() -> products.findBySkuIgnoreCase(c))
                .filter(Product::isActive)
                .orElseThrow(() -> ApiException.notFound("No product with barcode or SKU '" + c + "'")));
    }

    @Transactional
    public ProductDto.Response create(ProductDto.Request r) {
        return create(r, currentUser.get(), LocalDateTime.now());
    }

    @Transactional
    public ProductDto.Response create(ProductDto.Request r, User actor, LocalDateTime when) {
        String sku = normalizeSku(r.sku());
        if (products.existsBySkuIgnoreCase(sku)) {
            throw ApiException.conflict("SKU '" + sku + "' already exists. SKUs must be unique");
        }
        String barcode = CategoryService.blankToNull(r.barcode());
        if (barcode != null && products.existsByBarcodeIgnoreCase(barcode)) {
            throw ApiException.conflict("Barcode '" + barcode + "' already belongs to another product");
        }
        Product p = new Product();
        apply(p, r, sku);
        p.setQuantity(0);
        products.save(p);
        int opening = r.quantity() == null ? 0 : r.quantity();
        if (opening > 0) {
            // opening stock is placed at the default location; move it with a transfer if needed
            inventory.changeStock(p, locations.resolve(null), opening, TransactionType.INITIAL_STOCK, "PRODUCT",
                    p.getId(), "Opening stock", actor, when);
        }
        return Mappers.product(p);
    }

    @Transactional
    public ProductDto.Response update(Long id, ProductDto.Request r) {
        Product p = findActive(id);
        String sku = normalizeSku(r.sku());
        if (products.existsBySkuIgnoreCaseAndIdNot(sku, id)) {
            throw ApiException.conflict("SKU '" + sku + "' already exists. SKUs must be unique");
        }
        String barcode = CategoryService.blankToNull(r.barcode());
        if (barcode != null && products.existsByBarcodeIgnoreCaseAndIdNot(barcode, id)) {
            throw ApiException.conflict("Barcode '" + barcode + "' already belongs to another product");
        }
        apply(p, r, sku);   // quantity is deliberately NOT touched here
        return Mappers.product(p);
    }

    /**
     * Soft delete (business rule 8): the row stays so purchase orders, sales and the
     * transaction history that reference it remain intact. It disappears from every list
     * and cannot be bought or sold any more.
     */
    @Transactional
    public void delete(Long id) {
        Product p = findActive(id);
        p.setActive(false);
    }

    private Product findActive(Long id) {
        return products.findById(id).filter(Product::isActive)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
    }

    private void apply(Product p, ProductDto.Request r, String sku) {
        Category category = categories.findById(r.categoryId())
                .orElseThrow(() -> ApiException.badRequest("Selected category does not exist"));
        Supplier supplier = null;
        if (r.supplierId() != null) {
            supplier = suppliers.findById(r.supplierId())
                    .orElseThrow(() -> ApiException.badRequest("Selected supplier does not exist"));
        }
        p.setName(r.name().trim());
        p.setSku(sku);
        p.setBarcode(CategoryService.blankToNull(r.barcode()));
        p.setImageUrl(CategoryService.blankToNull(r.imageUrl()));
        p.setCategory(category);
        p.setSupplier(supplier);
        p.setPrice(r.price().setScale(2, java.math.RoundingMode.HALF_UP));
        p.setReorderLevel(r.reorderLevel());
    }

    private static String normalizeSku(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }
}
