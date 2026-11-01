package com.rims.controller;

import com.rims.service.ExportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/** CSV extracts for accounting / ERP systems. Admin only (see SecurityConfig). */
@RestController
@RequestMapping("/api/integration")
public class IntegrationController {

    private final ExportService export;

    public IntegrationController(ExportService export) {
        this.export = export;
    }

    @GetMapping("/sales.csv")
    public ResponseEntity<byte[]> sales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return csv("sales.csv", export.salesCsv(from, to));
    }

    @GetMapping("/purchases.csv")
    public ResponseEntity<byte[]> purchases(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return csv("purchases.csv", export.purchasesCsv(from, to));
    }

    @GetMapping("/products.csv")
    public ResponseEntity<byte[]> products() {
        return csv("products.csv", export.productsCsv());
    }

    private static ResponseEntity<byte[]> csv(String filename, String body) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(body.getBytes(StandardCharsets.UTF_8));
    }
}
