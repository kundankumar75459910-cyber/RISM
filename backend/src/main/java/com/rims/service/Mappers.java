package com.rims.service;

import com.rims.dto.*;
import com.rims.entity.*;

import java.math.BigDecimal;

/** Entity -> DTO conversion. Must be called inside a transaction (lazy associations). */
public final class Mappers {
    private Mappers() {}

    public static UserDto.Response user(User u) {
        return new UserDto.Response(u.getId(), u.getUsername(), u.getFullName(), u.getRole(), u.isActive(), u.getCreatedAt());
    }

    public static SupplierDto.Response supplier(Supplier s) {
        return new SupplierDto.Response(s.getId(), s.getName(), s.getContactNumber(), s.getEmail(), s.getAddress());
    }

    public static ProductDto.Response product(Product p) {
        Supplier s = p.getSupplier();
        return new ProductDto.Response(p.getId(), p.getName(), p.getSku(),
                p.getCategory().getId(), p.getCategory().getName(),
                s == null ? null : s.getId(), s == null ? null : s.getName(),
                p.getPrice(), p.getQuantity(), p.getReorderLevel(), p.isLowStock(),
                p.getPrice().multiply(BigDecimal.valueOf(p.getQuantity())),
                p.getBarcode(), p.getImageUrl());
    }

    public static InventoryDto.TransactionResponse transaction(InventoryTransaction t) {
        Product p = t.getProduct();
        return new InventoryDto.TransactionResponse(t.getId(), p.getId(), p.getName(), p.getSku(),
                p.getCategory().getName(), t.getType(), t.getQuantityChange(), t.getQuantityAfter(),
                t.getReferenceType(), t.getReferenceId(), t.getNote(),
                t.getCreatedBy() == null ? null : t.getCreatedBy().getFullName(), t.getCreatedAt(),
                t.getLocation().getId(), t.getLocation().getName());
    }

    public static PurchaseDto.Response purchase(PurchaseOrder po) {
        return new PurchaseDto.Response(po.getId(), po.getPoNumber(), po.getSupplier().getId(),
                po.getSupplier().getName(), po.getStatus(), po.getOrderDate(), po.getReceivedDate(),
                po.getTotalAmount(), po.getNotes(),
                po.getCreatedBy() == null ? null : po.getCreatedBy().getFullName(),
                po.getItems().stream().map(i -> new PurchaseDto.ItemResponse(i.getProduct().getId(),
                        i.getProduct().getName(), i.getProduct().getSku(), i.getQuantity(),
                        i.getUnitCost(), i.getLineTotal())).toList(),
                po.getLocation().getId(), po.getLocation().getName());
    }

    public static SaleDto.Response sale(Sale s) {
        return new SaleDto.Response(s.getId(), s.getSaleNumber(), s.getSaleDate(), s.getCustomerName(),
                s.getTotalAmount(), s.getCreatedBy() == null ? null : s.getCreatedBy().getFullName(),
                s.getItems().stream().map(i -> new SaleDto.ItemResponse(i.getProduct().getId(),
                        i.getProduct().getName(), i.getProduct().getSku(), i.getQuantity(),
                        i.getUnitPrice(), i.getLineTotal())).toList(),
                s.getLocation().getId(), s.getLocation().getName());
    }
}
