package com.rims.controller;

import com.rims.dto.PageResponse;
import com.rims.dto.SupplierDto;
import com.rims.service.SupplierService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierService service;

    public SupplierController(SupplierService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<SupplierDto.Response> list(@RequestParam(required = false) String search,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        return service.list(search, page, size);
    }

    @GetMapping("/{id}")
    public SupplierDto.Response get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierDto.Response create(@Valid @RequestBody SupplierDto.Request req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public SupplierDto.Response update(@PathVariable Long id, @Valid @RequestBody SupplierDto.Request req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
