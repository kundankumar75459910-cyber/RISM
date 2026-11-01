package com.rims;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rims.entity.InventoryTransaction;
import com.rims.entity.Product;
import com.rims.repository.InventoryTransactionRepository;
import com.rims.repository.ProductRepository;
import com.rims.repository.SaleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests through the real security filter chain, controllers, services and an
 * in-memory H2 database (MySQL mode) that is seeded by DataSeeder.
 * Each test creates its own products, so the tests are independent of each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class RimsBusinessRulesTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ProductRepository productRepository;
    @Autowired InventoryTransactionRepository transactionRepository;
    @Autowired SaleRepository saleRepository;

    // ------------------------------------------------------------------ helpers

    private String login(String username, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    private String admin() throws Exception { return login("admin", "admin123"); }
    private String staff() throws Exception { return login("staff", "staff123"); }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token);
    }

    private JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    private long firstCategoryId(String token) throws Exception {
        MvcResult r = mvc.perform(auth(get("/api/categories"), token)).andExpect(status().isOk()).andReturn();
        return body(r).get(0).get("id").asLong();
    }

    private long createProduct(String token, String sku, int quantity, String price) throws Exception {
        Map<String, Object> p = new HashMap<>();
        p.put("name", "Test product " + sku);
        p.put("sku", sku);
        p.put("categoryId", firstCategoryId(token));
        p.put("price", new BigDecimal(price));
        p.put("quantity", quantity);
        p.put("reorderLevel", 2);
        MvcResult r = mvc.perform(auth(post("/api/products"), token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(p))).andExpect(status().isCreated()).andReturn();
        return body(r).get("id").asLong();
    }

    private static String uniqueSku() {
        return "T-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private int stockOf(String token, long productId) throws Exception {
        MvcResult r = mvc.perform(auth(get("/api/products/" + productId), token)).andExpect(status().isOk()).andReturn();
        return body(r).get("quantity").asInt();
    }

    private long transactionCount(long productId) {
        return transactionRepository.findAll().stream()
                .filter(t -> t.getProduct().getId().equals(productId)).count();
    }

    private long firstSupplierId(String token) throws Exception {
        MvcResult r = mvc.perform(auth(get("/api/suppliers?size=5"), token)).andExpect(status().isOk()).andReturn();
        return body(r).get("content").get(0).get("id").asLong();
    }

    private long createPurchase(String token, long productId, int qty, String cost) throws Exception {
        Map<String, Object> req = Map.of("supplierId", firstSupplierId(token),
                "items", List.of(Map.of("productId", productId, "quantity", qty, "unitCost", new BigDecimal(cost))));
        MvcResult r = mvc.perform(auth(post("/api/purchases"), token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req))).andExpect(status().isCreated()).andReturn();
        return body(r).get("id").asLong();
    }

    private MvcResult sale(String token, long productId, int qty, int expectedStatus) throws Exception {
        Map<String, Object> req = Map.of("items", List.of(Map.of("productId", productId, "quantity", qty)));
        return mvc.perform(auth(post("/api/sales"), token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req))).andExpect(status().is(expectedStatus)).andReturn();
    }

    // ------------------------------------------------------------ authentication

    @Test
    void protectedEndpointsRejectAnonymousAndBadTokens() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/products").header("Authorization", "Bearer not.a.token")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", "admin", "password", "wrong"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staffIsBlockedFromAdminOnlyEndpointsOnTheServer() throws Exception {
        String staff = staff();
        String admin = admin();
        long id = createProduct(staff, uniqueSku(), 5, "10.00");           // staff may create products

        mvc.perform(auth(delete("/api/products/" + id), staff)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/users"), staff)).andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/suppliers"), staff).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nope Ltd\"}")).andExpect(status().isForbidden());

        long po = createPurchase(staff, id, 3, "4.00");                       // staff may create + receive POs
        mvc.perform(auth(post("/api/purchases/" + po + "/cancel"), staff)).andExpect(status().isForbidden());

        mvc.perform(auth(get("/api/users"), admin)).andExpect(status().isOk());
        mvc.perform(auth(delete("/api/products/" + id), admin)).andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------ products

    @Test
    void skuMustBeUniqueIgnoringCase() throws Exception {
        String admin = admin();
        String sku = uniqueSku();
        createProduct(admin, sku, 1, "5.00");
        Map<String, Object> dup = Map.of("name", "Duplicate", "sku", sku.toLowerCase(), "categoryId", firstCategoryId(admin),
                "price", new BigDecimal("5.00"), "reorderLevel", 1);
        mvc.perform(auth(post("/api/products"), admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dup))).andExpect(status().isConflict());
    }

    @Test
    void validationErrorsReturn400WithFieldMessages() throws Exception {
        String admin = admin();
        mvc.perform(auth(post("/api/products"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"sku\":\"X\",\"price\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").exists());
    }

    @Test
    void openingStockIsLoggedAndEditingAProductCannotChangeStock() throws Exception {
        String admin = admin();
        long id = createProduct(admin, uniqueSku(), 10, "9.99");
        assertEquals(1, transactionCount(id));                                 // INITIAL_STOCK row

        Map<String, Object> edit = new HashMap<>();
        edit.put("name", "Renamed");
        edit.put("sku", uniqueSku());
        edit.put("categoryId", firstCategoryId(admin));
        edit.put("price", new BigDecimal("11.00"));
        edit.put("quantity", 9999);                                            // must be ignored on update
        edit.put("reorderLevel", 3);
        mvc.perform(auth(put("/api/products/" + id), admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(edit))).andExpect(status().isOk());
        assertEquals(10, stockOf(admin, id));
        assertEquals(1, transactionCount(id));
    }

    // --------------------------------------------------------------------- sales

    @Test
    void successfulSaleReducesStockLogsTransactionAndUsesExactDecimalTotal() throws Exception {
        String staff = staff();
        long id = createProduct(staff, uniqueSku(), 10, "12.50");
        long before = transactionCount(id);

        MvcResult r = sale(staff, id, 3, 201);
        assertEquals(0, new BigDecimal("37.50").compareTo(body(r).get("totalAmount").decimalValue()));
        assertEquals(7, stockOf(staff, id));
        assertEquals(before + 1, transactionCount(id));
    }

    @Test
    void saleExceedingStockIsRejectedAndChangesNothing() throws Exception {
        String staff = staff();
        long id = createProduct(staff, uniqueSku(), 2, "5.00");
        long salesBefore = saleRepository.count();
        long txBefore = transactionCount(id);

        sale(staff, id, 3, 409);

        assertEquals(2, stockOf(staff, id));
        assertEquals(salesBefore, saleRepository.count());
        assertEquals(txBefore, transactionCount(id));
    }

    @Test
    void multiLineSaleIsAtomicWhenOneLineFails() throws Exception {
        String staff = staff();
        long ok = createProduct(staff, uniqueSku(), 10, "5.00");
        long scarce = createProduct(staff, uniqueSku(), 1, "5.00");
        long salesBefore = saleRepository.count();

        Map<String, Object> req = Map.of("items", List.of(
                Map.of("productId", ok, "quantity", 4),
                Map.of("productId", scarce, "quantity", 5)));
        mvc.perform(auth(post("/api/sales"), staff).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req))).andExpect(status().isConflict());

        assertEquals(10, stockOf(staff, ok));                                  // the valid line was NOT applied
        assertEquals(1, stockOf(staff, scarce));
        assertEquals(salesBefore, saleRepository.count());
    }

    @Test
    void manualAdjustmentCannotMakeStockNegative() throws Exception {
        String staff = staff();
        long id = createProduct(staff, uniqueSku(), 3, "5.00");
        Map<String, Object> out = Map.of("productId", id, "direction", "OUT", "quantity", 4, "reason", "damaged");
        mvc.perform(auth(post("/api/inventory/adjust"), staff).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(out))).andExpect(status().isConflict());
        assertEquals(3, stockOf(staff, id));

        Map<String, Object> ok = Map.of("productId", id, "direction", "OUT", "quantity", 3, "reason", "damaged");
        mvc.perform(auth(post("/api/inventory/adjust"), staff).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(ok))).andExpect(status().isCreated());
        assertEquals(0, stockOf(staff, id));
    }

    // ------------------------------------------------------------------ purchases

    @Test
    void creatingAPurchaseOrderDoesNotChangeStockAndReceivingIncreasesItExactlyOnce() throws Exception {
        String staff = staff();
        long id = createProduct(staff, uniqueSku(), 5, "8.00");
        long po = createPurchase(staff, id, 20, "3.25");

        assertEquals(5, stockOf(staff, id));                                   // created, not received
        long txBefore = transactionCount(id);

        mvc.perform(auth(post("/api/purchases/" + po + "/receive"), staff)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));
        assertEquals(25, stockOf(staff, id));
        assertEquals(txBefore + 1, transactionCount(id));

        mvc.perform(auth(post("/api/purchases/" + po + "/receive"), staff)).andExpect(status().isConflict());
        assertEquals(25, stockOf(staff, id));                                  // still +20 only once
        assertEquals(txBefore + 1, transactionCount(id));
    }

    @Test
    void cancellingAPendingOrderLeavesStockAloneAndReceivedOrdersCannotBeCancelled() throws Exception {
        String staff = staff();
        String admin = admin();
        long id = createProduct(staff, uniqueSku(), 5, "8.00");

        long pending = createPurchase(staff, id, 10, "3.00");
        mvc.perform(auth(post("/api/purchases/" + pending + "/cancel"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertEquals(5, stockOf(staff, id));
        mvc.perform(auth(post("/api/purchases/" + pending + "/receive"), staff)).andExpect(status().isConflict());
        assertEquals(5, stockOf(staff, id));

        long received = createPurchase(staff, id, 10, "3.00");
        mvc.perform(auth(post("/api/purchases/" + received + "/receive"), staff)).andExpect(status().isOk());
        mvc.perform(auth(post("/api/purchases/" + received + "/cancel"), admin)).andExpect(status().isConflict());
        assertEquals(15, stockOf(staff, id));
    }

    // ---------------------------------------------------------- history integrity

    @Test
    void deletingAProductKeepsSalesAndPurchaseHistoryIntact() throws Exception {
        String admin = admin();
        long id = createProduct(admin, uniqueSku(), 10, "6.00");
        long po = createPurchase(admin, id, 5, "2.00");
        MvcResult s = sale(admin, id, 2, 201);
        long saleId = body(s).get("id").asLong();

        mvc.perform(auth(delete("/api/products/" + id), admin)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/products/" + id), admin)).andExpect(status().isNotFound());

        mvc.perform(auth(get("/api/sales/" + saleId), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productId").value(id));
        mvc.perform(auth(get("/api/purchases/" + po), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productId").value(id));
        sale(admin, id, 1, 400);                                               // a deleted product cannot be sold
    }

    @Test
    void lowStockUsesEachProductsOwnReorderLevel() throws Exception {
        String admin = admin();
        long id = createProduct(admin, uniqueSku(), 2, "5.00");                // reorder level is 2 -> low
        MvcResult r = mvc.perform(auth(get("/api/inventory/low-stock"), admin)).andExpect(status().isOk()).andReturn();
        boolean found = false;
        for (JsonNode n : body(r)) {
            assertTrue(n.get("quantity").asInt() <= n.get("reorderLevel").asInt());
            if (n.get("id").asLong() == id) found = true;
        }
        assertTrue(found);
    }

    @Test
    void dashboardFiguresComeFromTheDatabase() throws Exception {
        String admin = admin();
        MvcResult r = mvc.perform(auth(get("/api/dashboard"), admin)).andExpect(status().isOk()).andReturn();
        JsonNode d = body(r);
        assertEquals(productRepository.countByActiveTrue(), d.get("totalProducts").asLong());
        assertEquals(productRepository.sumActiveQuantity(), d.get("totalQuantity").asLong());
        assertEquals(productRepository.countLowStock(), d.get("lowStockCount").asLong());
        assertEquals(saleRepository.count(), d.get("totalSalesCount").asLong());
        assertEquals(14, d.get("salesTrend").size());
    }

    /**
     * Whole-database invariant: for every product, the sum of its inventory transactions equals
     * its current quantity, and every transaction's running balance is non-negative.
     * This also proves the seeded sample data is internally consistent.
     */
    @Test
    @Transactional
    void everyProductsStockEqualsTheSumOfItsTransactions() {
        Map<Long, Integer> sums = new HashMap<>();
        for (InventoryTransaction t : transactionRepository.findAll()) {
            sums.merge(t.getProduct().getId(), t.getQuantityChange(), Integer::sum);
            assertTrue(t.getQuantityAfter() >= 0);
        }
        for (Product p : productRepository.findAll()) {
            assertEquals(sums.getOrDefault(p.getId(), 0), p.getQuantity(),
                    "Stock mismatch for " + p.getSku());
            assertTrue(p.getQuantity() >= 0);
        }
    }
}
