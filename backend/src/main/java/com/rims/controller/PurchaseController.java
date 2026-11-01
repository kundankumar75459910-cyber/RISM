package com.rims.controller;

import com.rims.dto.PageResponse;
import com.rims.dto.PurchaseDto;
import com.rims.entity.PurchaseStatus;
import com.rims.service.PurchaseService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/purchases")
public class PurchaseController {

    private final PurchaseService service;

    public PurchaseController(PurchaseService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<PurchaseDto.Response> list(
            @RequestParam(required = false) PurchaseStatus status,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.list(status, supplierId, from, to, page, size);
    }

    @GetMapping("/{id}")
    public PurchaseDto.Response get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseDto.Response create(@Valid @RequestBody PurchaseDto.Request req) {
        return service.create(req);
    }

    @PostMapping("/{id}/receive")
    public PurchaseDto.Response receive(@PathVariable Long id) {
        return service.receive(id);
    }

    @PostMapping("/{id}/cancel")
    public PurchaseDto.Response cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
