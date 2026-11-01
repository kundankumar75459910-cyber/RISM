package com.rims.repository;

import com.rims.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
    List<Location> findAllByOrderByNameAsc();

    /** The default location (used when a request names none): the oldest location that is still open. */
    Optional<Location> findFirstByActiveTrueOrderByIdAsc();
}
