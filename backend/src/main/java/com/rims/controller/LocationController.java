package com.rims.controller;

import com.rims.dto.LocationDto;
import com.rims.service.LocationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Warehouses and stores. Everyone can read; only admins can change them (see SecurityConfig). */
@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final LocationService service;

    public LocationController(LocationService service) {
        this.service = service;
    }

    @GetMapping
    public List<LocationDto.Response> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LocationDto.Response create(@Valid @RequestBody LocationDto.Request req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public LocationDto.Response update(@PathVariable Long id, @Valid @RequestBody LocationDto.Request req) {
        return service.update(id, req);
    }
}
