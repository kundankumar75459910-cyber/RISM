package com.rims.service;

import com.rims.dto.LocationDto;
import com.rims.entity.Location;
import com.rims.exception.ApiException;
import com.rims.repository.LocationRepository;
import com.rims.repository.StockLevelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class LocationService {

    private final LocationRepository locations;
    private final StockLevelRepository stockLevels;

    public LocationService(LocationRepository locations, StockLevelRepository stockLevels) {
        this.locations = locations;
        this.stockLevels = stockLevels;
    }

    public List<LocationDto.Response> list() {
        return locations.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public LocationDto.Response create(LocationDto.Request r) {
        String name = r.name().trim();
        if (locations.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("A location named '" + name + "' already exists");
        }
        Location l = new Location();
        l.setName(name);
        l.setAddress(CategoryService.blankToNull(r.address()));
        l.setActive(r.active() == null || r.active());
        return toResponse(locations.save(l));
    }

    @Transactional
    public LocationDto.Response update(Long id, LocationDto.Request r) {
        Location l = find(id);
        String name = r.name().trim();
        if (locations.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw ApiException.conflict("A location named '" + name + "' already exists");
        }
        boolean closing = l.isActive() && r.active() != null && !r.active();
        if (closing) {
            if (stockLevels.sumQuantityAtLocation(id) > 0) {
                throw ApiException.conflict("Location '" + l.getName()
                        + "' still holds stock. Transfer it to another location before closing it");
            }
            long otherOpen = locations.findAllByOrderByNameAsc().stream()
                    .filter(o -> o.isActive() && !o.getId().equals(id)).count();
            if (otherOpen == 0) {
                throw ApiException.conflict("At least one location must stay open");
            }
        }
        l.setName(name);
        l.setAddress(CategoryService.blankToNull(r.address()));
        if (r.active() != null) {
            l.setActive(r.active());
        }
        return toResponse(l);
    }

    /**
     * The location a stock operation applies to. With no id, the default: the oldest open location.
     * A closed or unknown location is rejected.
     */
    public Location resolve(Long locationId) {
        if (locationId == null) {
            return locations.findFirstByActiveTrueOrderByIdAsc()
                    .orElseThrow(() -> ApiException.badRequest("No open location exists. Create a location first"));
        }
        Location l = locations.findById(locationId)
                .orElseThrow(() -> ApiException.badRequest("Selected location does not exist"));
        if (!l.isActive()) {
            throw ApiException.badRequest("Location '" + l.getName() + "' is closed");
        }
        return l;
    }

    /** Any existing location, open or closed (for read-only views). */
    public Location resolveAny(Long locationId) {
        return find(locationId);
    }

    private Location find(Long id) {
        return locations.findById(id).orElseThrow(() -> ApiException.notFound("Location not found"));
    }

    private LocationDto.Response toResponse(Location l) {
        return new LocationDto.Response(l.getId(), l.getName(), l.getAddress(), l.isActive(),
                stockLevels.sumQuantityAtLocation(l.getId()));
    }
}
