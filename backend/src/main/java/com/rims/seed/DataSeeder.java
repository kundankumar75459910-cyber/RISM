package com.rims.seed;

import com.rims.dto.*;
import com.rims.entity.Role;
import com.rims.entity.User;
import com.rims.repository.ProductRepository;
import com.rims.repository.UserRepository;
import com.rims.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Loads realistic sample data the first time the app starts on an empty database.
 *
 * It does NOT insert stock numbers directly. It goes through the real services
 * (create product -> receive purchase orders -> record sales), with back-dated timestamps,
 * so quantities, inventory transactions, purchase and sales history are consistent by
 * construction: for every product, opening stock + received - sold = current stock.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final UserService userService;
    private final CategoryService categoryService;
    private final SupplierService supplierService;
    private final ProductService productService;
    private final PurchaseService purchaseService;
    private final SaleService saleService;
    private final LocationService locationService;
    private final InventoryService inventoryService;

    public DataSeeder(UserRepository userRepository, ProductRepository productRepository, UserService userService,
                      CategoryService categoryService, SupplierService supplierService, ProductService productService,
                      PurchaseService purchaseService, SaleService saleService,
                      LocationService locationService, InventoryService inventoryService) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.userService = userService;
        this.categoryService = categoryService;
        this.supplierService = supplierService;
        this.productService = productService;
        this.purchaseService = purchaseService;
        this.saleService = saleService;
        this.locationService = locationService;
        this.inventoryService = inventoryService;
    }

    private record P(String name, String sku, String category, String supplier, String price, int opening, int reorder) {}

    private static final List<P> PRODUCTS = List.of(
            new P("Wireless Mouse", "EL-1001", "Electronics", "TechWorld Distributors", "24.99", 8, 10),
            new P("USB-C Charger 30W", "EL-1002", "Electronics", "TechWorld Distributors", "29.99", 60, 15),
            new P("Bluetooth Speaker", "EL-1003", "Electronics", "TechWorld Distributors", "49.99", 35, 10),
            new P("Wired Earphones", "EL-1004", "Electronics", "TechWorld Distributors", "14.99", 80, 20),
            new P("10000mAh Power Bank", "EL-1005", "Electronics", "TechWorld Distributors", "34.99", 45, 12),
            new P("Men's Cotton T-Shirt", "AP-2001", "Apparel", "UrbanThread Apparel Co.", "12.99", 120, 30),
            new P("Women's Denim Jeans", "AP-2002", "Apparel", "UrbanThread Apparel Co.", "39.99", 70, 20),
            new P("Hooded Sweatshirt", "AP-2003", "Apparel", "UrbanThread Apparel Co.", "44.99", 6, 15),
            new P("Cotton Socks (3-pack)", "AP-2004", "Apparel", "UrbanThread Apparel Co.", "8.99", 150, 40),
            new P("Ceramic Dinner Set (12 pc)", "HM-3001", "Home Goods", "HomeNest Supplies", "59.99", 25, 8),
            new P("Cotton Bath Towel", "HM-3002", "Home Goods", "HomeNest Supplies", "11.99", 90, 25),
            new P("Non-stick Frying Pan", "HM-3003", "Home Goods", "HomeNest Supplies", "22.99", 40, 12),
            new P("LED Desk Lamp", "HM-3004", "Home Goods", "HomeNest Supplies", "19.99", 5, 10),
            new P("Running Shoes", "FW-4001", "Footwear", "StrideWell Footwear Ltd", "64.99", 30, 10),
            new P("Canvas Sneakers", "FW-4002", "Footwear", "StrideWell Footwear Ltd", "34.99", 50, 15),
            new P("Leather Wallet", "AC-5001", "Accessories", "PrimeAccessories Traders", "27.99", 40, 10),
            new P("Polarized Sunglasses", "AC-5002", "Accessories", "PrimeAccessories Traders", "32.99", 28, 8),
            new P("A5 Notebook (Pack of 5)", "ST-6001", "Stationery", "PaperTrail Stationers", "9.99", 100, 30),
            new P("Gel Pen Set (12)", "ST-6002", "Stationery", "PaperTrail Stationers", "6.99", 14, 25));

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Database already contains data - sample data not loaded");
            return;
        }
        log.info("Empty database detected - loading sample data ...");

        // ---- users -------------------------------------------------------
        userService.create(new UserDto.Request("admin", "admin123", "Alex Admin", Role.ADMIN, true));
        userService.create(new UserDto.Request("staff", "staff123", "Sam Staff", Role.STAFF, true));
        User admin = userRepository.findByUsername("admin").orElseThrow();
        User staff = userRepository.findByUsername("staff").orElseThrow();

        // ---- categories ----------------------------------------------------
        Map<String, Long> categoryIds = new HashMap<>();
        for (String[] c : new String[][]{
                {"Electronics", "Chargers, audio and small gadgets"},
                {"Apparel", "Clothing for men and women"},
                {"Home Goods", "Kitchen, dining and home essentials"},
                {"Footwear", "Casual and sports footwear"},
                {"Accessories", "Wallets, eyewear and more"},
                {"Stationery", "Notebooks, pens and office supplies"}}) {
            categoryIds.put(c[0], categoryService.create(new CategoryDto.Request(c[0], c[1])).id());
        }

        // ---- suppliers -----------------------------------------------------
        Map<String, Long> supplierIds = new HashMap<>();
        for (String[] s : new String[][]{
                {"TechWorld Distributors", "+1 555-0101", "orders@techworld.example.com", "12 Silicon Park, Austin, TX"},
                {"UrbanThread Apparel Co.", "+1 555-0102", "sales@urbanthread.example.com", "88 Garment Row, Los Angeles, CA"},
                {"HomeNest Supplies", "+1 555-0103", "hello@homenest.example.com", "5 Warehouse Lane, Columbus, OH"},
                {"StrideWell Footwear Ltd", "+1 555-0104", "trade@stridewell.example.com", "301 Runner Blvd, Portland, OR"},
                {"PrimeAccessories Traders", "+1 555-0105", "info@primeaccessories.example.com", "47 Market Street, Chicago, IL"},
                {"PaperTrail Stationers", "+1 555-0106", "supply@papertrail.example.com", "9 Ink Avenue, Denver, CO"}}) {
            supplierIds.put(s[0], supplierService.create(new SupplierDto.Request(s[0], s[1], s[2], s[3])).id());
        }

        // ---- locations: the first one is the default for stock operations ------
        Map<String, Long> locationIds = new LinkedHashMap<>();
        for (String[] l : new String[][]{
                {"Main Warehouse", "100 Distribution Way, Columbus, OH"},
                {"Downtown Store", "250 Main Street, Columbus, OH"},
                {"Riverside Store", "18 River Road, Columbus, OH"}}) {
            locationIds.put(l[0], locationService.create(new LocationDto.Request(l[0], l[1], true)).id());
        }
        Long mainId = locationIds.get("Main Warehouse");
        Long downtownId = locationIds.get("Downtown Store");
        Long riversideId = locationIds.get("Riverside Store");

        // ---- products with opening stock (35 days ago, at the main warehouse) ----
        LocalDateTime openingTime = LocalDate.now().minusDays(35).atTime(9, 0);
        Map<String, Long> productIds = new HashMap<>();
        int barcodeSeq = 1;
        for (P p : PRODUCTS) {
            // sample EAN-13-style barcodes (no check digit is validated by RIMS)
            String barcode = "4006381" + String.format("%06d", barcodeSeq++);
            ProductDto.Response created = productService.create(new ProductDto.Request(p.name(), p.sku(),
                    categoryIds.get(p.category()), supplierIds.get(p.supplier()), new BigDecimal(p.price()),
                    p.opening(), p.reorder(), barcode, null), admin, openingTime);
            productIds.put(p.sku(), created.id());
        }

        // ---- purchase orders -------------------------------------------------
        // received (back-dated)
        receivedPo(admin, mainId, supplierIds.get("TechWorld Distributors"), 33, 30, "Quarterly electronics restock",
                line(productIds, "EL-1002", 40, "19.00"), line(productIds, "EL-1003", 20, "32.00"),
                line(productIds, "EL-1005", 25, "22.50"));
        receivedPo(admin, mainId, supplierIds.get("UrbanThread Apparel Co."), 30, 26, "Seasonal apparel order",
                line(productIds, "AP-2001", 80, "7.50"), line(productIds, "AP-2002", 40, "24.00"),
                line(productIds, "AP-2004", 100, "4.50"));
        receivedPo(staff, mainId, supplierIds.get("HomeNest Supplies"), 27, 22, null,
                line(productIds, "HM-3002", 50, "6.50"), line(productIds, "HM-3003", 20, "14.00"),
                line(productIds, "HM-3001", 10, "38.00"));
        receivedPo(staff, mainId, supplierIds.get("StrideWell Footwear Ltd"), 24, 18, "Spring footwear intake",
                line(productIds, "FW-4001", 20, "42.00"), line(productIds, "FW-4002", 30, "21.50"));

        // pending (stock NOT increased yet)
        purchaseService.create(new PurchaseDto.Request(supplierIds.get("PaperTrail Stationers"), "Back-to-school stationery",
                List.of(item(productIds, "ST-6002", 100, "3.50"), item(productIds, "ST-6001", 60, "5.50")), mainId),
                staff, LocalDate.now().minusDays(2).atTime(11, 30));
        purchaseService.create(new PurchaseDto.Request(supplierIds.get("HomeNest Supplies"), null,
                List.of(item(productIds, "HM-3004", 30, "12.50")), mainId),
                admin, LocalDate.now().minusDays(1).atTime(15, 10));
        purchaseService.create(new PurchaseDto.Request(supplierIds.get("UrbanThread Apparel Co."), "Winter stock",
                List.of(item(productIds, "AP-2003", 40, "28.00")), mainId),
                admin, LocalDateTime.now().minusMinutes(30));

        // cancelled (stock never changed)
        PurchaseDto.Response cancelled = purchaseService.create(new PurchaseDto.Request(
                supplierIds.get("TechWorld Distributors"), "Cancelled - supplier out of stock",
                List.of(item(productIds, "EL-1001", 50, "14.00")), mainId), admin, LocalDate.now().minusDays(15).atTime(10, 0));
        purchaseService.cancel(cancelled.id());

        // ---- allocate stock to the stores (real, dated transfers out of the main warehouse) ----
        LocalDateTime allocationTime = LocalDate.now().minusDays(17).atTime(9, 0);
        for (Long id : productIds.values()) {
            int total = productRepository.findById(id).orElseThrow().getQuantity();
            if (total < 10) continue;                      // too little to split sensibly
            int toDowntown = (int) (total * 0.30);
            int toRiverside = (int) (total * 0.15);
            inventoryService.transfer(new InventoryDto.TransferRequest(id, mainId, downtownId, toDowntown,
                    "Initial store allocation"), admin, allocationTime);
            inventoryService.transfer(new InventoryDto.TransferRequest(id, mainId, riversideId, toRiverside,
                    "Initial store allocation"), admin, allocationTime);
        }

        // ---- sales over the last 14 days (never more than the stock at the selling location) ----
        // All stock receipts above are dated before this window, so the transaction
        // history reads chronologically: opening stock -> receipts -> sales.
        Random random = new Random(2026);
        String[] customers = {null, null, null, "Priya S.", "Daniel R.", "Maria G.", "Kenji T.", "Olivia B."};
        List<String> skus = PRODUCTS.stream().map(P::sku).toList();
        List<Long> sellingLocations = List.of(mainId, downtownId, riversideId);
        int salesCreated = 0;
        for (int daysAgo = 13; daysAgo >= 0; daysAgo--) {
            int salesToday = 1 + random.nextInt(3);
            for (int n = 0; n < salesToday; n++) {
                int hour = 10 + random.nextInt(10);
                LocalDateTime when = LocalDate.now().minusDays(daysAgo).atTime(hour, random.nextInt(60));
                if (when.isAfter(LocalDateTime.now())) continue;          // nothing in the future
                Long locationId = sellingLocations.get(random.nextInt(sellingLocations.size()));
                Map<Long, Integer> chosen = new LinkedHashMap<>();
                int lines = 1 + random.nextInt(3);
                for (int l = 0; l < lines; l++) {
                    String sku = skus.get(random.nextInt(skus.size()));
                    long id = productIds.get(sku);
                    int available = inventoryService.available(id, locationId) - chosen.getOrDefault(id, 0);
                    int qty = Math.min(1 + random.nextInt(4), available);
                    if (qty > 0) chosen.merge(id, qty, Integer::sum);
                }
                if (chosen.isEmpty()) continue;
                List<SaleDto.ItemRequest> items = new ArrayList<>();
                chosen.forEach((id, q) -> items.add(new SaleDto.ItemRequest(id, q)));
                saleService.create(new SaleDto.Request(customers[random.nextInt(customers.length)], items, locationId),
                        random.nextBoolean() ? admin : staff, when);
                salesCreated++;
            }
        }
        log.info("Sample data loaded: {} products, 6 suppliers, 3 locations, 8 purchase orders, {} sales. "
                + "Logins: admin/admin123 and staff/staff123", PRODUCTS.size(), salesCreated);
    }

    private void receivedPo(User actor, Long locationId, Long supplierId, int orderedDaysAgo, int receivedDaysAgo,
                            String notes, PurchaseDto.ItemRequest... lines) {
        PurchaseDto.Response po = purchaseService.create(
                new PurchaseDto.Request(supplierId, notes, List.of(lines), locationId),
                actor, LocalDate.now().minusDays(orderedDaysAgo).atTime(10, 0));
        purchaseService.receive(po.id(), actor, LocalDate.now().minusDays(receivedDaysAgo).atTime(14, 0));
    }

    private static PurchaseDto.ItemRequest line(Map<String, Long> ids, String sku, int qty, String cost) {
        return item(ids, sku, qty, cost);
    }

    private static PurchaseDto.ItemRequest item(Map<String, Long> ids, String sku, int qty, String cost) {
        return new PurchaseDto.ItemRequest(ids.get(sku), qty, new BigDecimal(cost));
    }
}
