package com.rims.controller;

import com.rims.dto.PageResponse;
import com.rims.dto.ProductDto;
import com.rims.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ProductDto.Response> list(@RequestParam(required = false) String search,
                                                  @RequestParam(required = false) Long categoryId,
                                                  @RequestParam(defaultValue = "false") boolean lowStock,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return service.list(search, categoryId, lowStock, page, size);
    }

    /** Barcode scanner lookup: matches the product's barcode, or its SKU as a fallback. */
    @GetMapping("/lookup/{code}")
    public ProductDto.Response lookup(@PathVariable String code) {
        return service.lookup(code);
    }

    @GetMapping("/{id}")
    public ProductDto.Response get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDto.Response create(@Valid @RequestBody ProductDto.Request req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public ProductDto.Response update(@PathVariable Long id, @Valid @RequestBody ProductDto.Request req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
