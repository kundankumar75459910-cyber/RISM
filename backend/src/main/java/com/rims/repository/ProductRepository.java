package com.rims.repository;

import com.rims.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    boolean existsBySkuIgnoreCase(String sku);
    boolean existsBySkuIgnoreCaseAndIdNot(String sku, Long id);
    boolean existsByBarcodeIgnoreCase(String barcode);
    boolean existsByBarcodeIgnoreCaseAndIdNot(String barcode, Long id);
    Optional<Product> findByBarcodeIgnoreCase(String barcode);
    Optional<Product> findBySkuIgnoreCase(String sku);
    boolean existsByCategoryId(Long categoryId);
    boolean existsBySupplierId(Long supplierId);
    long countByCategoryId(Long categoryId);

    /** Row lock: concurrent sales/receipts of the same product are serialised. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    long countByActiveTrue();

    @Query("select coalesce(sum(p.quantity), 0) from Product p where p.active = true")
    long sumActiveQuantity();

    @Query("select count(p) from Product p where p.active = true and p.quantity <= p.reorderLevel")
    long countLowStock();

    @Query("select coalesce(sum(p.price * p.quantity), 0) from Product p where p.active = true")
    BigDecimal sumInventoryValue();

    @Query("select c.name, coalesce(sum(p.quantity), 0) from Product p join p.category c " +
           "where p.active = true group by c.name order by c.name")
    List<Object[]> stockByCategory();

    @Query("select p from Product p where p.active = true and p.quantity <= p.reorderLevel " +
           "order by (p.quantity - p.reorderLevel) asc, p.name asc")
    List<Product> findLowStock();
}
