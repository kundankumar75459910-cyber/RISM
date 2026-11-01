package com.rims.repository;

import com.rims.entity.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface SaleRepository extends JpaRepository<Sale, Long>, JpaSpecificationExecutor<Sale> {

    @Query("select coalesce(sum(s.totalAmount), 0) from Sale s")
    BigDecimal sumTotalAmount();

    List<Sale> findBySaleDateGreaterThanEqual(LocalDateTime from);
}
