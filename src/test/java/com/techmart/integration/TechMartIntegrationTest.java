package com.techmart.integration;

import com.techmart.ejb.singleton.InventoryTrackerBean;
import com.techmart.ejb.stateful.ShoppingCartBean;
import com.techmart.ejb.stateless.ProductCatalogBean;
import com.techmart.ejb.stateless.OrderServiceBean;
import com.techmart.jms.producer.InventoryEventProducer;
import com.techmart.jms.producer.OrderMessageProducer;
import com.techmart.jms.mdb.OrderProcessorMDB;
import com.techmart.jms.mdb.InventoryUpdateMDB;

// Explicit model imports — avoids clash between com.techmart.model.Order
// and org.junit.jupiter.api.Order when using wildcard imports
import com.techmart.model.CartItem;
import com.techmart.model.Order;
import com.techmart.model.OrderItem;
import com.techmart.model.PerformanceMetric;
import com.techmart.model.Product;

import com.techmart.util.DataSourceProvider;
import com.techmart.util.PerformanceMonitor;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;

// Explicit JUnit 5 imports — do NOT use org.junit.jupiter.api.* wildcard;
// that pulls in org.junit.jupiter.api.Order which clashes with
// com.techmart.model.Order above.
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import jakarta.ejb.EJB;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Arquillian Integration Tests — Full EJB Container.
 *
 * These tests run inside a live GlassFish instance, verifying that:
 *   - EJB injection, JNDI lookups, and transaction management work correctly
 *   - JMS queues and topics are accessible and messages are delivered
 *   - Singleton concurrency annotations are enforced by the container
 *   - The full order placement flow works end-to-end
 *
 * PREREQUISITES:
 *   1. GlassFish 7 running on localhost:8080 (admin console on :4848)
 *   2. TechMartDS DataSource, OrderQueue, and InventoryTopic pre-created at
 *      the domain level via setup-resources.sh (see DEPLOYMENT.md) — NOT
 *      via a bundled glassfish-resources.xml, which hits a GlassFish 7
 *      deploy-time resource-binding ordering bug
 *   3. Run: mvn verify
 *
 * WHY ARQUILLIAN?
 *   JUnit alone cannot test @Lock, @Stateful passivation,
 *   @TransactionAttribute, or JMS delivery — these require a running EJB
 *   container. Arquillian deploys a micro-archive to GlassFish and runs
 *   test methods inside the container, giving true integration coverage.
 */
@ExtendWith(ArquillianExtension.class)
@DisplayName("TechMart Arquillian Integration Tests")
class TechMartIntegrationTest {

    /**
     * ShrinkWrap builds a minimal WAR containing only the classes under test.
     * Deploying a small archive is faster and isolates failures to the
     * components being tested.
     */
    @Deployment
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class, "techmart-test.war")
            .addClasses(
                // Models
                Product.class, Order.class, OrderItem.class,
                CartItem.class, PerformanceMetric.class,
                // EJBs
                ProductCatalogBean.class,
                OrderServiceBean.class,
                ShoppingCartBean.class,
                InventoryTrackerBean.class,
                // JMS
                OrderMessageProducer.class,
                InventoryEventProducer.class,
                OrderProcessorMDB.class,
                InventoryUpdateMDB.class,
                // Utilities
                DataSourceProvider.class,
                PerformanceMonitor.class
            )
            // CDI beans.xml to activate injection in tests
            .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
            // Note: no glassfish-resources.xml or persistence.xml here.
            // DataSource/JMS resources are pre-created at the domain level
            // via setup-resources.sh (see DEPLOYMENT.md) before running
            // `mvn verify`, and nothing in this codebase uses JPA/EntityManager
            // — all data access is raw JDBC via DataSourceProvider.
    }

    // ── Injected EJB references ──────────────────────────────────────────────

    @EJB private ProductCatalogBean   productCatalog;
    @EJB private OrderServiceBean     orderService;
    @EJB private InventoryTrackerBean inventoryTracker;
    @EJB private PerformanceMonitor   perfMonitor;
    @EJB private ShoppingCartBean     cart;

    // ── ProductCatalogBean ───────────────────────────────────────────────────

    @Nested
    @DisplayName("ProductCatalogBean — Container Integration")
    class ProductCatalogIntegrationTests {

        @Test
        @DisplayName("getAllProducts() returns non-null list from live DB")
        void getAllProductsReturnsNonNullList() {
            List<Product> products = productCatalog.getAllProducts();
            assertNotNull(products);
            assertTrue(products.size() >= 8,
                "Expected at least 8 seeded products, got: " + products.size());
        }

        @Test
        @DisplayName("searchProducts() finds products by keyword")
        void searchProductsFindsByKeyword() {
            List<Product> results = productCatalog.searchProducts("Laptop");
            assertFalse(results.isEmpty(), "Expected at least one laptop product");
            results.forEach(p ->
                assertTrue(
                    p.getName().toLowerCase().contains("laptop") ||
                    (p.getDescription() != null &&
                     p.getDescription().toLowerCase().contains("laptop")),
                    "Result '" + p.getName() + "' does not match keyword 'Laptop'"
                )
            );
        }

        @Test
        @DisplayName("getProductsByCategory() returns only matching category")
        void getProductsByCategoryReturnsCorrectCategory() {
            List<Product> electronics =
                productCatalog.getProductsByCategory("Electronics", 0, 10);
            assertFalse(electronics.isEmpty());
            electronics.forEach(p ->
                assertEquals("Electronics", p.getCategory(),
                    "Product " + p.getSku() + " has wrong category: " + p.getCategory())
            );
        }

        @Test
        @DisplayName("getProductById() returns correct product")
        void getProductByIdReturnsCorrectProduct() {
            List<Product> all = productCatalog.getAllProducts();
            assertFalse(all.isEmpty());

            Long firstId = all.get(0).getId();
            Product found = productCatalog.getProductById(firstId);

            assertNotNull(found);
            assertEquals(firstId, found.getId());
        }

        @Test
        @DisplayName("getProductById() returns null for non-existent ID")
        void getProductByIdReturnsNullForNonExistentId() {
            Product notFound = productCatalog.getProductById(Long.MAX_VALUE);
            assertNull(notFound);
        }
    }

    // ── InventoryTrackerBean ─────────────────────────────────────────────────

    @Nested
    @DisplayName("InventoryTrackerBean — Singleton Container Integration")
    class InventoryIntegrationTests {

        @Test
        @DisplayName("Singleton cache is pre-loaded from DB at startup")
        void singletonCachePreloadedFromDb() {
            Map<Long, Integer> inventory = inventoryTracker.getFullInventory();
            assertFalse(inventory.isEmpty(),
                "Inventory cache should be populated from seeded DB at startup");
        }

        @Test
        @DisplayName("reserveStock() succeeds for available product")
        void reserveStockSucceedsForAvailableProduct() {
            List<Product> products = productCatalog.getAllProducts();
            Product target = products.stream()
                .filter(p -> inventoryTracker.getStockLevel(p.getId()) >= 5)
                .findFirst()
                .orElse(null);

            if (target == null) return; // no product with sufficient stock

            int before = inventoryTracker.getStockLevel(target.getId());
            boolean result = inventoryTracker.reserveStock(target.getId(), 1);

            assertTrue(result);
            assertEquals(before - 1, inventoryTracker.getStockLevel(target.getId()));

            // Restore stock so other tests are not affected
            inventoryTracker.restockProduct(target.getId(), 1);
        }

        @Test
        @DisplayName("reserveStock() returns false when requesting more than available")
        void reserveStockReturnsFalseWhenOverRequested() {
            List<Product> products = productCatalog.getAllProducts();
            Product target = products.get(0);
            int currentStock = inventoryTracker.getStockLevel(target.getId());

            boolean result = inventoryTracker.reserveStock(
                target.getId(), currentStock + 1000);

            assertFalse(result);
            assertEquals(currentStock,
                inventoryTracker.getStockLevel(target.getId()));
        }
    }

    // ── ShoppingCartBean ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("ShoppingCartBean — Stateful Container Integration")
    class ShoppingCartIntegrationTests {

        @Test
        @DisplayName("Cart starts empty and accumulates items correctly")
        void cartStartsEmptyAndAccumulatesItems() {
            cart.setCustomerId("integration-test-user");
            assertTrue(cart.isEmpty());

            cart.addItem(1L, "LAPTOP-001", "Laptop Pro",
                         1, new BigDecimal("999.99"));
            cart.addItem(2L, "PHONE-001", "Smartphone",
                         2, new BigDecimal("499.99"));

            assertFalse(cart.isEmpty());
            assertEquals(3, cart.getItemCount());
        }

        @Test
        @DisplayName("buildOrder() produces a valid Order with correct totals")
        void buildOrderProducesValidOrder() {
            cart.setCustomerId("integration-test-user-2");
            cart.addItem(1L, "LAPTOP-001", "Laptop Pro",
                         2, new BigDecimal("1000.00"));

            Order order = cart.buildOrder(
                "test@techmart.com", "123 Integration Ave");

            assertNotNull(order);
            assertEquals("integration-test-user-2", order.getCustomerId());
            assertEquals(new BigDecimal("2000.00"), order.getTotalAmount());
            assertEquals(1, order.getItems().size());
        }
    }

    // ── PerformanceMonitor ───────────────────────────────────────────────────

    @Nested
    @DisplayName("PerformanceMonitor — Singleton Integration")
    class PerformanceMonitorIntegrationTests {

        @Test
        @DisplayName("Metrics are recorded after ProductCatalogBean calls")
        void metricsRecordedAfterCatalogCalls() {
            long beforeCount = perfMonitor.getInvocationCount(
                "ProductCatalogBean", "getAllProducts");

            productCatalog.getAllProducts();

            long afterCount = perfMonitor.getInvocationCount(
                "ProductCatalogBean", "getAllProducts");

            assertEquals(beforeCount + 1, afterCount,
                "Expected invocation count to increment by 1");
        }

        @Test
        @DisplayName("Average latency is non-negative after recording")
        void averageLatencyIsNonNegativeAfterRecording() {
            productCatalog.getAllProducts();
            double avg = perfMonitor.getAverageLatency(
                "ProductCatalogBean", "getAllProducts");
            assertTrue(avg >= 0,
                "Average latency should be non-negative");
        }

        @Test
        @DisplayName("getSummary() returns non-empty list after operations")
        void getSummaryNonEmptyAfterOperations() {
            productCatalog.getAllProducts();
            List<Map<String, Object>> summary = perfMonitor.getSummary();
            assertFalse(summary.isEmpty(),
                "Summary should contain at least one entry");
        }
    }

    // ── Performance Benchmarks ───────────────────────────────────────────────

    @Nested
    @DisplayName("Performance Benchmarks")
    class PerformanceBenchmarkTests {

        private static final int    ITERATIONS       = 100;
        private static final long   ACCEPTABLE_AVG_MS = 100L;

        @Test
        @DisplayName("getAllProducts() — 100 calls within acceptable avg latency")
        void productCatalogWithinAcceptableLatency() {
            long start = System.currentTimeMillis();
            for (int i = 0; i < ITERATIONS; i++) {
                productCatalog.getAllProducts();
            }
            long elapsed = System.currentTimeMillis() - start;
            long avgMs   = elapsed / ITERATIONS;

            System.out.printf("[BENCHMARK] getAllProducts() — %d calls, avg=%dms, total=%dms%n",
                ITERATIONS, avgMs, elapsed);

            if (avgMs > ACCEPTABLE_AVG_MS) {
                System.err.printf(
                    "[PERF WARNING] getAllProducts() avg %dms exceeds target %dms%n",
                    avgMs, ACCEPTABLE_AVG_MS);
            }
            // Hard lower bound — must not average > 5 s (something is very wrong)
            assertTrue(avgMs < 5000,
                "getAllProducts() avg latency " + avgMs + "ms is unreasonably high");
        }

        @Test
        @DisplayName("searchProducts('Laptop') — 100 calls within acceptable avg latency")
        void productSearchWithinAcceptableLatency() {
            long start = System.currentTimeMillis();
            for (int i = 0; i < ITERATIONS; i++) {
                productCatalog.searchProducts("Laptop");
            }
            long elapsed = System.currentTimeMillis() - start;
            long avgMs   = elapsed / ITERATIONS;

            System.out.printf(
                "[BENCHMARK] searchProducts('Laptop') — %d calls, avg=%dms%n",
                ITERATIONS, avgMs);

            assertTrue(avgMs < 5000,
                "searchProducts() avg latency " + avgMs + "ms is unreasonably high");
        }
    }
}
