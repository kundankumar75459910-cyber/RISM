package com.rims.repository;

import com.rims.entity.StockLevel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockLevelRepository extends JpaRepository<StockLevel, Long> {

    /** Row lock: concurrent changes to the same product at the same location are serialised. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockLevel s where s.product.id = :productId and s.location.id = :locationId")
    Optional<StockLevel> findForUpdate(@Param("productId") Long productId, @Param("locationId") Long locationId);

    @Query("select s from StockLevel s where s.product.id = :productId and s.location.id = :locationId")
    Optional<StockLevel> findLevel(@Param("productId") Long productId, @Param("locationId") Long locationId);

    @Query("select s from StockLevel s join fetch s.location l where s.product.id = :productId order by l.name")
    List<StockLevel> findByProductId(@Param("productId") Long productId);

    /** Active products that have stock at the location (used to check availability when selling there). */
    @Query("select s from StockLevel s join fetch s.product p where s.location.id = :locationId " +
           "and s.quantity > 0 and p.active = true")
    List<StockLevel> findInStockAt(@Param("locationId") Long locationId);

    @Query("select coalesce(sum(s.quantity), 0) from StockLevel s where s.location.id = :locationId")
    long sumQuantityAtLocation(@Param("locationId") Long locationId);

    /** [location name, units] for active products. Locations with no stock rows are added by the service. */
    @Query("select l.name, coalesce(sum(s.quantity), 0) from StockLevel s join s.location l join s.product p " +
           "where p.active = true group by l.id, l.name order by l.name")
    List<Object[]> unitsByLocation();
}
