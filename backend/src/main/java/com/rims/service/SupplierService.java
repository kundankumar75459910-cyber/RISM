package com.rims.service;

import com.rims.dto.PageResponse;
import com.rims.dto.SupplierDto;
import com.rims.entity.Supplier;
import com.rims.exception.ApiException;
import com.rims.repository.ProductRepository;
import com.rims.repository.PurchaseOrderRepository;
import com.rims.repository.SupplierRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SupplierService {

    private final SupplierRepository suppliers;
    private final ProductRepository products;
    private final PurchaseOrderRepository purchases;

    public SupplierService(SupplierRepository suppliers, ProductRepository products, PurchaseOrderRepository purchases) {
        this.suppliers = suppliers;
        this.products = products;
        this.purchases = purchases;
    }

    public PageResponse<SupplierDto.Response> list(String search, int page, int size) {
        Specification<Supplier> spec = (r, q, cb) -> cb.conjunction();
        if (search != null && !search.isBlank()) {
            String like = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((r, q, cb) -> cb.or(cb.like(cb.lower(r.get("name")), like),
                    cb.like(cb.lower(r.get("email")), like)));
        }
        return PageResponse.of(suppliers.findAll(spec,
                PageRequest.of(Math.max(page, 0), InventoryService.clamp(size), Sort.by("name"))), Mappers::supplier);
    }

    public SupplierDto.Response get(Long id) {
        return Mappers.supplier(find(id));
    }

    @Transactional
    public SupplierDto.Response create(SupplierDto.Request r) {
        String name = r.name().trim();
        if (suppliers.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("A supplier named '" + name + "' already exists");
        }
        Supplier s = new Supplier();
        apply(s, r, name);
        return Mappers.supplier(suppliers.save(s));
    }

    @Transactional
    public SupplierDto.Response update(Long id, SupplierDto.Request r) {
        Supplier s = find(id);
        String name = r.name().trim();
        if (suppliers.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw ApiException.conflict("A supplier named '" + name + "' already exists");
        }
        apply(s, r, name);
        return Mappers.supplier(s);
    }

    /** A supplier that is referenced by purchase orders or products is kept, so history stays valid. */
    @Transactional
    public void delete(Long id) {
        Supplier s = find(id);
        if (purchases.existsBySupplierId(id)) {
            throw ApiException.conflict("Supplier '" + s.getName() + "' has purchase orders and cannot be deleted");
        }
        if (products.existsBySupplierId(id)) {
            throw ApiException.conflict("Supplier '" + s.getName() + "' is linked to products. Re-assign those products first");
        }
        suppliers.delete(s);
    }

    private Supplier find(Long id) {
        return suppliers.findById(id).orElseThrow(() -> ApiException.notFound("Supplier not found"));
    }

    private void apply(Supplier s, SupplierDto.Request r, String name) {
        s.setName(name);
        s.setContactNumber(CategoryService.blankToNull(r.contactNumber()));
        s.setEmail(CategoryService.blankToNull(r.email()));
        s.setAddress(CategoryService.blankToNull(r.address()));
    }
}
