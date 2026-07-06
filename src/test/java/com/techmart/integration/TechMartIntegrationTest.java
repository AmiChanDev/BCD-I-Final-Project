package com.techmart.integration;

import com.techmart.ejb.singleton.InventoryTrackerBean;
import com.techmart.ejb.stateful.ShoppingCartBean;
import com.techmart.ejb.stateless.OrderServiceBean;
import com.techmart.ejb.stateless.ProductCatalogBean;
import com.techmart.jms.mdb.InventoryUpdateMDB;
import com.techmart.jms.mdb.OrderProcessorMDB;
import com.techmart.jms.producer.InventoryEventProducer;
import com.techmart.jms.producer.OrderMessageProducer;
import com.techmart.model.CartItem;
import com.techmart.model.Order;
import com.techmart.model.OrderItem;
import com.techmart.model.PerformanceMetric;
import com.techmart.model.Product;
import com.techmart.util.DataSourceProvider;
import com.techmart.util.PerformanceMonitor;
import jakarta.ejb.EJB;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ArquillianExtension.class)
@DisplayName("TechMart Arquillian Integration Tests")
class TechMartIntegrationTest {

    @Deployment
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class, "techmart-test.war")
                .addClasses(
                        Product.class, Order.class, OrderItem.class, CartItem.class,
                        PerformanceMetric.class, ProductCatalogBean.class,
                        OrderServiceBean.class, ShoppingCartBean.class,
                        InventoryTrackerBean.class, OrderMessageProducer.class,
                        InventoryEventProducer.class, OrderProcessorMDB.class,
                        InventoryUpdateMDB.class, DataSourceProvider.class,
                        PerformanceMonitor.class
                )
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @EJB private ProductCatalogBean productCatalog;
    @EJB private OrderServiceBean orderService;
    @EJB private InventoryTrackerBean inventoryTracker;
    @EJB private PerformanceMonitor perfMonitor;
    @EJB private ShoppingCartBean cart;

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
        results.forEach(p -> assertTrue(
                p.getName().toLowerCase().contains("laptop") ||
                        (p.getDescription() != null &&
                                p.getDescription().toLowerCase().contains("laptop")) ||
                        (p.getSku() != null &&
                                p.getSku().toLowerCase().contains("laptop")),
                "Result '" + p.getName() + "' (sku=" + p.getSku() + ") does not match keyword 'Laptop'"
        ));
    }

    @Test
    @DisplayName("getProductsByCategory() returns only matching category")
    void getProductsByCategoryReturnsCorrectCategory() {
        List<Product> electronics =
                productCatalog.getProductsByCategory("Electronics", 0, 10);
        assertFalse(electronics.isEmpty());
        electronics.forEach(p -> assertEquals("Electronics", p.getCategory(),
                "Product " + p.getSku() + " has wrong category: " + p.getCategory()));
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
        assertNull(productCatalog.getProductById(Long.MAX_VALUE));
    }

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

        if (target == null) return;

        int before = inventoryTracker.getStockLevel(target.getId());
        assertTrue(inventoryTracker.reserveStock(target.getId(), 1));
        assertEquals(before - 1, inventoryTracker.getStockLevel(target.getId()));

        inventoryTracker.restockProduct(target.getId(), 1);
    }

    @Test
    @DisplayName("reserveStock() returns false when requesting more than available")
    void reserveStockReturnsFalseWhenOverRequested() {
        Product target = productCatalog.getAllProducts().get(0);
        int currentStock = inventoryTracker.getStockLevel(target.getId());

        assertFalse(inventoryTracker.reserveStock(target.getId(), currentStock + 1000));
        assertEquals(currentStock, inventoryTracker.getStockLevel(target.getId()));
    }

    @Test
    @DisplayName("Cart starts empty and accumulates items correctly")
    void cartStartsEmptyAndAccumulatesItems() {
        cart.setCustomerId("integration-test-user");
        assertTrue(cart.isEmpty());

        cart.addItem(1L, "LAPTOP-001", "Laptop Pro", 1, new BigDecimal("999.99"));
        cart.addItem(2L, "PHONE-001", "Smartphone", 2, new BigDecimal("499.99"));

        assertFalse(cart.isEmpty());
        assertEquals(3, cart.getItemCount());
    }

    @Test
    @DisplayName("buildOrder() produces a valid Order with correct totals")
    void buildOrderProducesValidOrder() {
        cart.setCustomerId("integration-test-user-2");
        cart.addItem(1L, "LAPTOP-001", "Laptop Pro", 2, new BigDecimal("1000.00"));

        Order order = cart.buildOrder("test@techmart.com", "123 Integration Ave");

        assertNotNull(order);
        assertEquals("integration-test-user-2", order.getCustomerId());
        assertEquals(new BigDecimal("2000.00"), order.getTotalAmount());
        assertEquals(1, order.getItems().size());
    }

    @Test
    @DisplayName("Metrics are recorded after ProductCatalogBean calls")
    void metricsRecordedAfterCatalogCalls() {
        long beforeCount = perfMonitor.getInvocationCount(
                "ProductCatalogBean", "getAllProducts");

        productCatalog.getAllProducts();

        long afterCount = perfMonitor.getInvocationCount(
                "ProductCatalogBean", "getAllProducts");

        assertEquals(beforeCount + 1, afterCount);
    }

    @Test
    @DisplayName("Average latency is non-negative after recording")
    void averageLatencyIsNonNegativeAfterRecording() {
        productCatalog.getAllProducts();
        double avg = perfMonitor.getAverageLatency(
                "ProductCatalogBean", "getAllProducts");
        assertTrue(avg >= 0);
    }

    @Test
    @DisplayName("getSummary() returns non-empty list after operations")
    void getSummaryNonEmptyAfterOperations() {
        productCatalog.getAllProducts();
        assertFalse(perfMonitor.getSummary().isEmpty());
    }

    @Test
    @DisplayName("getAllProducts() benchmark stays below hard upper bound")
    void productCatalogWithinAcceptableLatency() {
        int iterations = 100;
        long start = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            productCatalog.getAllProducts();
        }
        long avgMs = (System.currentTimeMillis() - start) / iterations;
        assertTrue(avgMs < 5000,
                "getAllProducts() avg latency " + avgMs + "ms is unreasonably high");
    }

    @Test
    @DisplayName("searchProducts('Laptop') benchmark stays below hard upper bound")
    void productSearchWithinAcceptableLatency() {
        int iterations = 100;
        long start = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            productCatalog.searchProducts("Laptop");
        }
        long avgMs = (System.currentTimeMillis() - start) / iterations;
        assertTrue(avgMs < 5000,
                "searchProducts() avg latency " + avgMs + "ms is unreasonably high");
    }
}