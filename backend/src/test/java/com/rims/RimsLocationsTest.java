package com.rims;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Multi-location stock, transfers, barcode lookup and the admin-only CSV export, end to end
 * (security filter chain, controllers, services, H2 in MySQL mode). Every test creates its own
 * products and locations, so tests do not depend on each other or on the sample data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class RimsLocationsTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

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

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private MvcResult send(MockHttpServletRequestBuilder b, String token, Object payload, int expected) throws Exception {
        MockHttpServletRequestBuilder req = auth(b, token);
        if (payload != null) req = req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload));
        return mvc.perform(req).andExpect(status().is(expected)).andReturn();
    }

    private long createLocation(String token) throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("name", unique("Store "));
        req.put("address", "1 Test Street");
        return body(send(post("/api/locations"), token, req, 201)).get("id").asLong();
    }

    /** Opening stock lands at the default (oldest open) location. */
    private long createProduct(String token, int quantity, String barcode) throws Exception {
        long categoryId = body(send(get("/api/categories"), token, null, 200)).get(0).get("id").asLong();
        Map<String, Object> p = new HashMap<>();
        p.put("name", "Loc test " + unique(""));
        p.put("sku", unique("L-"));
        p.put("categoryId", categoryId);
        p.put("price", new BigDecimal("4.50"));
        p.put("quantity", quantity);
        p.put("reorderLevel", 1);
        if (barcode != null) p.put("barcode", barcode);
        return body(send(post("/api/products"), token, p, 201)).get("id").asLong();
    }

    private int total(String token, long productId) throws Exception {
        return body(send(get("/api/products/" + productId), token, null, 200)).get("quantity").asInt();
    }

    private int at(String token, long productId, long locationId) throws Exception {
        for (JsonNode n : body(send(get("/api/inventory/stock/" + productId), token, null, 200))) {
            if (n.get("locationId").asLong() == locationId) return n.get("quantity").asInt();
        }
        return 0;
    }

    private long defaultLocationOf(String token, long productId) throws Exception {
        for (JsonNode n : body(send(get("/api/inventory/stock/" + productId), token, null, 200))) {
            if (n.get("quantity").asInt() > 0) return n.get("locationId").asLong();
        }
        throw new AssertionError("product has no stock anywhere");
    }

    private Map<String, Object> transfer(long productId, long from, long to, int qty) {
        Map<String, Object> m = new HashMap<>();
        m.put("productId", productId);
        m.put("fromLocationId", from);
        m.put("toLocationId", to);
        m.put("quantity", qty);
        m.put("note", "test transfer");
        return m;
    }

    private Map<String, Object> saleAt(long locationId, long productId, int qty) {
        return Map.of("locationId", locationId, "items", List.of(Map.of("productId", productId, "quantity", qty)));
    }

    // ----------------------------------------------------------------- locations

    @Test
    void locationNamesAreUniqueAndOnlyAdminsCanChangeLocations() throws Exception {
        String admin = admin();
        Map<String, Object> req = Map.of("name", unique("Depot "));
        send(post("/api/locations"), admin, req, 201);
        send(post("/api/locations"), admin, req, 409);
        send(post("/api/locations"), staff(), Map.of("name", unique("Depot ")), 403);
        send(get("/api/locations"), staff(), null, 200);     // everyone may read
    }

    @Test
    void aLocationHoldingStockCannotBeClosed() throws Exception {
        String admin = admin();
        long product = createProduct(admin, 5, null);
        long main = defaultLocationOf(admin, product);
        long other = createLocation(admin);
        send(post("/api/inventory/transfer"), admin, transfer(product, main, other, 2), 200);

        Map<String, Object> close = Map.of("name", "Closing test", "active", false);
        send(put("/api/locations/" + other), admin, close, 409);          // still holds 2 units
        send(post("/api/inventory/transfer"), admin, transfer(product, other, main, 2), 200);
        send(put("/api/locations/" + other), admin, Map.of("name", unique("Closed "), "active", false), 200);
    }

    // ----------------------------------------------------------------- transfers

    @Test
    void transferMovesStockBetweenLocationsAndLeavesTheTotalUnchanged() throws Exception {
        String staff = staff();
        long product = createProduct(staff, 10, null);
        long main = defaultLocationOf(staff, product);
        long other = createLocation(admin());

        send(post("/api/inventory/transfer"), staff, transfer(product, main, other, 4), 200);

        assertEquals(6, at(staff, product, main));
        assertEquals(4, at(staff, product, other));
        assertEquals(10, total(staff, product));

        // one OUT and one IN row, both visible in the history for that product
        JsonNode rows = body(send(get("/api/inventory/transactions?productId=" + product + "&size=50"), staff, null, 200))
                .get("content");
        int outs = 0, ins = 0;
        for (JsonNode t : rows) {
            if ("TRANSFER_OUT".equals(t.get("type").asText())) { outs++; assertEquals(-4, t.get("quantityChange").asInt()); }
            if ("TRANSFER_IN".equals(t.get("type").asText())) { ins++; assertEquals(4, t.get("quantityChange").asInt()); }
        }
        assertEquals(1, outs);
        assertEquals(1, ins);
    }

    @Test
    void transferMoreThanTheSourceHoldsIsRejectedAndChangesNothing() throws Exception {
        String staff = staff();
        long product = createProduct(staff, 3, null);
        long main = defaultLocationOf(staff, product);
        long other = createLocation(admin());

        send(post("/api/inventory/transfer"), staff, transfer(product, main, other, 4), 409);

        assertEquals(3, at(staff, product, main));
        assertEquals(0, at(staff, product, other));
        assertEquals(3, total(staff, product));
    }

    @Test
    void transferToTheSameLocationIsRejected() throws Exception {
        String staff = staff();
        long product = createProduct(staff, 3, null);
        long main = defaultLocationOf(staff, product);
        send(post("/api/inventory/transfer"), staff, transfer(product, main, main, 1), 400);
        assertEquals(3, at(staff, product, main));
    }

    // ------------------------------------------------- sales and purchases by location

    @Test
    void aSaleCanOnlyTakeWhatTheChosenLocationHolds() throws Exception {
        String staff = staff();
        long product = createProduct(staff, 10, null);
        long main = defaultLocationOf(staff, product);
        long other = createLocation(admin());
        send(post("/api/inventory/transfer"), staff, transfer(product, main, other, 4), 200);

        // 10 exist company-wide, but only 4 are at "other"
        send(post("/api/sales"), staff, saleAt(other, product, 5), 409);
        assertEquals(10, total(staff, product));

        MvcResult ok = send(post("/api/sales"), staff, saleAt(other, product, 4), 201);
        assertEquals(other, body(ok).get("locationId").asLong());
        assertEquals(0, at(staff, product, other));
        assertEquals(6, at(staff, product, main));
        assertEquals(6, total(staff, product));
    }

    @Test
    void receivingAPurchaseAddsStockToItsDeliveryLocation() throws Exception {
        String staff = staff();
        long product = createProduct(staff, 2, null);
        long main = defaultLocationOf(staff, product);
        long other = createLocation(admin());
        long supplier = body(send(get("/api/suppliers?size=1"), staff, null, 200)).get("content").get(0).get("id").asLong();

        Map<String, Object> po = Map.of("supplierId", supplier, "locationId", other,
                "items", List.of(Map.of("productId", product, "quantity", 7, "unitCost", new BigDecimal("1.25"))));
        long poId = body(send(post("/api/purchases"), staff, po, 201)).get("id").asLong();
        assertEquals(0, at(staff, product, other));                       // ordering changes nothing

        send(post("/api/purchases/" + poId + "/receive"), staff, null, 200);

        assertEquals(7, at(staff, product, other));
        assertEquals(2, at(staff, product, main));
        assertEquals(9, total(staff, product));
    }

    // -------------------------------------------------------------------- barcodes

    @Test
    void barcodesAreUniqueIgnoringCaseAndLookupFindsByBarcodeThenSku() throws Exception {
        String staff = staff();
        String code = unique("BC");
        long product = createProduct(staff, 1, code);

        // a second product may not reuse it, in any letter case
        long categoryId = body(send(get("/api/categories"), staff, null, 200)).get(0).get("id").asLong();
        Map<String, Object> dup = new HashMap<>();
        dup.put("name", "Dup"); dup.put("sku", unique("D-")); dup.put("categoryId", categoryId);
        dup.put("price", new BigDecimal("1.00")); dup.put("quantity", 0); dup.put("reorderLevel", 0);
        dup.put("barcode", code.toLowerCase());
        send(post("/api/products"), staff, dup, 409);

        JsonNode byBarcode = body(send(get("/api/products/lookup/" + code), staff, null, 200));
        assertEquals(product, byBarcode.get("id").asLong());
        JsonNode bySku = body(send(get("/api/products/lookup/" + byBarcode.get("sku").asText()), staff, null, 200));
        assertEquals(product, bySku.get("id").asLong());
        send(get("/api/products/lookup/" + unique("NOPE")), staff, null, 404);
    }

    @Test
    void productImageMustBeAnHttpLink() throws Exception {
        String staff = staff();
        long categoryId = body(send(get("/api/categories"), staff, null, 200)).get(0).get("id").asLong();
        Map<String, Object> p = new HashMap<>();
        p.put("name", "Img"); p.put("sku", unique("I-")); p.put("categoryId", categoryId);
        p.put("price", new BigDecimal("1.00")); p.put("quantity", 0); p.put("reorderLevel", 0);
        p.put("imageUrl", "javascript:alert(1)");
        send(post("/api/products"), staff, p, 400);
        p.put("imageUrl", "https://example.com/a.png");
        send(post("/api/products"), staff, p, 201);
    }

    // ---------------------------------------------------------------- CSV export

    @Test
    void accountingExportIsAdminOnlyAndReturnsCsv() throws Exception {
        for (String path : List.of("/api/integration/sales.csv", "/api/integration/purchases.csv", "/api/integration/products.csv")) {
            send(get(path), staff(), null, 403);
            send(get(path), "not-a-token", null, 401);
        }
        MvcResult r = send(get("/api/integration/products.csv"), admin(), null, 200);
        String csv = r.getResponse().getContentAsString();
        assertTrue(csv.startsWith("sku,"), "header row first, got: " + csv.split("\n")[0]);
        assertTrue(csv.lines().count() > 1, "sample data should produce rows");
    }
}
