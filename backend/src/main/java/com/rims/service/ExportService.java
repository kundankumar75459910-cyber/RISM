package com.rims.service;

import com.rims.dto.PageResponse;
import com.rims.dto.ProductDto;
import com.rims.dto.PurchaseDto;
import com.rims.dto.SaleDto;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Flat CSV extracts for accounting and ERP systems. One row per document line, fixed column order,
 * ISO dates, plain decimal numbers. This is a file-based integration point, not a connector to a
 * particular product: the receiving system imports the CSV.
 */
@Service
public class ExportService {

    private static final int PAGE = 1000;

    private final SaleService sales;
    private final PurchaseService purchases;
    private final ProductService products;

    public ExportService(SaleService sales, PurchaseService purchases, ProductService products) {
        this.sales = sales;
        this.purchases = purchases;
        this.products = products;
    }

    public String salesCsv(LocalDate from, LocalDate to) {
        List<List<Object>> rows = new ArrayList<>();
        for (SaleDto.Response s : all(page -> sales.list(from, to, page, PAGE))) {
            for (SaleDto.ItemResponse i : s.items()) {
                rows.add(List.of(s.saleNumber(), s.saleDate(), nz(s.customerName()), nz(s.locationName()),
                        i.sku(), i.productName(), i.quantity(), i.unitPrice(), i.lineTotal(), s.totalAmount()));
            }
        }
        return csv(List.of("sale_number", "sale_date", "customer", "location", "sku", "product", "quantity",
                "unit_price", "line_total", "sale_total"), rows);
    }

    public String purchasesCsv(LocalDate from, LocalDate to) {
        List<List<Object>> rows = new ArrayList<>();
        for (PurchaseDto.Response po : all(page -> purchases.list(null, null, from, to, page, PAGE))) {
            for (PurchaseDto.ItemResponse i : po.items()) {
                rows.add(List.of(po.poNumber(), po.orderDate(), nz(po.receivedDate()), po.status(), po.supplierName(),
                        nz(po.locationName()), i.sku(), i.productName(), i.quantity(), i.unitCost(), i.lineTotal(),
                        po.totalAmount()));
            }
        }
        return csv(List.of("po_number", "order_date", "received_date", "status", "supplier", "location", "sku",
                "product", "quantity", "unit_cost", "line_total", "po_total"), rows);
    }

    public String productsCsv() {
        List<List<Object>> rows = new ArrayList<>();
        for (ProductDto.Response p : all(page -> products.list(null, null, false, page, PAGE))) {
            rows.add(List.of(p.sku(), nz(p.barcode()), p.name(), nz(p.categoryName()), nz(p.supplierName()),
                    p.price(), p.quantity(), p.reorderLevel(), p.stockValue()));
        }
        return csv(List.of("sku", "barcode", "name", "category", "supplier", "price", "quantity_on_hand",
                "reorder_level", "stock_value"), rows);
    }

    private static <T> List<T> all(IntFunction<PageResponse<T>> loader) {
        List<T> out = new ArrayList<>();
        int page = 0;
        PageResponse<T> r;
        do {
            r = loader.apply(page++);
            out.addAll(r.content());
        } while (page < r.totalPages());
        return out;
    }

    private static Object nz(Object o) {
        return o == null ? "" : o;
    }

    static String csv(List<String> headers, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append(String.join(",", headers)).append("\r\n");
        for (List<Object> row : rows) {
            for (int c = 0; c < row.size(); c++) {
                if (c > 0) sb.append(',');
                sb.append(cell(row.get(c)));
            }
            sb.append("\r\n");
        }
        return sb.toString();
    }

    /** Quotes when needed and neutralises spreadsheet formulas (a text cell must never start a formula). */
    static String cell(Object value) {
        String s = value == null ? "" : value instanceof BigDecimal d ? d.toPlainString() : String.valueOf(value);
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0 && !isNumber(s)) {
            s = "'" + s;
        }
        boolean quote = s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        return quote ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    private static boolean isNumber(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
