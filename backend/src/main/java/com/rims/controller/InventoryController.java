package com.rims.controller;

import com.rims.dto.InventoryDto;
import com.rims.dto.PageResponse;
import com.rims.dto.ProductDto;
import com.rims.entity.TransactionType;
import com.rims.service.InventoryService;
import com.rims.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventory;
    private final ProductService products;

    public InventoryController(InventoryService inventory, ProductService products) {
        this.inventory = inventory;
        this.products = products;
    }

    /** Current stock levels (same data as products, with the same filters). */
    @GetMapping
    public PageResponse<ProductDto.Response> stockLevels(@RequestParam(required = false) String search,
                                                         @RequestParam(required = false) Long categoryId,
                                                         @RequestParam(defaultValue = "false") boolean lowStock,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "10") int size) {
        return products.list(search, categoryId, lowStock, page, size);
    }

    @GetMapping("/low-stock")
    public List<ProductDto.Response> lowStock() {
        return inventory.lowStock();
    }

    @GetMapping("/transactions")
    public PageResponse<InventoryDto.TransactionResponse> transactions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return inventory.transactions(from, to, categoryId, productId, locationId, type, page, size);
    }

    /** Where one product's stock is held, location by location. */
    @GetMapping("/stock/{productId}")
    public List<InventoryDto.StockLevelResponse> stockByLocation(@PathVariable Long productId) {
        return inventory.stockByLocation(productId);
    }

    /** What a location holds: product id and units for every product in stock there. */
    @GetMapping("/locations/{locationId}/stock")
    public List<InventoryDto.ProductStockResponse> stockAtLocation(@PathVariable Long locationId) {
        return inventory.stockAtLocation(locationId);
    }

    /** Move stock between two locations (the product's total is unchanged). */
    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    public List<InventoryDto.TransactionResponse> transfer(@Valid @RequestBody InventoryDto.TransferRequest req) {
        return inventory.transfer(req);
    }

    @PostMapping("/adjust")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryDto.TransactionResponse adjust(@Valid @RequestBody InventoryDto.AdjustmentRequest req) {
        return inventory.adjust(req);
    }
}
